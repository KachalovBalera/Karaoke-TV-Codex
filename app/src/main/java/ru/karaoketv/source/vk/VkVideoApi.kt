package ru.karaoketv.source.vk

import retrofit2.http.GET
import retrofit2.http.Query

interface VkVideoApi {
    @GET("method/video.search")
    suspend fun search(
        @Query("q") query: String,
        @Query("count") count: Int = 25,
        @Query("sort") sort: Int = 0,
        @Query("adult") adult: Int = 0,
        @Query("access_token") accessToken: String,
        @Query("v") apiVersion: String
    ): VkSearchResponse
}

data class VkSearchResponse(
    val response: VkSearchBody? = null,
    val error: VkApiError? = null
)

data class VkSearchBody(val count: Int = 0, val items: List<VkVideoItem> = emptyList())

data class VkVideoItem(
    val id: Long = 0,
    val owner_id: Long = 0,
    val title: String = "",
    val description: String = "",
    val duration: Int? = null,
    val photo_320: String? = null,
    val player: String? = null
)

data class VkApiError(val error_msg: String? = null, val error_code: Int? = null)
