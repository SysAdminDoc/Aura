package com.freevibe.data.remote.wikimedia

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

interface WikimediaPotdApi {

    @GET("feed/v1/wikipedia/en/featured/{year}/{month}/{day}")
    suspend fun getFeatured(
        @Path("year") year: String,
        @Path("month") month: String,
        @Path("day") day: String,
    ): WikimediaFeaturedResponse

    companion object {
        const val BASE_URL = "https://api.wikimedia.org/"
    }
}

@JsonClass(generateAdapter = true)
data class WikimediaFeaturedResponse(
    @Json(name = "image") val image: WikimediaPotdImage? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaPotdImage(
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: WikimediaText? = null,
    @Json(name = "artist") val artist: WikimediaText? = null,
    @Json(name = "thumbnail") val thumbnail: WikimediaThumbnail? = null,
    @Json(name = "image") val image: WikimediaThumbnail? = null,
    @Json(name = "file_page") val filePage: String? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaText(
    @Json(name = "text") val text: String = "",
)

@JsonClass(generateAdapter = true)
data class WikimediaThumbnail(
    @Json(name = "source") val source: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
)
