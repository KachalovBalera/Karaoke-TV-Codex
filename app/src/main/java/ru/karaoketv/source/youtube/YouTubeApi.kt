package ru.karaoketv.source.youtube

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 25,
        @Query("videoEmbeddable") embeddable: String = "true",
        @Query("key") key: String
    ): SearchResponse

    @GET("videos")
    suspend fun details(
        @Query("part") part: String = "snippet,contentDetails,status",
        @Query("id") id: String,
        @Query("key") key: String
    ): DetailsResponse
}

data class SearchResponse(val items: List<SearchItem> = emptyList())
data class SearchItem(val id: SearchId, val snippet: Snippet)
data class SearchId(val videoId: String? = null)
data class DetailsResponse(val items: List<DetailItem> = emptyList())
data class DetailItem(val id: String, val snippet: Snippet, val contentDetails: ContentDetails? = null, val status: VideoStatus? = null)
data class Snippet(val title: String = "", val description: String = "", val thumbnails: Thumbnails? = null)
data class Thumbnails(val medium: Thumbnail? = null, val high: Thumbnail? = null)
data class Thumbnail(val url: String = "")
data class ContentDetails(val duration: String? = null, val definition: String? = null)
data class VideoStatus(val embeddable: Boolean? = null)
