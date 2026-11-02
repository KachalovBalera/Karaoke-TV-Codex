package ru.karaoketv.source.rutube

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Adapter for an approved RUTUBE partner/search endpoint.
 * The endpoint shape is intentionally configured by the integrator because
 * RUTUBE does not publish one stable public search contract for all clients.
 */
interface RutubeApi {
    @GET
    suspend fun search(@Url endpoint: String, @Query("query") query: String, @Query("limit") limit: Int = 25): RutubeSearchResponse
}

data class RutubeSearchResponse(
    val results: List<RutubeVideoItem>? = null,
    val items: List<RutubeVideoItem>? = null,
    val data: List<RutubeVideoItem>? = null
)

data class RutubeVideoItem(
    val id: String? = null,
    val is_deleted: Boolean = false,
    val is_locked: Boolean = false,
    val title: String = "",
    val description: String = "",
    val duration: Int? = null,
    val thumbnail_url: String? = null,
    val picture: String? = null,
    val url: String? = null,
    val embed_url: String? = null
)
