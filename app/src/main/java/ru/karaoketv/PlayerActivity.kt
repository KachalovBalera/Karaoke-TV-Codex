package ru.karaoketv

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.net.Uri
import ru.karaoketv.lyrics.LyricsParser
import ru.karaoketv.audio.AudioQualityMonitor
import ru.karaoketv.audio.AudioQualitySnapshot

class PlayerActivity : Activity() {
    private var playerView: WebView? = null
    private var lyricsView: TextView? = null
    private var qualityView: TextView? = null
    private var latestQuality: AudioQualitySnapshot? = null
    private var audioMonitor: AudioQualityMonitor? = null
    private var playbackCompleted = false
    private var lyricsOffsetMs = 0L
    private var lastPosition = 0.0
    private val lyricLines by lazy { LyricsParser.parse(intent.getStringExtra(EXTRA_LRC).orEmpty()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lyricsOffsetMs = savedInstanceState?.getLong("lyrics_offset") ?: 0L
        playbackCompleted = savedInstanceState?.getBoolean("completed") ?: false
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val isYouTubeEmbed = Uri.parse(url).host == "www.youtube.com"
        if (!isAllowedEmbed(url)) {
            Toast.makeText(this, "Неподдерживаемый или небезопасный адрес плеера", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF080A1D.toInt()) }
        WebView(this).apply {
            playerView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !isAllowedEmbed(request.url.toString())
            }
            webChromeClient = WebChromeClient()
            if (isYouTubeEmbed) addJavascriptInterface(YouTubeBridge(), "KaraokeBridge")
            if (isYouTubeEmbed) {
                loadDataWithBaseURL("https://www.youtube.com/", youtubeShell(url, lyricLines.isNotEmpty()), "text/html", "UTF-8", null)
            } else {
                loadUrl(url)
            }
            root.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        if (isYouTubeEmbed && lyricLines.isNotEmpty()) {
            lyricsView = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
                setBackgroundColor(0x990D0B16.toInt())
                setPadding(24, 12, 24, 12)
                text = ""
            }
            root.addView(lyricsView, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 48
                leftMargin = 48
                rightMargin = 48
            })
        }
        qualityView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(0x990D0B16.toInt())
            setPadding(16, 8, 16, 8)
            text = "Микрофон: проверка…"
            background = pillBackground(0xCC131634.toInt(), 0xFF7436C4.toInt())
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = pillBackground(0xE6131634.toInt(), 0xFF8F47D4.toInt())
            val close = Button(this@PlayerActivity).apply {
                text = "Закрыть"
                setOnClickListener { onBackPressed() }
            }
            if (isYouTubeEmbed) {
                addView(Button(this@PlayerActivity).apply {
                    text = "Пауза"
                    setOnClickListener { playerView?.evaluateJavascript("try{player.pauseVideo()}catch(e){}", null) }
                })
                addView(Button(this@PlayerActivity).apply {
                    text = "Продолжить"
                    setOnClickListener { playerView?.evaluateJavascript("try{player.playVideo()}catch(e){}", null) }
                })
                if (lyricLines.isNotEmpty()) {
                    val offsetLabel = TextView(this@PlayerActivity).apply {
                        setTextColor(Color.WHITE)
                        text = "LRC ${lyricsOffsetMs} мс"
                    }
                    addView(Button(this@PlayerActivity).apply {
                        text = "Текст −0,5 с"
                        setOnClickListener {
                            lyricsOffsetMs = (lyricsOffsetMs - 500).coerceAtLeast(-30_000)
                            offsetLabel.text = "LRC ${lyricsOffsetMs} мс"
                            updateLyrics(lastPosition)
                        }
                    })
                    addView(offsetLabel)
                    addView(Button(this@PlayerActivity).apply {
                        text = "Текст +0,5 с"
                        setOnClickListener {
                            lyricsOffsetMs = (lyricsOffsetMs + 500).coerceAtMost(30_000)
                            offsetLabel.text = "LRC ${lyricsOffsetMs} мс"
                            updateLyrics(lastPosition)
                        }
                    })
                }
            }
            addView(close)
        }
        val controlScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(controls)
        }
        root.addView(controlScroll, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = if (isYouTubeEmbed && lyricLines.isNotEmpty()) dp(130) else dp(20)
        })
        val titleView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Караоке" }
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(20, 10, 20, 10)
            background = pillBackground(0xCC131634.toInt(), 0xFF8F47D4.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), 0)
            addView(titleView)
            addView(qualityView)
        }
        root.addView(header, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
        })
        setContentView(root)
        startAudioMonitoringIfAllowed()
    }

    override fun onDestroy() {
        playerView?.run {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        playerView = null
        lyricsView = null
        qualityView = null
        audioMonitor?.stop()
        audioMonitor = null
        super.onDestroy()
    }

    private fun startAudioMonitoringIfAllowed() {
        if (audioMonitor != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
            qualityView?.text = "Микрофон: разрешение не выдано"
            return
        }
        audioMonitor = AudioQualityMonitor { snapshot ->
            latestQuality = snapshot
            runOnUiThread {
                qualityView?.text = if (snapshot.signalDetected) {
                    "Микрофон: ${snapshot.score}/100 · ${snapshot.dominantFrequencyHz.toInt()} Гц"
                } else {
                    "Микрофон: сигнала нет"
                }
            }
        }.also { monitor ->
            if (!monitor.start()) qualityView?.text = "Микрофон недоступен"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startAudioMonitoringIfAllowed()
        }
    }

    private fun updateLyrics(seconds: Double) {
        runOnUiThread {
            lastPosition = seconds
            val line = lyricLines.lastOrNull { it.startMs <= (seconds * 1000).toLong() - lyricsOffsetMs }
            lyricsView?.text = line?.text.orEmpty()
        }
    }

    private fun youtubeShell(url: String, reportLyrics: Boolean): String {
        val videoId = Uri.parse(url).lastPathSegment.orEmpty()
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        return """
            <!doctype html><html><body style="margin:0;background:#000;overflow:hidden">
            <div id="player" style="width:100vw;height:100vh"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            let player;
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{width:'100%',height:'100%',videoId:'$videoId',playerVars:{autoplay:1,playsinline:1},events:{onReady:attach,onStateChange:function(e){if(e.data===0)KaraokeBridge.onEnded();}}});
            }
            function attach(){${if (reportLyrics) "setInterval(function(){if(player&&player.getCurrentTime)KaraokeBridge.onCurrentTime(player.getCurrentTime());},250);" else ""}}
            </script></body></html>
        """.trimIndent()
    }

    private inner class YouTubeBridge {
        @JavascriptInterface
        fun onCurrentTime(seconds: Double) { updateLyrics(seconds) }
        @JavascriptInterface
        fun onEnded() { runOnUiThread { playbackCompleted = true; returnResult(); finish() } }
    }

    override fun onPause() {
        playerView?.evaluateJavascript("if(typeof player!=='undefined' && player.pauseVideo)player.pauseVideo();", null)
        playerView?.onPause()
        audioMonitor?.stop()
        audioMonitor = null
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView?.onResume()
        if (qualityView != null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioMonitoringIfAllowed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("lyrics_offset", lyricsOffsetMs)
        outState.putBoolean("completed", playbackCompleted)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun isAllowedEmbed(url: String): Boolean = runCatching {
        val uri = Uri.parse(url)
        when (uri.host) {
            "www.youtube.com" -> uri.scheme == "https" && uri.path?.startsWith("/embed/") == true
            "rutube.ru" -> uri.scheme == "https" && uri.path?.startsWith("/play/embed/") == true
            "vkvideo.ru", "vk.com" -> ru.karaoketv.source.isVkEmbed(url)
            else -> false
        }
    }.getOrDefault(false)

    private fun pillBackground(fill: Int, stroke: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(fill)
            setStroke(2, stroke)
        }

    companion object {
        const val EXTRA_URL = "player_url"
        const val EXTRA_LRC = "player_lrc"
        const val EXTRA_HISTORY_ID = "history_id"
        const val EXTRA_QUALITY_SCORE = "quality_score"
        const val EXTRA_TITLE = "player_title"
        const val EXTRA_COMPLETED = "player_completed"
        private const val REQUEST_AUDIO_PERMISSION = 1001
    }

    override fun onBackPressed() {
        returnResult()
        super.onBackPressed()
    }

    private fun returnResult() {
        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        if (historyId > 0) {
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_HISTORY_ID, historyId)
                    .putExtra(EXTRA_QUALITY_SCORE, latestQuality?.score ?: -1)
                    .putExtra(EXTRA_COMPLETED, playbackCompleted)
            )
        }
    }
}
