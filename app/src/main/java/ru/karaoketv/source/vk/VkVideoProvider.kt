package ru.karaoketv.source.vk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.karaoketv.BuildConfig
import ru.karaoketv.lyrics.LyricsParser
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Song
import ru.karaoketv.data.Source
import ru.karaoketv.source.Availability
import ru.karaoketv.source.PlaybackAction
import ru.karaoketv.source.SourceProvider

class VkVideoProvider(private val api: VkVideoApi) : SourceProvider {
    override val source: Source = Source.VK_VIDEO

    override suspend fun searchSongs(query: String): Result<List<KaraokeVersion>> = withContext(Dispatchers.IO) {
        if (BuildConfig.VK_API_TOKEN.isBlank()) {
            return@withContext Result.failure(IllegalStateException("VK Видео API token is not configured"))
        }
        runCatching {
            val response = api.search(
                query = "$query караоке",
                accessToken = BuildConfig.VK_API_TOKEN,
                apiVersion = BuildConfig.VK_API_VERSION
            )
            response.response?.items.orEmpty().map { item ->
                val text = "${item.title} ${item.description}"
                val embedUrl = item.player?.takeIf { ru.karaoketv.source.isVkEmbed(it) }
                KaraokeVersion(
                    id = "vk:${item.owner_id}_${item.id}",
                    song = Song(0, query.substringBefore(" "), query.substringAfter(" ").ifBlank { query }),
                    source = Source.VK_VIDEO,
                    title = item.title,
                    url = embedUrl ?: "https://vkvideo.ru/video${item.owner_id}_${item.id}",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.photo_320,
                    karaokeConfidence = confidence(text),
                    playbackCapability = if (embedUrl != null) PlaybackCapability.EMBEDDED else PlaybackCapability.EXTERNAL,
                    lyricsLrc = LyricsParser.extractLrc(item.description)
                )
            }.sortedByDescending { it.karaokeConfidence }
                .ifEmpty { response.error?.let { error("VK API ${it.error_code ?: ""}: ${it.error_msg ?: "ошибка"}") } ?: emptyList() }
        }
    }

    override suspend fun getVideoDetails(id: String): Result<KaraokeVersion> =
        Result.failure(UnsupportedOperationException("VK video details require a separate video.get contract"))

    override suspend fun checkAvailability(version: KaraokeVersion): Availability = Availability.Unknown

    override fun getPlaybackCapability(version: KaraokeVersion): PlaybackCapability = PlaybackCapability.EXTERNAL

    override suspend fun openPlayback(version: KaraokeVersion): PlaybackAction = PlaybackAction.External(version.url)

    private fun confidence(text: String): Int {
        val normalized = text.lowercase()
        var score = 35
        if (normalized.contains("караоке") || normalized.contains("karaoke")) score += 40
        if (normalized.contains("минусовка") || normalized.contains("instrumental") || normalized.contains("lyrics")) score += 20
        if (normalized.contains("клип") || normalized.contains("official video")) score -= 35
        return score.coerceIn(0, 100)
    }
}
