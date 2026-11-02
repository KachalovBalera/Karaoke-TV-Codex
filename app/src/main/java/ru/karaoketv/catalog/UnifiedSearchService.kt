package ru.karaoketv.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.source.SourceProvider

data class SongGroup(
    val artist: String,
    val title: String,
    val versions: List<KaraokeVersion>
)

data class UnifiedSearchResult(
    val songs: List<SongGroup>,
    val sourceErrors: List<String>
)

class UnifiedSearchService(private val providers: List<SourceProvider>) {
    suspend fun search(query: String): UnifiedSearchResult = supervisorScope {
        val results = providers.map { provider ->
            async {
                val result = try { provider.searchSongs(query) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
                provider.source.name to result
            }
        }.awaitAll()

        val versions = results.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
        val buckets = mutableListOf<MutableList<KaraokeVersion>>()
        versions.forEach { version ->
            val bucket = buckets.firstOrNull { existing ->
                val sample = existing.first()
                CatalogScoring.isLikelySameSong(sample.song.artist, sample.song.title, version.song.artist, version.song.title)
            }
            if (bucket == null) buckets.add(mutableListOf(version)) else bucket.add(version)
        }
        val groups = buckets
            .map { matching ->
                val best = matching.maxByOrNull { CatalogScoring.rank(it) }!!
                SongGroup(
                    artist = best.song.artist,
                    title = best.song.title,
                    versions = matching.sortedByDescending { CatalogScoring.rank(it) }
                )
            }
            .sortedByDescending { CatalogScoring.rank(it.versions.first()) }

        UnifiedSearchResult(
            songs = groups,
            sourceErrors = results.mapNotNull { (name, result) -> result.exceptionOrNull()?.let { "$name: ${it.message ?: "ошибка"}" } }
        )
    }
}
