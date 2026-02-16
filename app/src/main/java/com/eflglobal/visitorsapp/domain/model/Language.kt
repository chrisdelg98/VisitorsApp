package com.eflglobal.visitorsapp.domain.model

enum class Language(val code: String, val displayName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): Language {
            return values().find { it.code == code } ?: SPANISH
        }
    }
}

