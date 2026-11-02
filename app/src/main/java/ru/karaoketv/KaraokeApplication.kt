package ru.karaoketv

import android.app.Application
import androidx.room.Room
import ru.karaoketv.data.KaraokeDatabase

class KaraokeApplication : Application() {
    val database: KaraokeDatabase by lazy {
        Room.databaseBuilder(this, KaraokeDatabase::class.java, "karaoke-tv.db")
            .addMigrations(KaraokeDatabase.MIGRATION_1_2)
            .addMigrations(KaraokeDatabase.MIGRATION_2_3)
            .addMigrations(KaraokeDatabase.MIGRATION_3_4)
            .build()
    }
}
