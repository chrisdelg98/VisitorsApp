package com.eflglobal.visitorsapp.data.remote

import android.content.Context
import com.eflglobal.visitorsapp.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds and caches the singleton [VisitorsApi].
 *
 * Responsibilities:
 *  - Inject the `X-API-Key` header on every authenticated request (read live
 *    from [SecureStore]; the call goes through unauthenticated if no key has
 *    been provisioned yet — only `validateStation` should be in that state).
 *  - Standard JSON headers + sane timeouts.
 *  - HTTP body logging on debug builds; headers-only (no body) on release.
 *
 * `application context` is captured to avoid leaking activities; it is safe
 * because the singleton lives for the whole process.
 */
object ApiClient {

    @Volatile private var instance: VisitorsApi? = null
    @Volatile private var instanceBaseUrl: String? = null

    fun get(ctx: Context): VisitorsApi {
        val baseUrl = ApiConfig.baseUrl(ctx)
        val cached = instance
        if (cached != null && baseUrl == instanceBaseUrl) return cached

        synchronized(this) {
            val again = instance
            if (again != null && baseUrl == instanceBaseUrl) return again

            val appCtx = ctx.applicationContext
            val client = buildOkHttp(appCtx)
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val api = retrofit.create(VisitorsApi::class.java)
            instance = api
            instanceBaseUrl = baseUrl
            return api
        }
    }

    /** Force a rebuild on the next [get] call (e.g. after changing the base URL override). */
    fun invalidate() {
        synchronized(this) {
            instance = null
            instanceBaseUrl = null
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            // Explicit Unit adapter — needed by endpoints declared as
            // ApiResponse<Unit> (image upload, checkout). Moshi has no
            // built-in adapter for Kotlin's Unit type.
            .add(Unit::class.javaObjectType, UnitJsonAdapter)
            // KotlinJsonAdapterFactory handles non-codegen classes & defaults.
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun buildOkHttp(appCtx: Context): OkHttpClient {
        val auth = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
            SecureStore.apiKey(appCtx)?.let { key ->
                builder.addHeader("X-API-Key", key)
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

