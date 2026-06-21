package com.freevibe.data.remote.lemmy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lemmy API for fetching crowd-voted wallpaper posts.
 * No auth required — public read-only endpoints. Rate-limited to ~1 req/s.
 * Primary community: !wallpapers@lemmy.world
 */
interface LemmyApi {

    @GET("api/v3/post/list")
    suspend fun getPosts(
        @Query("community_name") communityName: String = DEFAULT_COMMUNITY,
        @Query("sort") sort: String = "Hot",
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("type_") type: String = "All",
    ): LemmyPostListResponse

    companion object {
        const val BASE_URL = "https://lemmy.world/"
        const val DEFAULT_COMMUNITY = "wallpapers"
    }
}

@JsonClass(generateAdapter = true)
data class LemmyPostListResponse(
    @Json(name = "posts") val posts: List<LemmyPostView> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LemmyPostView(
    @Json(name = "post") val post: LemmyPost = LemmyPost(),
    @Json(name = "counts") val counts: LemmyPostCounts = LemmyPostCounts(),
    @Json(name = "creator") val creator: LemmyPerson = LemmyPerson(),
)

@JsonClass(generateAdapter = true)
data class LemmyPost(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "url") val url: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "ap_id") val apId: String = "",
    @Json(name = "published") val published: String = "",
    @Json(name = "nsfw") val nsfw: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class LemmyPostCounts(
    @Json(name = "score") val score: Int = 0,
    @Json(name = "upvotes") val upvotes: Int = 0,
    @Json(name = "downvotes") val downvotes: Int = 0,
    @Json(name = "comments") val comments: Int = 0,
)

@JsonClass(generateAdapter = true)
data class LemmyPerson(
    @Json(name = "name") val name: String = "",
    @Json(name = "display_name") val displayName: String? = null,
)
