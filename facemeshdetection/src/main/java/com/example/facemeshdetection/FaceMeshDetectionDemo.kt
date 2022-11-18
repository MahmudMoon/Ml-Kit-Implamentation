package com.example.facemeshdetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facemeshdetection.databinding.ActivityFaceMeshDetectionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.Triangle
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import java.io.ByteArrayOutputStream
import java.nio.ReadOnlyBufferException
import java.util.concurrent.ExecutorService
import kotlin.experimental.inv

class FaceMeshDetectionDemo : AppCompatActivity() {
    private lateinit var cameraId: String
    private lateinit var previewSize: Size
    private var cameraDevice: CameraDevice? = null
    private var isProcessing = false
    private var mThreadHandler: HandlerThread? = null
    private var mHandler: Handler? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCaptureSessions: CameraCaptureSession? = null
    lateinit var faceMeshDetectionBinding: ActivityFaceMeshDetectionBinding
    //lateinit var defaultDetector: FaceMeshDetector

    private lateinit var detector: FaceMeshDetector
    private val TAG = "FaceMeshDetectionDemo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceMeshDetectionBinding = ActivityFaceMeshDetectionBinding.inflate(layoutInflater)
        setContentView(faceMeshDetectionBinding.root)
        faceMeshDetectionBinding.textureNid.surfaceTextureListener = textureListener
        val optionsBuilder = FaceMeshDetectorOptions.Builder()
        optionsBuilder.setUseCase(FaceMeshDetectorOptions.FACE_MESH)
        detector = FaceMeshDetection.getClient(optionsBuilder.build())

