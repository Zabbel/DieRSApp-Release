package com.zabbel.diersapp.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppStateViewModel : ViewModel() {
    private val _isIntentionalBackground = MutableStateFlow(false)
    val isIntentionalBackground: StateFlow<Boolean> = _isIntentionalBackground

    fun setIntentionalBackground(value: Boolean) {
        _isIntentionalBackground.value = value
    }
}
