package ru.karaoketv.source

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackCapability
import ru.karaoketv.data.Song
import ru.karaoketv.data.Source

class PlaybackRouterTest {
    private val song = Song(1, "Кино", "Группа крови")

    @Test fun youtubeUsesOfficialEmbed() {
        val version = KaraokeVersion("youtube:abc", song, Source.YOUTUBE, "karaoke", "https://youtube.com/watch?v=abc", playbackCapability = PlaybackCapability.EMBEDDED)
        assertEquals("https://www.youtube.com/embed/abc?autoplay=1&playsinline=1", (PlaybackRouter().resolve(version) as PlaybackResolution.Embedded).url)
    }

    @Test fun unknownDoesNotGuessAPlayer() {
        val version = KaraokeVersion("vk:1", song, Source.VK_VIDEO, "video", "https://vkvideo.ru/video-1_1")
        assertEquals(PlaybackResolution.Unknown, PlaybackRouter().resolve(version))
    }
    @Test fun embeddedIdCannotInjectAnotherUrl() {
        val version = KaraokeVersion(
            "youtube:https://evil.example/video",
            song,
            Source.YOUTUBE,
            "karaoke",
            "https://youtube.com/watch?v=abc",
            playbackCapability = PlaybackCapability.EMBEDDED
        )
        assertEquals(
            PlaybackResolution.Unsupported("Источник не предоставил разрешённый embed-адрес"),
            PlaybackRouter().resolve(version)
        )
    }

    @Test fun externalPlaybackRejectsNonHttpsUrl() {
        val version = KaraokeVersion(
            "rutube:abc",
            song,
            Source.RUTUBE,
            "external",
            "javascript:alert(1)",
            playbackCapability = PlaybackCapability.EXTERNAL
        )
        assertEquals(
            PlaybackResolution.Unsupported("Источник предоставил небезопасный адрес"),
            PlaybackRouter().resolve(version)
        )
    }

    @Test fun externalPlaybackAcceptsOfficialSourceHost() {
        val version = KaraokeVersion(
            "youtube:abc",
            song,
            Source.YOUTUBE,
            "external",
            "https://www.youtube.com/watch?v=abc",
            playbackCapability = PlaybackCapability.EXTERNAL
        )
        assertEquals(
            PlaybackResolution.External("https://www.youtube.com/watch?v=abc"),
            PlaybackRouter().resolve(version)
        )
    }
}
