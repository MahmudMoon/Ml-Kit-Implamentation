package com.example.languagedetectiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import com.example.languagedetectiondemo.databinding.ActivityLanguageDetectionBinding
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier

class LanguageDetectionActivity : AppCompatActivity() {
    lateinit var binding: ActivityLanguageDetectionBinding
    lateinit var listview: ListView
    lateinit var listAdapter: ArrayAdapter<String>
    private val TAG = "LanguageDetectionActivi"
    lateinit var languageIdentifier: LanguageIdentifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        languageIdentifier = LanguageIdentification
            .getClient(
                LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.34f)
                .build())

        listview = binding.languageList
        val texts = arrayOf(
            "I eat rice", "मैं चावल खता हूँ", "أنا أكل الرز",
            "je mange du riz", "я ем рис", "আমি ভাত খাই"
        )
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,texts)
        listview.adapter = listAdapter
        listview.setOnItemClickListener { adapterView, view, i, l ->
            Log.d(TAG, "onCreate: $i")
            languageIdentifier.identifyLanguage(texts.get(i))
                .addOnSuccessListener { language ->
                    if(language == "und") Toast.makeText(applicationContext, "Failed to identify language",Toast.LENGTH_SHORT).show()
                        else Toast.makeText(applicationContext, language  ,Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(applicationContext, "Failed to identify language",Toast.LENGTH_SHORT).show() }
                .addOnCompleteListener {

                }
        }

    }
}