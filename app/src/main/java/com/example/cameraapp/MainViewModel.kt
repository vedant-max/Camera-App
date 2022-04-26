package com.example.cameraapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


//To set splash screen visible for some time
class MainViewModel: ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val coordinates: ArrayList<Pair<Float ,Float>> = ArrayList()
    val isLoading = _isLoading.asStateFlow()
    init {
        viewModelScope.launch {
            delay(2000)
            _isLoading.value = false
        }
    }
}