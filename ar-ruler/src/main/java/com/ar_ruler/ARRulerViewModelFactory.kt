package com.ar_ruler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ARRulerViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ARRulerViewModel() as T
    }
}