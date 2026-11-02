package ru.karaoketv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KaraokeDao {
    @Query("SELECT * FROM songs WHERE artistKey LIKE '%' || :query || '%' OR titleKey LIKE '%' || :query || '%' ORDER BY artistKey, titleKey")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM karaoke_versions WHERE songId = :songId ORDER BY karaokeConfidence DESC")
    fun observeVersions(songId: Long): Flow<List<KaraokeVersionEntity>>

    @Query("SELECT id FROM karaoke_versions WHERE songId = :songId ORDER BY karaokeConfidence DESC, id ASC LIMIT 1")
    suspend fun findBestVersionId(songId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SongEntity): Long

    @Query("SELECT id FROM songs WHERE artistKey = :artistKey AND titleKey = :titleKey LIMIT 1")
    suspend fun findSongId(artistKey: String, titleKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVersion(version: KaraokeVersionEntity): Long

    @Query("SELECT id FROM karaoke_versions WHERE source = :source AND remoteId = :remoteId LIMIT 1")
    suspend fun findVersionId(source: Source, remoteId: String): Long?

    @Update
    suspend fun updateVersion(version: KaraokeVersionEntity)

    @Transaction
    suspend fun upsertVersion(version: KaraokeVersionEntity): Long {
        val existingId = findVersionId(version.source, version.remoteId)
        if (existingId != null) {
            updateVersion(version.copy(id = existingId))
            return existingId
        }
        val insertedId = insertVersion(version)
        if (insertedId != -1L) return insertedId
        return findVersionId(version.source, version.remoteId)
            ?: error("Version was not saved")
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRating(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE versionId = :versionId")
    suspend fun deleteRating(versionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun observeFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT songId FROM favorites ORDER BY addedAtEpochMs DESC")
    fun observeFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT s.* FROM songs s INNER JOIN favorites f ON f.songId = s.id ORDER BY f.addedAtEpochMs DESC")
    fun observeFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Insert
    suspend fun addPlaybackHistory(history: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun observePlaybackHistory(limit: Int = 100): Flow<List<PlaybackHistoryEntity>>

    @Query("UPDATE playback_history SET completed = 1, qualityScore = :qualityScore WHERE id = :historyId")
    suspend fun markPlaybackCompleted(historyId: Long, qualityScore: Int?)

    @Query("SELECT h.id, h.versionId, h.participant, h.startedAtEpochMs, h.completed, h.qualityScore, v.title FROM playback_history h INNER JOIN karaoke_versions v ON v.id = h.versionId ORDER BY h.startedAtEpochMs DESC LIMIT :limit")
    fun observePlaybackHistoryDisplay(limit: Int = 50): Flow<List<PlaybackHistoryDisplay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveParticipant(participant: ParticipantEntity)

    @Query("SELECT * FROM participants ORDER BY lastUsedAtEpochMs DESC")
    fun observeParticipants(): Flow<List<ParticipantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSearch(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Insert
    suspend fun savePlaybackFailure(failure: PlaybackFailureEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM playback_failures WHERE versionId = :versionId AND expiresAtEpochMs > :now)")
    suspend fun hasActiveFailure(versionId: Long, now: Long = System.currentTimeMillis()): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(item: QueueItemEntity): Long

    @Transaction
    suspend fun enqueueExistingSong(songId: Long, participant: String, position: Int): Long? {
        val versionId = findBestVersionId(songId) ?: return null
        return addToQueue(
            QueueItemEntity(
                versionId = versionId,
                participant = participant,
                position = position,
                addedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    @Transaction
    suspend fun enqueueVersion(song: SongEntity, version: KaraokeVersionEntity, participant: String, position: Int): Long {
        insertSong(song)
        val songId = findSongId(song.artistKey, song.titleKey) ?: error("Song was not saved")
        val versionId = upsertVersion(version.copy(songId = songId))
        return addToQueue(QueueItemEntity(versionId = versionId, participant = participant, position = position, addedAtEpochMs = System.currentTimeMillis()))
    }

    @Query("SELECT * FROM queue_items ORDER BY position ASC, addedAtEpochMs ASC")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT q.id, q.versionId, v.songId, q.participant, q.position, v.title, v.source, r.stars AS ratingStars FROM queue_items q INNER JOIN karaoke_versions v ON v.id = q.versionId LEFT JOIN ratings r ON r.versionId = q.versionId ORDER BY q.position ASC, q.addedAtEpochMs ASC")
    fun observeQueueDisplay(): Flow<List<QueueItemDisplay>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC, addedAtEpochMs ASC")
    suspend fun getQueue(): List<QueueItemEntity>

    @Update
    suspend fun updateQueue(items: List<QueueItemEntity>)

    @Query("SELECT * FROM karaoke_versions WHERE id = :id LIMIT 1")
    suspend fun getVersion(id: Long): KaraokeVersionEntity?

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteQueueItem(id: Long)

    @Transaction
    suspend fun removeFromQueue(id: Long) {
        deleteQueueItem(id)
        updateQueue(getQueue().mapIndexed { index, item -> item.copy(position = index) })
    }

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Transaction
    suspend fun moveQueueItem(id: Long, direction: Int) {
        val items = getQueue()
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + direction).coerceIn(0, items.lastIndex)
        if (from == to) return
        val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
        updateQueue(reordered.mapIndexed { index, item -> item.copy(position = index) })
    }

    @Transaction
    suspend fun skipCurrent() {
        val items = getQueue()
        if (items.isEmpty()) return
        removeFromQueue(items.first().id)
        updateQueue(items.drop(1).mapIndexed { index, item -> item.copy(position = index) })
    }

    @Transaction
    suspend fun repeatQueueItem(id: Long) {
        val item = getQueue().firstOrNull { it.id == id } ?: return
        addToQueue(item.copy(id = 0, position = getQueue().size, addedAtEpochMs = System.currentTimeMillis()))
    }

    @Query("SELECT * FROM ratings WHERE versionId = :versionId")
    fun observeRating(versionId: Long): Flow<RatingEntity?>

    @Transaction
    suspend fun replaceRating(rating: RatingEntity) = saveRating(rating)
}
