package ru.karaoketv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import ru.karaoketv.KaraokeApplication
import ru.karaoketv.BuildConfig
import ru.karaoketv.data.KaraokeDao
import ru.karaoketv.data.QueueItemDisplay
import ru.karaoketv.data.PlaybackHistoryDisplay
import ru.karaoketv.data.SongEntity
import ru.karaoketv.catalog.CatalogScoring
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.KaraokeVersionEntity
import ru.karaoketv.data.PlaybackHistoryEntity
import ru.karaoketv.data.ParticipantEntity
import ru.karaoketv.data.RatingEntity
import ru.karaoketv.data.SearchHistoryEntity
import ru.karaoketv.catalog.UnifiedSearchService
import ru.karaoketv.source.rutube.RutubeApi
import ru.karaoketv.source.rutube.RutubeVideoProvider
import ru.karaoketv.source.youtube.YouTubeApi
import ru.karaoketv.source.youtube.YouTubeProvider
import ru.karaoketv.source.vk.VkVideoApi
import ru.karaoketv.source.vk.VkVideoProvider
import ru.karaoketv.source.PlaybackRouter
import ru.karaoketv.source.PlaybackResolution
import ru.karaoketv.remote.LocalControlServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ActiveSong(
    val title: String,
    val source: ru.karaoketv.data.Source,
    val thumbnailUrl: String? = null,
    val participant: String? = null
)

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val playbackRouter = PlaybackRouter()
    private val remoteServer = LocalControlServer(application, { queueJson() }) { command, args ->
        when (command) {
            "remove" -> args["id"]?.toLongOrNull()?.let { removeFromQueue(it) }
            "clear" -> clearQueue()
        }
    }
    private val _remoteAddress = MutableStateFlow<String?>(null)
    private val _adminAddress = MutableStateFlow<String?>(null)
    private val dao: KaraokeDao = (application as KaraokeApplication).database.karaokeDao()
    private val query = MutableStateFlow("")
    private var searchJob: Job? = null
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _remoteResults = MutableStateFlow<List<KaraokeVersion>>(emptyList())
    private val _remoteError = MutableStateFlow<String?>(null)
    private val _playback = MutableStateFlow<PlaybackResolution?>(null)
    private val _activeSong = MutableStateFlow<ActiveSong?>(null)
    val remoteResults: StateFlow<List<KaraokeVersion>> = _remoteResults
    val remoteError: StateFlow<String?> = _remoteError
    val playback: StateFlow<PlaybackResolution?> = _playback.asStateFlow()
    val activeSong: StateFlow<ActiveSong?> = _activeSong.asStateFlow()
    val remoteAddress: StateFlow<String?> = _remoteAddress.asStateFlow()
    val adminAddress: StateFlow<String?> = _adminAddress.asStateFlow()
    val searchQuery: StateFlow<String> = query
    val songs = query
        .flatMapLatest { dao.searchSongs(CatalogScoring.key(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val queue = dao.observeQueueDisplay().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = dao.observePlaybackHistoryDisplay().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteSongIds = dao.observeFavoriteSongIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = dao.observeFavoriteSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        if (query.value == value) return
        searchJob?.cancel()
        _searching.value = false
        _remoteResults.value = emptyList()
        _remoteError.value = null
        query.value = value
    }

    fun searchAllSources() {
        searchJob?.cancel()
        val normalizedQuery = query.value.trim()
        if (normalizedQuery.isBlank()) {
            _remoteResults.value = emptyList()
            _remoteError.value = "Введите исполнителя или название песни"
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            _remoteError.value = null
            _remoteResults.value = emptyList()
            try {
                val result = createSearchService().search(normalizedQuery)
                coroutineContext.ensureActive()
                _remoteResults.value = result.songs.flatMap { it.versions }.distinctBy { it.id }
                _remoteError.value = result.sourceErrors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                dao.saveSearch(SearchHistoryEntity(query = normalizedQuery, searchedAtEpochMs = System.currentTimeMillis()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _remoteError.value = "Поиск не выполнен: ${error.message ?: "проверьте подключение"}"
            } finally {
                if (coroutineContext[Job]?.isActive == true) _searching.value = false
            }
        }
    }

    fun searchYouTube() = searchAllSources()

    private fun createSearchService(): UnifiedSearchService {
        val retrofit = Retrofit.Builder()
            .client(okhttp3.OkHttpClient.Builder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
        val providers = mutableListOf<ru.karaoketv.source.SourceProvider>()
        providers += YouTubeProvider(
            retrofit.baseUrl("https://www.googleapis.com/youtube/v3/").build().create(YouTubeApi::class.java)
        )
        providers += VkVideoProvider(
            retrofit.baseUrl("https://api.vk.com/").build().create(VkVideoApi::class.java)
        )
        val rutubeEndpoint = BuildConfig.RUTUBE_SEARCH_ENDPOINT.trim().ifBlank { "https://rutube.ru/api/search/video/?format=json" }
        val rutubeApi = rutubeEndpoint?.let {
            retrofit.baseUrl("https://rutube.ru/").build().create(RutubeApi::class.java)
        }
        providers += RutubeVideoProvider(rutubeApi, rutubeEndpoint)
        return UnifiedSearchService(providers)
    }

    fun addSongToQueue(song: SongEntity, participant: String) {
        viewModelScope.launch {
            dao.enqueueExistingSong(
                songId = song.id,
                participant = participant.ifBlank { "Гость" },
                position = queue.value.size
            )
            remoteServer.broadcastQueueChanged()
        }
    }

    fun addVersionToQueue(version: KaraokeVersion, participant: String) = viewModelScope.launch {
        val artistKey = CatalogScoring.key(version.song.artist)
        val titleKey = CatalogScoring.key(version.song.title)
        dao.enqueueVersion(
            song = SongEntity(artist = version.song.artist, title = version.song.title, artistKey = artistKey, titleKey = titleKey),
            version = KaraokeVersionEntity(
                songId = 0,
                source = version.source,
                remoteId = version.id.substringAfter(':', version.id),
                title = version.title,
                url = version.url,
                durationSeconds = version.durationSeconds,
                thumbnailUrl = version.thumbnailUrl,
                karaokeConfidence = version.karaokeConfidence,
                playbackCapability = version.playbackCapability,
                lyricsLrc = version.lyricsLrc
            ),
            participant = participant.ifBlank { "Гость" },
            position = queue.value.size
        )
        remoteServer.broadcastQueueChanged()
    }

    fun playVersion(version: KaraokeVersion) {
        _activeSong.value = ActiveSong(
            title = version.title,
            source = version.source,
            thumbnailUrl = version.thumbnailUrl
        )
        _playback.value = playbackRouter.resolve(version)
    }

    fun removeFromQueue(id: Long) {
        viewModelScope.launch {
            dao.removeFromQueue(id)
            remoteServer.broadcastQueueChanged()
        }
    }
    fun clearQueue() {
        viewModelScope.launch {
            dao.clearQueue()
            remoteServer.broadcastQueueChanged()
        }
    }
    fun moveQueueItem(id: Long, direction: Int) {
        viewModelScope.launch {
            dao.moveQueueItem(id, direction)
            remoteServer.broadcastQueueChanged()
        }
    }
    fun repeatQueueItem(id: Long) {
        viewModelScope.launch {
            dao.repeatQueueItem(id)
            remoteServer.broadcastQueueChanged()
        }
    }
    fun skipCurrent() {
        viewModelScope.launch {
            dao.skipCurrent()
            remoteServer.broadcastQueueChanged()
        }
    }

    fun playQueueItem(item: QueueItemDisplay) = viewModelScope.launch {
        val storedVersion = dao.getVersion(item.versionId) ?: return@launch
        _activeSong.value = ActiveSong(
            title = storedVersion.title,
            source = storedVersion.source,
            thumbnailUrl = storedVersion.thumbnailUrl,
            participant = item.participant
        )
        val historyId = dao.addPlaybackHistory(
            PlaybackHistoryEntity(
                versionId = item.versionId,
                participant = item.participant,
                startedAtEpochMs = System.currentTimeMillis(),
                completed = false
            )
        )
        dao.saveParticipant(ParticipantEntity(name = item.participant, lastUsedAtEpochMs = System.currentTimeMillis()))
        val resolution = playbackRouter.resolve(
                KaraokeVersion(
                    id = "${storedVersion.source.name.lowercase()}:${storedVersion.remoteId}",
                    song = ru.karaoketv.data.Song(storedVersion.songId, "", ""),
                    source = storedVersion.source,
                    title = storedVersion.title,
                    url = storedVersion.url,
                    durationSeconds = storedVersion.durationSeconds,
                    thumbnailUrl = storedVersion.thumbnailUrl,
                    karaokeConfidence = storedVersion.karaokeConfidence,
                    playbackCapability = storedVersion.playbackCapability,
                    lyricsLrc = storedVersion.lyricsLrc
                )
            )
        _playback.value = when (resolution) {
            is PlaybackResolution.Embedded -> resolution.copy(historyId = historyId)
            is PlaybackResolution.External -> resolution.copy(historyId = historyId)
            else -> resolution
        }
    }

    fun completePlayback(historyId: Long, qualityScore: Int?) = viewModelScope.launch {
        dao.markPlaybackCompleted(historyId, qualityScore)
    }

    fun clearPlayback() { _playback.value = null }

    fun clearActivePlayback() { _activeSong.value = null }

    fun rateVersion(versionId: Long, stars: Int, reasons: Set<String> = emptySet()) = viewModelScope.launch {
        dao.saveRating(
            RatingEntity(
                versionId = versionId,
                stars = stars.coerceIn(1, 5),
                reasons = reasons.joinToString(","),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun toggleFavorite(songId: Long) = viewModelScope.launch {
        if (dao.isFavorite(songId)) {
            dao.removeFavorite(songId)
        } else {
            dao.addFavorite(ru.karaoketv.data.FavoriteEntity(songId = songId, addedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun startRemoteControl() {
        runCatching {
            remoteServer.start()
            remoteServer.address() to remoteServer.address(admin = true)
        }.onSuccess { (guest, admin) ->
            _remoteError.value = null
            _remoteAddress.value = guest
            _adminAddress.value = admin
        }.onFailure {
            _remoteAddress.value = null
            _adminAddress.value = null
            _remoteError.value = it.message ?: "Не удалось запустить управление с телефона"
        }
    }

    private fun queueJson(): String = queue.value.joinToString(prefix = "[", postfix = "]") {
        "{\"id\":${it.id},\"participant\":${jsonString(it.participant)},\"title\":${jsonString(it.title)},\"source\":${jsonString(it.source.name)},\"position\":${it.position}}"
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    override fun onCleared() {
        remoteServer.stop()
        super.onCleared()
    }
}
