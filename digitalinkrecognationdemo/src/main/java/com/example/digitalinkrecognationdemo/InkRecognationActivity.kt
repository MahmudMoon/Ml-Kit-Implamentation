package com.example.digitalinkrecognationdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.digitalinkrecognationdemo.databinding.ActivityInkRecognationBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class InkRecognationActivity : AppCompatActivity() {
    lateinit var binding: ActivityInkRecognationBinding
    lateinit var clear: Button
    lateinit var submit: Button
    private val TAG = "InkRecognationActivity"
    lateinit var textView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInkRecognationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textView = binding.signaturetext
        clear = binding.clear
        submit = binding.submit
        clear.setOnClickListener { binding.customview.clearCustomView() }
        submit.setOnClickListener {
           val data =  binding.customview.submitResult()

            Log.d(TAG, "onCreate: $data")
        }
         binding.customview.textData.observe(this){
//             Log.d(TAG, "onCreate: $it")
             textView.text = it
         }
        CoroutineScope(Dispatchers.Default).launch {
            binding.customview.testData.collect {
                Log.d(TAG, "$it")
            }

        }

    }

}