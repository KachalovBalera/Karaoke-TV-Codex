package ru.karaoketv.source.youtube

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

class YouTubeProvider(private val api: YouTubeApi) : SourceProvider {
    override val source: Source = Source.YOUTUBE
    override suspend fun searchSongs(query: String): Result<List<KaraokeVersion>> = withContext(Dispatchers.IO) {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) return@withContext Result.failure(IllegalStateException("YouTube API key is not configured"))
        runCatching {
            api.search(query = "$query karaoke", key = BuildConfig.YOUTUBE_API_KEY).items.mapNotNull { item ->
                val id = item.id.videoId ?: return@mapNotNull null
                val text = "${item.snippet.title} ${item.snippet.description}"
                val confidence = confidence(text)
                KaraokeVersion(
                    id = "youtube:$id",
                    song = Song(0, artist = query.substringBefore(" "), title = query.substringAfter(" ").ifBlank { query }),
                    source = Source.YOUTUBE,
                    title = item.snippet.title,
                    url = "https://www.youtube.com/watch?v=$id",
                    thumbnailUrl = item.snippet.thumbnails?.high?.url ?: item.snippet.thumbnails?.medium?.url,
                    karaokeConfidence = confidence,
                    playbackCapability = PlaybackCapability.EMBEDDED,
                    lyricsLrc = LyricsParser.extractLrc(item.snippet.description)
                )
            }.sortedByDescending { it.karaokeConfidence }
        }
    }

    override suspend fun getVideoDetails(id: String): Result<KaraokeVersion> = withContext(Dispatchers.IO) {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) return@withContext Result.failure(IllegalStateException("YouTube API key is not configured"))
        val remoteId = id.substringAfter(':', id).takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?: return@withContext Result.failure(IllegalArgumentException("Некорректный идентификатор YouTube-видео"))
        runCatching {
            val item = api.details(id = remoteId, key = BuildConfig.YOUTUBE_API_KEY).items.firstOrNull()
                ?: error("Видео YouTube не найдено")
            KaraokeVersion(
                id = "youtube:$remoteId",
                song = Song(0, artist = "", title = item.snippet.title),
                source = Source.YOUTUBE,
                title = item.snippet.title,
                url = "https://www.youtube.com/watch?v=$remoteId",
                durationSeconds = item.contentDetails?.duration?.let(::parseDurationSeconds),
                thumbnailUrl = item.snippet.thumbnails?.high?.url ?: item.snippet.thumbnails?.medium?.url,
                karaokeConfidence = confidence("${item.snippet.title} ${item.snippet.description}"),
                playbackCapability = if (item.status?.embeddable == false) PlaybackCapability.EXTERNAL else PlaybackCapability.EMBEDDED,
                lyricsLrc = LyricsParser.extractLrc(item.snippet.description)
            )
        }
    }

    override suspend fun checkAvailability(version: KaraokeVersion): Availability =
        getVideoDetails(version.id).fold(
            onSuccess = { Availability.Available },
            onFailure = { Availability.Unknown }
        )

    override fun getPlaybackCapability(version: KaraokeVersion): PlaybackCapability =
        version.playbackCapability.takeUnless { it == PlaybackCapability.UNKNOWN } ?: PlaybackCapability.EMBEDDED

    override suspend fun openPlayback(version: KaraokeVersion): PlaybackAction {
        val remoteId = version.id.substringAfter(':', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?: return PlaybackAction.Failed("Некорректный идентификатор YouTube-видео")
        return PlaybackAction.Embedded(
            "https://www.youtube.com/embed/$remoteId?autoplay=1&playsinline=1"
        )
    }

    private fun confidence(text: String): Int {
        val key = text.lowercase()
        var score = 35
        if (key.contains("karaoke") || key.contains("караоке")) score += 35
        if (key.contains("lyrics") || key.contains("текст") || key.contains("минусовка")) score += 20
        if (key.contains("official video") || key.contains("клип")) score -= 35
        return score.coerceIn(0, 100)
    }

    private fun parseDurationSeconds(value: String): Int? {
        val match = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").matchEntire(value) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0
        val minutes = match.groupValues[2].toLongOrNull() ?: 0
        val seconds = match.groupValues[3].toLongOrNull() ?: 0
        return (hours * 3600 + minutes * 60 + seconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
