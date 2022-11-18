package com.example.selfisegmentdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.selfisegmentdemo.databinding.ActivitySelfiSegmentBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SelfiSegmentActivity : AppCompatActivity() {
    lateinit var binding: ActivitySelfiSegmentBinding
    private lateinit var cameraExecutor: ExecutorService
    var imageCapture: ImageCapture? = null
    lateinit var mGraphicOverlay:  GraphicOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelfiSegmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mGraphicOverlay = binding.graphicsoverlay

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
                    it.setAnalyzer(cameraExecutor, SelfiSegmentAnalyser { segmentationMask->
                        mGraphicOverlay.clear()
                        mGraphicOverlay.add(SegmentationGraphic(
                            mGraphicOverlay,
                            segmentationMask!!
                        ))
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

    private class SelfiSegmentAnalyser(private val listener: SelfiSegmentListener) :
        ImageAnalysis.Analyzer {
        var segmenter: Segmenter? = null

        init {
            val options =
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                    .enableRawSizeMask()
                    .build()
            segmenter = Segmentation.getClient(options)
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            //Log.d(TAG, "analyze: inside")
            val mediaImage = image.image
            if (mediaImage != null) {
                //  Log.d(TAG, "analyze: mediaImage not null")
                val finalImage =
                    InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                segmenter?.process(finalImage)?.addOnSuccessListener { segmentationMask ->

//                    val mask = segmentationMask.getBuffer()
//                    val maskWidth = segmentationMask.getWidth()
//                    val maskHeight = segmentationMask.getHeight()
//                    for (y in 0..maskHeight - 1 step 1) {
//                        for (x in 0..maskWidth - 1 step 1) {
//                            // Gets the confidence of the (x,y) pixel in the mask being in the foreground.
//                            val foregroundConfidence = mask.getFloat()
//                            Log.d(TAG, "analyze: $x, $y, $foregroundConfidence")
//                        }
//                    }
                    processSelfiData(segmentationMask)
                    // processFaceContourDetectionResult(faces)
                    image.close()
                }?.addOnFailureListener {
                    image.close()
                }?.addOnCompleteListener {
                    image.close()
                }

            }
            image.close()
        }

        private fun processSelfiData(segmentationMask: SegmentationMask?) {
            listener(segmentationMask)
        }

    }

}

typealias SelfiSegmentListener = (segmentationMask: SegmentationMask?) -> Unit