package ru.karaoketv.data

enum class Source { YOUTUBE, VK_VIDEO, RUTUBE }
enum class PlaybackCapability { EMBEDDED, EXTERNAL, UNSUPPORTED, UNKNOWN }

data class Song(val id: Long, val artist: String, val title: String)

data class QueueItemDisplay(
    val id: Long,
    val versionId: Long,
    val songId: Long,
    val participant: String,
    val position: Int,
    val title: String,
    val source: Source,
    val ratingStars: Int? = null
)

data class PlaybackHistoryDisplay(
    val id: Long,
    val versionId: Long,
    val participant: String,
    val startedAtEpochMs: Long,
    val completed: Boolean,
    val title: String,
    val qualityScore: Int? = null
)

data class KaraokeVersion(
    val id: String,
    val song: Song,
    val source: Source,
    val title: String,
    val url: String,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val karaokeConfidence: Int = 0,
    val playbackCapability: PlaybackCapability = PlaybackCapability.UNKNOWN,
    val lyricsLrc: String? = null
)
