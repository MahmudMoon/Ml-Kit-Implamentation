package com.example.commonlibrary.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.commonlibrary.CameraXViewModel

class ViewModelFactoryProvider  : ViewModelProvider.Factory{
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if(modelClass.isAssignableFrom(CameraXViewModel::class.java))
            return CameraXViewModel() as T
        else{
            throw IllegalStateException("ViewModel not found")
        }
    }
}