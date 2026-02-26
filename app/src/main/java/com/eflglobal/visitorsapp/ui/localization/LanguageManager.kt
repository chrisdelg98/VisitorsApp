package com.eflglobal.visitorsapp.ui.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * LanguageManager — locale override via ContextWrapper.
 *
 * Strategy:
 *   We wrap the original context (which must be the Activity context so that
 *   Accompanist permissions and other Activity-aware APIs can still walk up
 *   the chain and find the Activity via Context.findActivity()).
 *
 *   The wrapper overrides getResources() to return a Resources object with
 *   the selected locale applied, so ALL stringResource() / getString() calls
 *   resolve to the correct values-XX/strings.xml without breaking the chain.
 */
object LanguageManager {

    /**
     * Returns a [ContextWrapper] whose resources use [language]'s locale,
     * but whose base context is still [activityContext] so the Activity
     * remains reachable via Context.findActivity().
     */
    fun wrapContext(activityContext: Context, language: AppLanguage): Context =
        LocalizedContextWrapper(activityContext, language.tag)

    fun wrapContext(activityContext: Context, tag: String): Context =
        wrapContext(activityContext, AppLanguage.fromTag(tag))

    // ── Internal wrapper ────────────────────────────────────────────────────

    private class LocalizedContextWrapper(
        base: Context,
        private val languageTag: String
    ) : ContextWrapper(base) {

        private val localizedResources: Resources by lazy {
            val locale = Locale.forLanguageTag(languageTag)
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList(locale))
            base.createConfigurationContext(config).resources
        }

        override fun getResources(): Resources = localizedResources

        // Make sure any new Context created from this wrapper also gets localized resources
        override fun createConfigurationContext(overrideConfig: Configuration): Context =
            LocalizedContextWrapper(super.createConfigurationContext(overrideConfig), languageTag)
    }
}
