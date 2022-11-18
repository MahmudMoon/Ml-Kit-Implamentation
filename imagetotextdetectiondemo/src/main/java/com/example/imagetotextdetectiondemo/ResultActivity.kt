package com.example.imagetotextdetectiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.imagetotextdetectiondemo.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {
    lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.tvName.text = intent?.extras?.getString("name")?:""
        binding.tvNID.text = intent?.extras?.getString("nid")?:""
        binding.tvDOB.text = intent?.extras?.getString("dob")?:""
    }
}