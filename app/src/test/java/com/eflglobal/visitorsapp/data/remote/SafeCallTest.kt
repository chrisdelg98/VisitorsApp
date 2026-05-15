package com.eflglobal.visitorsapp.data.remote

import com.eflglobal.visitorsapp.data.remote.dto.ApiResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Tests the [safeCall] funnel — every failure surface the rest of the app
 * relies on:
 *
 *  - Offline / dropped socket → [ApiErrorCode.NETWORK_UNAVAILABLE] (transient,
 *    triggers `Result.retry()` in the SyncWorker).
 *  - HTTP 401 with tagged code → [ApiErrorCode.API_KEY_INVALID] (auth failure,
 *    triggers `SecureStore.clearStation()` in the SyncWorker).
 *  - HTTP 422 with `errors` map → [ApiErrorCode.VALIDATION_ERROR] + populated
 *    fieldErrors.
 *  - HTTP 429 → [ApiErrorCode.RATE_LIMIT_EXCEEDED] (transient).
 *  - HTTP 5xx → [ApiErrorCode.UNKNOWN] (transient via httpStatus check).
 *  - Envelope with `success=false` → tagged code passes through unchanged.
 */
class SafeCallTest {

    private lateinit var server: MockWebServer
    private lateinit var api: FakeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val moshi = Moshi.Builder()
            .add(Unit::class.javaObjectType, UnitJsonAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create(FakeApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Happy path ─────────────────────────────────────────────────────────-

    @Test
    fun `success envelope returns data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":{"value":"ok"}}""")
        )
        val data = safeCall { api.ping() }
        assertEquals("ok", data.value)
    }

    // ── Offline / network failure ──────────────────────────────────────────-

    @Test
    fun `socket disconnect maps to NETWORK_UNAVAILABLE`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.NETWORK_UNAVAILABLE, e.code)
            assertTrue("must be transient so the worker retries", e.isTransient)
        }
    }

    @Test
    fun `server shutdown maps to NETWORK_UNAVAILABLE`() = runTest {
        server.shutdown() // simulate airplane mode / unreachable host
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.NETWORK_UNAVAILABLE, e.code)
            assertTrue(e.isTransient)
        }
    }

    // ── 401 — auth failures ────────────────────────────────────────────────-

    @Test
    fun `401 with tagged API_KEY_INVALID surfaces auth failure`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"key revoked","code":"API_KEY_INVALID"}""")
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.API_KEY_INVALID, e.code)
            assertEquals(401, e.httpStatus)
            assertTrue("must trigger SecureStore wipe", e.isAuthFailure)
            assertEquals(false, e.isTransient)
        }
    }

    @Test
    fun `401 with no body defaults to API_KEY_INVALID`() = runTest {
        // Some upstream proxies strip the body. The status code alone must be enough.
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.API_KEY_INVALID, e.code)
            assertTrue(e.isAuthFailure)
        }
    }

    @Test
    fun `401 with API_KEY_MISSING tag is also an auth failure`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"no key","code":"API_KEY_MISSING"}""")
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.API_KEY_MISSING, e.code)
            assertTrue(e.isAuthFailure)
        }
    }

    @Test
    fun `401 STATION_INVALID is NOT auth-wipe`() = runTest {
        // Setup-screen typo — must surface to the UI without nuking SecureStore.
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"wrong code","code":"STATION_INVALID"}""")
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.STATION_INVALID, e.code)
            assertEquals(false, e.isAuthFailure)
        }
    }

    // ── 422 — validation errors ────────────────────────────────────────────-

    @Test
    fun `422 returns VALIDATION_ERROR with fieldErrors`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "success": false,
                      "message": "Validation failed",
                      "errors": {
                        "first_name": ["The first name field is required."],
                        "email": ["The email must be a valid email address."]
                      }
                    }
                    """.trimIndent()
                )
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code)
            assertNotNull(e.fieldErrors)
            assertEquals(
                "The first name field is required.",
                e.fieldErrors!!["first_name"]?.first()
            )
            assertEquals(false, e.isTransient)
        }
    }

    // ── 429 — rate limit ───────────────────────────────────────────────────-

    @Test
    fun `429 maps to RATE_LIMIT_EXCEEDED and is transient`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.RATE_LIMIT_EXCEEDED, e.code)
            assertTrue(e.isTransient)
        }
    }

    // ── 5xx — server side ──────────────────────────────────────────────────-

    @Test
    fun `503 maps to UNKNOWN but is transient via httpStatus`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.UNKNOWN, e.code)
            assertEquals(503, e.httpStatus)
            assertTrue("5xx must drive WorkManager retry", e.isTransient)
        }
    }

    @Test
    fun `500 with tagged code preserves the backend code`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"success":false,"message":"boom","code":"UNKNOWN"}""")
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.UNKNOWN, e.code)
            assertTrue(e.isTransient)
        }
    }

    // ── 2xx with success=false ─────────────────────────────────────────────-

    @Test
    fun `200 with success false propagates the tagged code`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":false,"message":"already closed","code":"VISIT_ALREADY_COMPLETED"}""")
        )
        try {
            safeCall { api.ping() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.VISIT_ALREADY_COMPLETED, e.code)
            assertEquals(false, e.isTransient)
            assertEquals(false, e.isAuthFailure)
        }
    }

    // ── safeCallUnit variant ───────────────────────────────────────────────-

    @Test
    fun `safeCallUnit accepts a success envelope without data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true}""")
        )
        // Should not throw.
        safeCallUnit { api.pingUnit() }
    }

    @Test
    fun `safeCallUnit on 401 still wipes through isAuthFailure`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"key revoked","code":"API_KEY_INVALID"}""")
        )
        try {
            safeCallUnit { api.pingUnit() }
            fail("Expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isAuthFailure)
        }
    }

    // ── Fixtures ───────────────────────────────────────────────────────────-

    @JsonClass(generateAdapter = false)
    data class Probe(val value: String)

    interface FakeApi {
        @GET("ping")
        suspend fun ping(): ApiResponse<Probe>

        @GET("ping")
        suspend fun pingUnit(): ApiResponse<Unit>
    }

    @Suppress("unused")
    private fun moshiTypeBypassUnused() {
        // Keep the Moshi parameterized-type helper imported even if not used directly
        Types.newParameterizedType(ApiResponse::class.java, Probe::class.java)
    }
}


