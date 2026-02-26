package com.eflglobal.visitorsapp.ui.localization

/**
 * Strong enum for all supported app languages.
 * Add a new entry here + a values-XX/strings.xml to support a new language.
 */
enum class AppLanguage(
    /** BCP-47 language tag used by Android locale system. */
    val tag: String,
    /** Native name shown in the language selector. */
    val nativeName: String
) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    PORTUGUESE("pt", "Português"),
    FRENCH("fr", "Français");

    companion object {
        /** Returns the enum entry for [tag], defaulting to [SPANISH]. */
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SPANISH

        val default: AppLanguage get() = SPANISH
    }
}

