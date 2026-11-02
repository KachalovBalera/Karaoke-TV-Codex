package ru.karaoketv.source

import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Source

interface SourceProvider {
    val source: Source
    suspend fun searchSongs(query: String): Result<List<KaraokeVersion>>
    suspend fun getVideoDetails(id: String): Result<KaraokeVersion>
    suspend fun checkAvailability(version: KaraokeVersion): Availability
    fun getPlaybackCapability(version: KaraokeVersion): PlaybackCapability
    suspend fun openPlayback(version: KaraokeVersion): PlaybackAction
}

sealed interface Availability {
    data object Available : Availability
    data object Unavailable : Availability
    data object Unknown : Availability
}

sealed interface PlaybackAction {
    data class Embedded(val url: String) : PlaybackAction
    data class External(val url: String) : PlaybackAction
    data class Failed(val reason: String) : PlaybackAction
}
