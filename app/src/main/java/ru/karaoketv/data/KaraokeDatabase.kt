package ru.karaoketv.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class KaraokeConverters {
    @TypeConverter fun sourceToString(value: Source): String = value.name
    @TypeConverter fun stringToSource(value: String): Source = Source.valueOf(value)
    @TypeConverter fun capabilityToString(value: PlaybackCapability): String = value.name
    @TypeConverter fun stringToCapability(value: String): PlaybackCapability = PlaybackCapability.valueOf(value)
}

@Database(
    entities = [
        SongEntity::class, KaraokeVersionEntity::class, RatingEntity::class, QueueItemEntity::class,
        FavoriteEntity::class, PlaybackHistoryEntity::class, ParticipantEntity::class,
        SourcePreferenceEntity::class, SearchHistoryEntity::class, PlaybackFailureEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(KaraokeConverters::class)
abstract class KaraokeDatabase : RoomDatabase() {
    abstract fun karaokeDao(): KaraokeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`songId` INTEGER NOT NULL, `addedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playback_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `versionId` INTEGER NOT NULL, `participant` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_versionId` ON `playback_history` (`versionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_startedAtEpochMs` ON `playback_history` (`startedAtEpochMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `participants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `lastUsedAtEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_participants_name` ON `participants` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `source_preferences` (`id` INTEGER NOT NULL, `preferredSource` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL, `searchedAtEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playback_failures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `versionId` INTEGER NOT NULL, `reason` TEXT NOT NULL, `failedAtEpochMs` INTEGER NOT NULL, `expiresAtEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_failures_versionId` ON `playback_failures` (`versionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_failures_expiresAtEpochMs` ON `playback_failures` (`expiresAtEpochMs`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `karaoke_versions` ADD COLUMN `lyricsLrc` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playback_history` ADD COLUMN `qualityScore` INTEGER")
            }
        }
    }
}
