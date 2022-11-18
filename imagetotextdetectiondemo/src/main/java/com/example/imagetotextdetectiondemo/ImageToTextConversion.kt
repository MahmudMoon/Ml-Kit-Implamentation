package com.example.imagetotextdetectiondemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
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
import com.example.imagetotextdetectiondemo.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageToTextConversion : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    var imageCapture: ImageCapture? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
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
                    it.setAnalyzer(
                        cameraExecutor,
                        ImageLabelDetectionAnalyser { name, dob, nid ->
                            Log.d(TAG, "startCamera: ${name}, ${dob}, ${nid}")
                            //cameraProvider.unbindAll()
                            startActivity(Intent(this@ImageToTextConversion, ResultActivity::class.java)
                                .putExtra("name", name)
                                .putExtra("nid", nid)
                                .putExtra("dob", dob)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
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
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        startCamera()
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

    private class ImageLabelDetectionAnalyser(private val listener: ImageToTextAnalyser) :
        ImageAnalysis.Analyzer {
        var recognizer: TextRecognizer? = null
        var nameFound = false
        var dobFound = false
        var nidFound = false
        var nid: String? = null
        var dob: String? = null
        var name: String? = null
        var day: Int? = null
        var month: String? = null
        var year: String? = null
        var isProcessing = false

        init {
            isProcessing = false
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Log.d(TAG, ": called init")
        }



        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            //Log.d(TAG, "analyze: inside")
            val mediaImage = image.image
            if (mediaImage != null && !isProcessing) {
                //  Log.d(TAG, "analyze: mediaImage not null")
                val finalImage =
                    InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

                // Log.d(TAG, "analyze: mediaImage not null")
                recognizer?.process(finalImage)?.addOnSuccessListener { result ->
                    // Log.d(TAG, "analyze: barcode size ${barcodes.size}")
                    isProcessing = true
                    val resultText = result.text
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val tempLine = StringBuffer()
                                for (word in line.elements) {
                                    tempLine.append(word.text)
                                    tempLine.append(" ")
                                }
                                Log.d("TextRec", "run: raw: $tempLine")
                                if (!nameFound)
                                    nameFound = validateName(tempLine)
                                else if (!dobFound)
                                    dobFound = validateDob(tempLine)
                                else if (!nidFound)
                                    nidFound = validateNIDNo(tempLine)
                                else {
                                    Log.d("TextRec", "run: all completed")
                                    recognizer!!.close()
                                    moveToNextActivity(name, dob, nid)
                                }
                            }
                        }
                    }
                    image.close()
                }?.addOnFailureListener {
                    image.close()
                }

            }
            image.close()
        }

        private fun validateName(tempLine: StringBuffer): Boolean {
            if (tempLine.startsWith("Name:")) {
                val colPos = tempLine.indexOf(":")
                if (tempLine.length > (colPos + 1)) {
                    name = tempLine.substring(colPos + 1, tempLine.length - 1)
                    Log.d("TextRec", "validateName: " + name)
                    return true
                }
            }
            return false
        }

        private fun validateDob(tempLine: StringBuffer): Boolean {
            if (tempLine.startsWith("Date of Birth:")) {
                val colPos = tempLine.indexOf(":")
                if (tempLine.length > (colPos + 1)) {
                    dob = tempLine.substring(colPos + 1, tempLine.length - 1)
                    dob = dob?.trim()
                    Log.d("TextRec", "validateDOB: " + dob)
                    if (validDob(dob))
                        return true
                }
            }
            return false
        }

        private fun validateNIDNo(tempLine: StringBuffer): Boolean {
            if(tempLine.startsWith("ID NO:")){
                val colPos = tempLine.indexOf(":")
                if(tempLine.length>(colPos+1)) {
                    nid = tempLine.substring(colPos + 1, tempLine.length - 1)
                    Log.d("TextRec", "validate ID NO: " +nid)
                    return nid != ""
                }
            }
            return false
        }

        private fun validDob(dob: String?): Boolean {
            val dateAry = dob?.split(" ")
            if (dateAry != null) {
                for (i in 0..dateAry.size - 1) {
                    if (i == 0) {
                        val date = dateAry[i]
                        if (date != "" && validateDate(date)) {
                            day = date.toInt()
                        }
                    } else if (i == 1) {
                        val monthTmp = dateAry[i]
                        if (monthTmp != "" && validateMonthData(monthTmp)) {
                            month = getMonthPosition(monthTmp)
                        }
                    } else if (i == 2) {
                        val yearTmp = dateAry[i]
                        if (yearTmp != "" && validateYearData(yearTmp)) {
                            year = yearTmp
                        }
                    }
                }
            }
            return year != null && month != null && day != null
        }

        private fun validateDate(day: String): Boolean {
            try {
                val day = day.toInt()
                if (day in 1..31)
                    return true
            } catch (exception: Exception) {
                Log.d(TAG, "validateDate: ${exception.localizedMessage}")
            }
            return false
        }

        private fun validateMonthData(monthTmp: String): Boolean {
            return when (monthTmp.trim()) {
                "Jan" -> true
                "Feb" -> true
                "Mar" -> true
                "Apr" -> true
                "May" -> true
                "Jun" -> true
                "Jul" -> true
                "Aug" -> true
                "Sep" -> true
                "Oct" -> true
                "Nov" -> true
                "Dec" -> true
                else -> false
            }
        }

        private fun getMonthPosition(monthTmp: String): String? {
            return when (monthTmp.trim()) {
                "Jan" -> "01"
                "Feb" -> "02"
                "Mar" -> "03"
                "Apr" -> "04"
                "May" -> "05"
                "Jun" -> "06"
                "Jul" -> "07"
                "Aug" -> "08"
                "Sep" -> "09"
                "Oct" -> "10"
                "Nov" -> "11"
                "Dec" -> "12"
                else -> null
            }
        }

        private fun validateYearData(yearTmp: String): Boolean {
            try {
                val y = yearTmp.toInt()
                if (y in 1900..(Calendar.getInstance().get(Calendar.YEAR)) - 18) return true
            } catch (e: Exception) {
                Log.d(TAG, "validateYearData: ${e.localizedMessage}")
            }
            return false
        }


        private fun moveToNextActivity(name: String?, dob: String?, nid: String?) {
            listener(name, dob, nid)
            isProcessing = false
        }
    }
}

typealias ImageToTextAnalyser = (name: String?, dob: String?, nid: String?) -> Unit