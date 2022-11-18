package com.example.facemeshdetection

import android.R
import android.content.Context
import android.content.res.Resources
import android.preference.PreferenceManager
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions


object PreferenceUtils {
    fun getFaceMeshUseCase(context: Context): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey: String = "face_mesh_use_case"
        return sharedPreferences.getString(prefKey, FaceMeshDetectorOptions.FACE_MESH.toString())!!.toInt()
    }
}
