package com.example.barcodescanning.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barcodescanning.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.greenrobot.eventbus.EventBus
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    lateinit var binding: ActivityMainBinding
    var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.imageCaptureButton.setOnClickListener {
            takePhoto()
        }
        binding.videoCaptureButton.setOnClickListener {

        }

    }


    override fun onResume() {
        super.onResume()
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarCodeAnalyser{ title, body, type ->
                        Log.d(TAG, "startCamera: ${title}, ${body}, ${type}")
                        startActivity(Intent(this@BarcodeScannerActivity, BarCodeResult::class.java)
                            .putExtra("title", title)
                            .putExtra("body",body)
                            .putExtra("type",type))
                    })
//
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    private class BarCodeAnalyser(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        val options = BarcodeScannerOptions.Builder()
            .build()

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            //Log.d(TAG, "analyze: inside")
            val mediaImage = image.image
            if(mediaImage != null){
              //  Log.d(TAG, "analyze: mediaImage not null")
                val finalImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                val scanner = BarcodeScanning.getClient(options)
                scanner.process(finalImage).addOnSuccessListener { barcodes ->
                   // Log.d(TAG, "analyze: barcode size ${barcodes.size}")
                    for (barcode in barcodes) {
                        val bounds = barcode.boundingBox
                        val corners = barcode.cornerPoints

                        val rawValue = barcode.rawValue

                        val valueType = barcode.valueType

                        // See API reference for complete list of supported types
                        when (valueType) {
                            Barcode.TYPE_WIFI -> {
                                val ssid = barcode.wifi!!.ssid
                                val password = barcode.wifi!!.password
                                val type = barcode.wifi!!.encryptionType
                                moveToNextActivity(ssid, password, type)
                            }
                            Barcode.TYPE_URL -> {
                                val title = barcode.url!!.title
                                val url = barcode.url!!.url
                                moveToNextActivity(title,url, 0)
                            }

                        }
                    }
                    image.close()
                }.addOnFailureListener {
                    image.close()
                }

            }
            image.close()
        }

        private fun moveToNextActivity(ssid: String?, password: String?, type: Int) {
            EventBus.getDefault().post("stop")
            listener(ssid, password, type)
        }
    }

}

typealias LumaListener = (title: String?, body: String?, type: Int?) -> Unit