package com.freevibe.data.remote.nasa

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NASA Astronomy Picture of the Day (APOD) API.
 * No auth required — uses the public DEMO_KEY by default (30 req/hour, 50/day).
 * Callers should cache aggressively since the image changes at most once per day.
 */
interface NasaApodApi {

    @GET("planetary/apod")
    suspend fun getApod(
        @Query("api_key") apiKey: String = DEFAULT_KEY,
        @Query("date") date: String? = null,
        @Query("count") count: Int? = null,
        @Query("thumbs") thumbs: Boolean = true,
    ): NasaApodResponse

    @GET("planetary/apod")
    suspend fun getApodList(
        @Query("api_key") apiKey: String = DEFAULT_KEY,
        @Query("count") count: Int = 10,
        @Query("thumbs") thumbs: Boolean = true,
    ): List<NasaApodResponse>

    companion object {
        const val BASE_URL = "https://api.nasa.gov/"
        const val DEFAULT_KEY = "DEMO_KEY"
    }
}

@JsonClass(generateAdapter = true)
data class NasaApodResponse(
    @Json(name = "date") val date: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "explanation") val explanation: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "hdurl") val hdUrl: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "media_type") val mediaType: String = "image",
    @Json(name = "copyright") val copyright: String? = null,
    @Json(name = "service_version") val serviceVersion: String = "",
)
