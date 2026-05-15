package com.eflglobal.visitorsapp.data.remote

import com.eflglobal.visitorsapp.data.remote.dto.ApiResponse
import com.eflglobal.visitorsapp.data.remote.dto.CreateVisitBody
import com.eflglobal.visitorsapp.data.remote.dto.CreateVisitorBody
import com.eflglobal.visitorsapp.data.remote.dto.StationDto
import com.eflglobal.visitorsapp.data.remote.dto.ValidateStationBody
import com.eflglobal.visitorsapp.data.remote.dto.VisitDto
import com.eflglobal.visitorsapp.data.remote.dto.VisitorDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit definition of every Visitors API endpoint the Android app talks to.
 *
 * Authentication: every call below — except [validateStation] — requires the
 * `X-API-Key` header. It is injected automatically by
 * [ApiClient]'s auth interceptor; the interface does **not** declare it.
 *
 * Base URL: provided at build time by [ApiConfig] / `BuildConfig.API_BASE_URL`.
 */
interface VisitorsApi {

    // ── Setup (unauthenticated) ──────────────────────────────────────────────

    /**
     * Exchanges a station code (typed by the operator on the StationSetup
     * screen) for the credentials this device will use forever after.
     * Persist the returned `api_key` in [SecureStore] immediately.
     */
    @POST("v1/auth/station")
    suspend fun validateStation(
        @Body body: ValidateStationBody
    ): ApiResponse<StationDto>

    // ── Visitors ─────────────────────────────────────────────────────────────

    @GET("v1/visitors/search")
    suspend fun searchVisitors(
        @Query("q") query: String
    ): ApiResponse<List<VisitorDto>>

    @POST("v1/visitors")
    suspend fun createVisitor(
        @Body body: CreateVisitorBody
    ): ApiResponse<VisitorDto>

    @PUT("v1/visitors/{id}")
    suspend fun updateVisitor(
        @Path("id") visitorId: String,
        @Body body: CreateVisitorBody
    ): ApiResponse<VisitorDto>

    /**
     * Returns the most recent visit for a visitor. Backend returns HTTP 404
     * with `code = NO_VISITS` when the visitor has no history.
     */
    @GET("v1/visitors/{id}/latest-visit")
    suspend fun latestVisit(
        @Path("id") visitorId: String
    ): ApiResponse<VisitDto>

    // ── Visits ───────────────────────────────────────────────────────────────

    /**
     * Cross-station read: any valid `X-API-Key` of the same tenant can fetch
     * any visit. Used by the re-entry flow (Phase 6) to look up a visit
     * scanned at a different station.
     */
    @GET("v1/visits/{id}")
    suspend fun getVisit(
        @Path("id") visitId: String
    ): ApiResponse<VisitDto>

    @POST("v1/visits")
    suspend fun createVisit(
        @Body body: CreateVisitBody
    ): ApiResponse<VisitDto>

    @GET("v1/visits/active")
    suspend fun activeVisits(): ApiResponse<List<VisitDto>>

    @PATCH("v1/visits/{id}/checkout")
    suspend fun checkout(
        @Path("id") visitId: String
    ): ApiResponse<VisitDto>

    // ── Visit images ─────────────────────────────────────────────────────────

    /**
     * Uploads a JPEG attached to an existing visit. `type` is one of
     * `personal_photo`, `doc_front`, `doc_back`. The backend rejects payloads
     * larger than 5 MB; the client should target <2 MB after compression.
     */
    @Multipart
    @POST("v1/visits/{id}/images")
    suspend fun uploadVisitImage(
        @Path("id") visitId: String,
        @Part("type") type: RequestBody,
        @Part image: MultipartBody.Part
    ): ApiResponse<Unit>

    /**
     * Streams the raw bytes of a visit image. `type` is `personal_photo`,
     * `doc_front` or `doc_back`. Authenticated cross-station read — any
     * tenant station's key works.
     */
    @Streaming
    @GET("v1/visits/{id}/images/{type}")
    suspend fun downloadVisitImage(
        @Path("id") visitId: String,
        @Path("type") type: String
    ): ResponseBody

    /**
     * Same as [downloadVisitImage] but accepts a fully-resolved URL (e.g. the
     * `url` field inside [com.eflglobal.visitorsapp.data.remote.dto.VisitImageDto]).
     * Useful when the backend returns absolute URLs in the visit envelope.
     */
    @Streaming
    @GET
    suspend fun downloadByUrl(
        @Url url: String
    ): ResponseBody
}

