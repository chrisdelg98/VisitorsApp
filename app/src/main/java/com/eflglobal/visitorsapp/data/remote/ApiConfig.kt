package com.eflglobal.visitorsapp.data.remote

import android.content.Context
import com.eflglobal.visitorsapp.BuildConfig

/**
 * Resolves the API base URL.
 *
 * Priority:
 *  1. Per-device override stored via [SecureStore.setApiBaseOverride] (hidden
 *     "tech settings" screen — lets ops repoint an installed APK to a new
 *     backend without recompiling).
 *  2. The compile-time value injected by `build.gradle.kts` per buildType
 *     ([BuildConfig.API_BASE_URL]).
 *
 * The returned value always ends with `/`, so Retrofit can append relative
 * endpoint paths directly.
 */
object ApiConfig {

    fun baseUrl(ctx: Context): String {
        val override = SecureStore.apiBaseOverride(ctx)
        return override ?: BuildConfig.API_BASE_URL
    }
}

