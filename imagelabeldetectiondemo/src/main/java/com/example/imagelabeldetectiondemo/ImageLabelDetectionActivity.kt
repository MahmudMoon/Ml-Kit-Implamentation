package com.example.imagelabeldetectiondemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.imagelabeldetectiondemo.databinding.ActivityImageLabelDetectionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageLabelDetectionActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    lateinit var binding:ActivityImageLabelDetectionBinding
    var imageCapture: ImageCapture? = null
    lateinit var tvObjectDetails: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageLabelDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tvObjectDetails = binding.objcetDescription
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
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
                    it.setAnalyzer(cameraExecutor, ImageLabelDetectionAnalyser{ title, body, type ->
                        Log.d(TAG, "startCamera: ${title}, ${body}, ${type}")
                        tvObjectDetails.text = title
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
    private class ImageLabelDetectionAnalyser(private val listener: LumaListener) : ImageAnalysis.Analyzer {
        var labeler: ImageLabeler? = null
        init {
            labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            Log.d(TAG, ": called init")
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            //Log.d(TAG, "analyze: inside")
           // Log.d(TAG, "analyze: inside ")
            val mediaImage = image.image
            if(mediaImage != null){
                //  Log.d(TAG, "analyze: mediaImage not null")
                val finalImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

               // Log.d(TAG, "analyze: mediaImage not null")
                labeler?.process(finalImage)?.addOnSuccessListener { labels ->
                    // Log.d(TAG, "analyze: barcode size ${barcodes.size}")
                    for (label in labels) {
                        val text = label.text
                        val confidence = label.confidence
                        val index = label.index
                        Log.d(TAG, "analyze: $text, $confidence, $index")
                        moveToNextActivity(text, confidence, index)
                    }
                    image.close()
                }?.addOnFailureListener {
                    image.close()
                }

            }
            image.close()
        }

        private fun moveToNextActivity(ssid: String?, password: Float?, type: Int) {
            listener(ssid, password, type)
        }
    }
}

typealias LumaListener = (title: String?, body: Float?, type: Int?) -> Unit