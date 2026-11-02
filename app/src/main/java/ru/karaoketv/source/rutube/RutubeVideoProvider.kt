package ru.karaoketv.source.rutube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.lyrics.LyricsParser
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Song
import ru.karaoketv.data.Source
import ru.karaoketv.source.Availability
import ru.karaoketv.source.PlaybackAction
import ru.karaoketv.source.SourceProvider

class RutubeVideoProvider(private val api: RutubeApi?, private val endpoint: String?) : SourceProvider {
    override val source: Source = Source.RUTUBE

    override suspend fun searchSongs(query: String): Result<List<KaraokeVersion>> = withContext(Dispatchers.IO) {
        val configuredApi = api ?: return@withContext Result.failure(
            IllegalStateException("RUTUBE approved search endpoint is not configured")
        )
        val configuredEndpoint = endpoint ?: return@withContext Result.failure(
            IllegalStateException("RUTUBE approved search endpoint is not configured")
        )
        runCatching {
            configuredApi.search(configuredEndpoint, "$query караоке").allItems().filter { !it.is_deleted && !it.is_locked }.mapNotNull { item ->
                val id = item.id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return@mapNotNull null
                val text = "${item.title} ${item.description}"
                KaraokeVersion(
                    id = "rutube:$id",
                    song = Song(0, query.substringBefore(" "), query.substringAfter(" ").ifBlank { query }),
                    source = Source.RUTUBE,
                    title = item.title,
                    url = item.url ?: "https://rutube.ru/video/$id/",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnail_url ?: item.picture,
                    karaokeConfidence = confidence(text),
                    playbackCapability = PlaybackCapability.EMBEDDED,
                    lyricsLrc = LyricsParser.extractLrc(item.description)
                )
            }.sortedByDescending { it.karaokeConfidence }
        }
    }

    override suspend fun getVideoDetails(id: String): Result<KaraokeVersion> =
        Result.failure(UnsupportedOperationException("RUTUBE details require a configured partner contract"))

    override suspend fun checkAvailability(version: KaraokeVersion): Availability = Availability.Unknown

    override fun getPlaybackCapability(version: KaraokeVersion): PlaybackCapability = PlaybackCapability.EMBEDDED

    override suspend fun openPlayback(version: KaraokeVersion): PlaybackAction {
        val id = version.id.substringAfter(':', "").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?: return PlaybackAction.Failed("Некорректный идентификатор RUTUBE-видео")
        return PlaybackAction.Embedded("https://rutube.ru/play/embed/$id?autoplay=true")
    }

    private fun confidence(text: String): Int {
        val normalized = text.lowercase()
        var score = 35
        if (normalized.contains("караоке") || normalized.contains("karaoke")) score += 40
        if (normalized.contains("минусовка") || normalized.contains("instrumental") || normalized.contains("lyrics")) score += 20
        if (normalized.contains("клип") || normalized.contains("official video")) score -= 35
        return score.coerceIn(0, 100)
    }
}

private fun RutubeSearchResponse.allItems(): List<RutubeVideoItem> =
    (results.orEmpty() + items.orEmpty() + data.orEmpty()).distinctBy { it.id ?: "${it.title}:${it.url}" }