        if (allPermissionsGranted()) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    var camerastateListener: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "onOpened: ")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "onDisconnected: ")
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "onError: ")
            cameraDevice?.close()
        }

    }

    override fun onResume() {
        super.onResume()
        startBackground()
        if (faceMeshDetectionBinding.textureNid.isAvailable) {
            openCamera()
        } else {
            faceMeshDetectionBinding.textureNid.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        super.onPause()
        stopBackgroundThread()
        cameraDevice?.close()
    }

    private fun stopBackgroundThread() {
        mThreadHandler?.quitSafely()
        try {
            mThreadHandler?.join()
            mThreadHandler = null
            mHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    private fun startBackground() {
        isProcessing = false
        mThreadHandler = HandlerThread("Camera Background")
        mThreadHandler?.start()
        mHandler = Handler(mThreadHandler!!.looper)
    }

    private val previewReader by lazy {
        ImageReader.newInstance(
            1280,
            720,
            ImageFormat.YUV_420_888,
            3
        )
    }

    // listeners
    var textureListener: TextureView.SurfaceTextureListener = object :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable: ")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: ")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "onSurfaceTextureDestroyed: ")
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            Log.d(TAG, "onSurfaceTextureUpdated: ")
        }

    }

    private val readerListener =
        ImageReader.OnImageAvailableListener { reader ->
            val image = reader.acquireLatestImage()

            if (isProcessing && image != null) {
                image.close()
            } else if (image != null) {
                isProcessing = true
                if (mHandler == null) {
                    image.close()
                    return@OnImageAvailableListener
                }

                try {
                    mHandler?.post(object : Runnable {
                        override fun run() {
                            val nv21 = YUV_420_888toNV21(image)
                            image.close()
                            val bmp = createBitMap(NV21toJPEG(nv21, 1280, 720))
                            //Ocr processing
                            val inputImage = InputImage.fromBitmap(bmp, 0)
                            detector.process(inputImage).addOnSuccessListener { faceMeshs ->
                                Log.d(TAG, "analyze: ${faceMeshs.size}")
                                for (faceMesh in faceMeshs) {
                                    val bounds: Rect = faceMesh.boundingBox
                                    Log.d(
                                        TAG, "analyze: " +
                                                "\nbtm: ${bounds.bottom}" +
                                                "\nleft  ${bounds.left}" +
                                                "\nright  ${bounds.right}" +
                                                "\ntop  ${bounds.top}"
                                    )
                                    // Gets all points
                                    val faceMeshpoints = faceMesh.allPoints
                                    Log.d(TAG, "analyze: points -> ${faceMeshpoints.size}")
                                    for (faceMeshpoint in faceMeshpoints) {
                                        val index: Int = faceMeshpoints.indexOf(faceMeshpoint)
                                        Log.d(TAG, "analyze: index -> ${index}")
                                        val position = faceMeshpoint.position
                                        Log.d(
                                            TAG,
                                            "analyze: faceMeshPoint -> ${position.x} , ${position.y} , ${position.z}"
                                        )
                                    }

                                    // Gets triangle info
                                    val triangles: List<Triangle<FaceMeshPoint>> =
                                        faceMesh.allTriangles
                                    for (triangle in triangles) {
                                        // 3 Points connecting to each other and representing a triangle area.
                                        val connectedPoints = triangle.allPoints
                                        Log.d(TAG, "analyze: connection -> ${connectedPoints.size}")
                                    }

                                }
                            }.addOnFailureListener {
                                Log.d(TAG, "analyze: ${it.localizedMessage}")
                            }
                            isProcessing = false
                        }
                    })
                } catch (exception: Exception) {
                    Log.e(
                        "TextRec",
                        "issue is : ${exception.localizedMessage}, ${exception.message}"
                    )
                }
            }
        }

    protected fun createCameraPreview() {
        try {
            val texture = faceMeshDetectionBinding.textureNid.surfaceTexture
            if (texture != null) {
                texture?.setDefaultBufferSize(1280, 720)
                previewReader.setOnImageAvailableListener(readerListener, null)
                val readerSurface = previewReader.surface
                val surface = Surface(texture)
                captureRequestBuilder =
                    cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder?.addTarget(surface)
                captureRequestBuilder?.addTarget(readerSurface)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cameraDevice?.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(
                                OutputConfiguration(0, surface),
                                OutputConfiguration(1, readerSurface)
                            ),
                            mainExecutor,
                            captureStateCallback
                        )
                    )
                } else {
                    cameraDevice?.createCaptureSession(
                        listOf(surface, readerSurface),
                        captureStateCallback,
                        null
                    )
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            //The camera is already closed
            if (cameraDevice == null) {
                return
            }
            // When the session is ready, we start displaying the preview.
            cameraCaptureSessions = cameraCaptureSession
            updatePreview()
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            Toast.makeText(
                this@FaceMeshDetectionDemo,
                "Configuration change",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updatePreview() {
        captureRequestBuilder?.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        try {
            cameraCaptureSessions?.setRepeatingRequest(
                captureRequestBuilder!!.build(),
                null,
                mHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager.cameraIdList[1]
            val cameraCharacters = cameraManager.getCameraCharacteristics(cameraId)
            sensorOrientation =
                cameraCharacters.get(CameraCharacteristics.SENSOR_ORIENTATION)
            val map = cameraCharacters.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            previewSize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            cameraManager.openCamera(cameraId, camerastateListener, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun allPermissionsGranted(): Boolean{
        if(ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
       // cameraExecutor.shutdown()
    }

    var sensorOrientation: Int? = 0

    // 参照：https://stackoverflow.com/questions/52726002/camera2-captured-picture-conversion-from-yuv-420-888-to-nv21/52740776#52740776
    // 参照：https://stackoverflow.com/questions/52726002/camera2-captured-picture-conversion-from-yuv-420-888-to-nv21/52740776#52740776
    fun YUV_420_888toNV21(image: Image): ByteArray {
        val width: Int = image.width
        val height: Int = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride === 1)
        var pos = 0
        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel: Byte = vBuffer.get(1)
            try {
                vBuffer.put(1, savePixel.inv())
                if (uBuffer[0] == savePixel.inv()) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer.get(nv21, ySize, 1)
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining())
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer.get(vuPos)
                nv21[pos++] = uBuffer.get(vuPos)
            }
        }
        return nv21
    }

    fun createBitMap(jpeg: ByteArray): Bitmap {
        var bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        if (sensorOrientation == 270) {
            bitmap = rotate(bitmap, 180)
        }
        return bitmap
    }

    private fun rotate(beforeBmp: Bitmap, degrees: Int): Bitmap? {
        val matrix = Matrix()
        matrix.setRotate(degrees.toFloat())
        val filter = degrees % 90 != 0
        return Bitmap.createBitmap(
            beforeBmp,
            0,
            0,
            beforeBmp.width,
            beforeBmp.height,
            matrix,
            filter
        )
    }

    fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return out.toByteArray()
    }
}