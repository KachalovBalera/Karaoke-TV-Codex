package ru.karaoketv.source

import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Source
import android.net.Uri

class PlaybackRouter {
    fun resolve(version: KaraokeVersion): PlaybackResolution {
        return when (version.playbackCapability) {
            PlaybackCapability.EMBEDDED -> {
                val remoteId = version.id.substringAfter(':', "").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                val embedUrl = when (version.source) {
                    Source.YOUTUBE -> remoteId?.let { "https://www.youtube.com/embed/$it?autoplay=1&playsinline=1" }
                    Source.RUTUBE -> remoteId?.let { "https://rutube.ru/play/embed/$it?autoplay=true" }
                    Source.VK_VIDEO -> version.url.takeIf { isVkEmbed(it) }
                }
                if (embedUrl == null) PlaybackResolution.Unsupported("Источник не предоставил разрешённый embed-адрес")
                else PlaybackResolution.Embedded(embedUrl, version.lyricsLrc)
            }
            PlaybackCapability.EXTERNAL -> if (isAllowedExternalUrl(version.source, version.url)) {
                PlaybackResolution.External(version.url, null)
            } else {
                PlaybackResolution.Unsupported("Источник предоставил небезопасный адрес")
            }
            PlaybackCapability.UNSUPPORTED -> PlaybackResolution.Unsupported("Эта версия не поддерживает воспроизведение")
            PlaybackCapability.UNKNOWN -> PlaybackResolution.Unknown
        }
    }

    private fun isAllowedExternalUrl(source: Source, value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        if (uri.scheme != "https") return@runCatching false
        when (source) {
            Source.YOUTUBE -> uri.host == "www.youtube.com" || uri.host == "youtube.com"
            Source.RUTUBE -> uri.host == "rutube.ru" || uri.host == "www.rutube.ru"
            Source.VK_VIDEO -> uri.host == "vkvideo.ru" || uri.host == "vk.com"
        }
    }.getOrDefault(false)
}

fun isVkEmbed(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme == "https" && uri.host in setOf("vk.com", "vkvideo.ru") && uri.path == "/video_ext.php" && uri.userInfo == null
}.getOrDefault(false)

sealed interface PlaybackResolution {
    data class Embedded(val url: String, val lyricsLrc: String? = null, val historyId: Long? = null) : PlaybackResolution
    data class External(val url: String, val historyId: Long? = null) : PlaybackResolution
    data class Unsupported(val reason: String) : PlaybackResolution
    data object Unknown : PlaybackResolution
}
