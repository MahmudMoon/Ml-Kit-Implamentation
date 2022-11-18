package com.example.facedetectiondemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facedetectiondemo.databinding.ActivityFaceDetectionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectionActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    var imageCapture: ImageCapture? = null
    lateinit var binding: ActivityFaceDetectionBinding
    lateinit var mGraphicOverlay:  GraphicOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mGraphicOverlay = binding.graphicOverlay

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
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
                    it.setAnalyzer(cameraExecutor,  FaceDetectionAnalyser{ faces ->
                        processFaceContourDetectionResult(faces)
                    })
//
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFaceContourDetectionResult(faces: List<Face>) {
        // Task completed successfully
        if (faces.size == 0) {
            Log.d(TAG, "processFaceContourDetectionResult: No face found")
            return
        }
          mGraphicOverlay.clear()
        for (i in faces.indices) {
            val face: Face = faces[i]
            val faceGraphic = FaceContourGraphic(mGraphicOverlay)
            mGraphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }


    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
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

    private class FaceDetectionAnalyser(private val listener: FaceDetectionListener) : ImageAnalysis.Analyzer {
        var detection: FaceDetector? = null
        init {
//            val highAccuracyOpts = FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                .build()

            // Real-time contour detection
            val realTimeOpts = FaceDetectorOptions.Builder()
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
             detection = FaceDetection.getClient(realTimeOpts)
        }


        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            //Log.d(TAG, "analyze: inside")
            val mediaImage = image.image
            if(mediaImage != null){
                //  Log.d(TAG, "analyze: mediaImage not null")
                val finalImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                detection?.process(finalImage)?.addOnSuccessListener { faces ->
                    Log.d(TAG, "analyze: ${faces.size}")
                    moveToNextActivity(faces)
                   // processFaceContourDetectionResult(faces)
                    image.close()
                }?.addOnFailureListener {
                    image.close()
                }

            }
            image.close()
        }
        private fun moveToNextActivity(faces: List<Face>) {
            listener(faces)
        }
    }
}

typealias FaceDetectionListener = (faces: List<Face>) -> Unit