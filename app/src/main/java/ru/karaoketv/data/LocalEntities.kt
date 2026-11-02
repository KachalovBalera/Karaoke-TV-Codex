package ru.karaoketv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs", indices = [Index(value = ["artistKey", "titleKey"], unique = true)])
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val title: String,
    val artistKey: String,
    val titleKey: String
)

@Entity(tableName = "karaoke_versions", indices = [Index("songId"), Index(value = ["source", "remoteId"], unique = true)])
data class KaraokeVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val source: Source,
    val remoteId: String,
    val title: String,
    val url: String,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val karaokeConfidence: Int = 0,
    val playbackCapability: PlaybackCapability = PlaybackCapability.UNKNOWN,
    val lyricsLrc: String? = null
)

@Entity(tableName = "ratings", primaryKeys = ["versionId"])
data class RatingEntity(
    val versionId: Long,
    val stars: Int,
    val reasons: String = "",
    val updatedAtEpochMs: Long
)

@Entity(tableName = "queue_items", indices = [Index("position")])
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val participant: String,
    val position: Int,
    val addedAtEpochMs: Long
)

@Entity(tableName = "favorites", primaryKeys = ["songId"])
data class FavoriteEntity(val songId: Long, val addedAtEpochMs: Long)

@Entity(tableName = "playback_history", indices = [Index("versionId"), Index("startedAtEpochMs")])
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val participant: String,
    val startedAtEpochMs: Long,
    val completed: Boolean,
    val qualityScore: Int? = null
)

@Entity(tableName = "participants", indices = [Index(value = ["name"], unique = true)])
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lastUsedAtEpochMs: Long
)

@Entity(tableName = "source_preferences")
data class SourcePreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val preferredSource: Source? = null
)

@Entity(tableName = "search_history", indices = [Index(value = ["query"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAtEpochMs: Long
)

@Entity(tableName = "playback_failures", indices = [Index("versionId"), Index("expiresAtEpochMs")])
data class PlaybackFailureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val reason: String,
    val failedAtEpochMs: Long,
    val expiresAtEpochMs: Long
)
