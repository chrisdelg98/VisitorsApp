package com.eflglobal.visitorsapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eflglobal.visitorsapp.data.local.LanguagePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanguageViewModel(application: Application) : AndroidViewModel(application) {

    private val languagePreferences = LanguagePreferences(application)

    private val _selectedLanguage = MutableStateFlow("es")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    init {
        viewModelScope.launch {
            languagePreferences.selectedLanguage.collect { language ->
                _selectedLanguage.value = language
            }
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            languagePreferences.saveLanguage(languageCode)
            _selectedLanguage.value = languageCode
        }
    }
}

