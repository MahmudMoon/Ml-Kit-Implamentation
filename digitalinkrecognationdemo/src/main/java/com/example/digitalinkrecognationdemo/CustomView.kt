package com.example.digitalinkrecognationdemo

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.MutableLiveData
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.Flow

class CustomView(context: Context, attrs: AttributeSet) : SignaturePad(context, attrs) {
    private val TAG = "InkRecognationActivity"
    var inkBuilder = Ink.builder()
    lateinit var strokeBuilder: Ink.Stroke.Builder
    var recognizer: DigitalInkRecognizer? = null
    var model: DigitalInkRecognitionModel? = null
    var textData: MutableLiveData<String> = MutableLiveData()
    var testData = emptyFlow<String>()

    init {
        var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null
        try {
            modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
        } catch (e: MlKitException) {
            Log.d(TAG, "onCreate: ${e.localizedMessage}")
        }

        if (modelIdentifier == null) {
            Log.d(TAG, "onCreate: null")
        }
        model =
            DigitalInkRecognitionModel.builder(modelIdentifier!!).build()


// Get a recognizer for the language
        recognizer =
            DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model!!).build()
            )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG, "onTouchEvent: custom view")
        super.onTouchEvent(event)
        val action = event?.actionMasked
        val x = event?.x
        val y = event?.y
        val t = System.currentTimeMillis()
        Log.d(TAG, "onTouch: $x, $y, $t")

        // If your setup does not provide timing information, you can omit the
        // third paramater (t) in the calls to Ink.Point.create
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                strokeBuilder = Ink.Stroke.builder()
                strokeBuilder.addPoint(Ink.Point.create(x!!, y!!, t))
            }
            MotionEvent.ACTION_MOVE -> strokeBuilder!!.addPoint(
                Ink.Point.create(
                    x!!,
                    y!!,
                    t
                )
            )
            MotionEvent.ACTION_UP -> {
                strokeBuilder.addPoint(Ink.Point.create(x!!, y!!, t))
                inkBuilder.addStroke(strokeBuilder.build())
            }
            else -> {
                // Action not relevant for ink construction
            }
        }
        return true
    }


    fun clearCustomView() {
        Log.d(TAG, "clearCustomView: ")
        clearView()
    }

    fun submitResult() {
        Log.d(TAG, "callOnClick: Submitted")
        val ink = inkBuilder.build()
        recognizer?.recognize(ink)
            ?.addOnSuccessListener { result: RecognitionResult ->
                Log.i(TAG, result.candidates[0].text)
                textData.postValue(result.candidates[0].text)
                testData = flow {
                    emit(result.candidates[0].text)
                }
                return@addOnSuccessListener
            }
            ?.addOnFailureListener { e: Exception ->
                Log.e(TAG, "Error during recognition: $e")
                //var model: DigitalInkRecognitionModel =  ...
                val remoteModelManager = RemoteModelManager.getInstance()
                model?.let {
                    remoteModelManager.download(it, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            Log.i(TAG, "Model downloaded")
                        }
                        .addOnFailureListener { e: Exception ->
                            Log.e(TAG, "Error while downloading a model: $e")
                        }
                }
            }
    }



}