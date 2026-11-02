package ru.karaoketv.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Song
import ru.karaoketv.data.Source
import ru.karaoketv.source.Availability
import ru.karaoketv.source.PlaybackAction
import ru.karaoketv.source.SourceProvider

class UnifiedSearchServiceTest {
    @Test fun failedSourceDoesNotHideSuccessfulResults() = runBlocking {
        val song = Song(1, "Кино", "Группа крови")
        val good = fake(Result.success(listOf(KaraokeVersion("youtube:a", song, Source.YOUTUBE, "Караоке", "https://youtube.com/a", playbackCapability = PlaybackCapability.EMBEDDED))))
        val failed = fake(Result.failure(IllegalStateException("offline")))
        val result = UnifiedSearchService(listOf(good, failed)).search("Кино Группа крови")
        assertEquals(1, result.songs.size)
        assertTrue(result.sourceErrors.single().contains("offline"))
    }

    private fun fake(result: Result<List<KaraokeVersion>>) = object : SourceProvider {
        override val source = Source.YOUTUBE
        override suspend fun searchSongs(query: String) = result
        override suspend fun getVideoDetails(id: String): Result<KaraokeVersion> = Result.failure(UnsupportedOperationException())
        override suspend fun checkAvailability(version: KaraokeVersion) = Availability.Unknown
        override fun getPlaybackCapability(version: KaraokeVersion) = version.playbackCapability
        override suspend fun openPlayback(version: KaraokeVersion) = PlaybackAction.Failed("test")
    }
}
