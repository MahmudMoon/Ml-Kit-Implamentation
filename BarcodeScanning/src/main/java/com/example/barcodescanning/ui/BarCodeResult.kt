package com.example.barcodescanning.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.barcodescanning.R
import com.example.barcodescanning.databinding.ActivityBarCodeResultBinding

class BarCodeResult : AppCompatActivity() {
    lateinit var binding: ActivityBarCodeResultBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarCodeResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = intent?.extras
        val title = intent?.getString("title", "")
        val body = intent?.getString("body", "")
        val type = intent?.getInt("type", 0)
        if(title!=""){
            binding.barcodetitle.text = "Title $title"
        }
        if(body!=""){
            binding.barcodebody.text = "Body $body"
        }
        if(type!=0){
            binding.barcodetype.text = "Type $type"
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}