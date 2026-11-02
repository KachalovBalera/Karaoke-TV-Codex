package ru.karaoketv

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Updates {
    private const val REPO = "KachalovBalera/Karaoke-TV-Codex"
    private val client = OkHttpClient.Builder().callTimeout(180, TimeUnit.SECONDS).build()
    data class Release(val version: String, val notes: String, val url: String, val hash: String, val size: Long)
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").build()).execute().use { response ->
            if (response.code == 404) return@withContext null
            check(response.isSuccessful) { "GitHub: HTTP ${response.code}" }
            val json = JSONObject(response.body!!.string())
            check(!json.optBoolean("draft") && !json.optBoolean("prerelease"))
            val tag = json.getString("tag_name").removePrefix("v")
            val parts = tag.split('.').map { it.toIntOrNull() ?: error("Неверная версия") }
            val current = BuildConfig.VERSION_NAME.split('.').map { it.toInt() }
            require(parts.size == 3)
            val comparison = parts.zip(current).firstOrNull { it.first != it.second }
            if (comparison == null || comparison.first < comparison.second) return@withContext null
            val assets = json.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == "karaoke-tv.apk" } ?: error("APK ещё не опубликован")
            val url = asset.getString("browser_download_url")
            require(url.startsWith("https://github.com/$REPO/releases/download/"))
            val hash = asset.optString("digest").removePrefix("sha256:")
            require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Нет SHA-256 релиза" }
            Release(tag, json.optString("body"), url, hash, asset.getLong("size"))
        }
    }

    suspend fun download(activity: ComponentActivity, release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.size in 1..250_000_000)
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "karaoke-tv.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                check(response.isSuccessful)
                response.body!!.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(total <= release.size)
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        progress((total * 100 / release.size).toInt())
                    }
                    check(total == release.size)
                } }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(release.hash, true)) { "SHA-256 не совпал" }
            val pm = activity.packageManager
            val candidate = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("APK повреждён")
            val installed = pm.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            check(candidate.packageName == installed.packageName) { "Чужое приложение" }
            check(candidate.longVersionCode > installed.longVersionCode) { "Версия не новее установленной" }
            check(candidate.versionName == release.version) { "Версия APK не совпадает с релизом" }
            check((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= android.os.Build.VERSION.SDK_INT)
            val incoming = candidate.signingInfo?.apkContentsSigners?.toSet().orEmpty()
            check(incoming.isNotEmpty() && incoming == installed.signingInfo?.apkContentsSigners?.toSet()) { "Подпись APK не совпадает" }
            file
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun checkAtStartup(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val release = try { latest() } catch (_: Exception) { null }
            if (release != null && !activity.isFinishing) AlertDialog.Builder(activity)
                .setTitle("Доступно обновление ${release.version}").setMessage(release.notes)
                .setNegativeButton("Позже", null).setPositiveButton("Подробнее") { _, _ ->
                    activity.startActivity(Intent(activity, UpdateActivity::class.java))
                }.show()
        }
    }
}

class UpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val status = TextView(this).apply { text = "Проверка обновлений…"; textSize = 20f }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        val action = Button(this).apply { text = "Скачать обновление"; isEnabled = false }
        layout.addView(status); layout.addView(progress); layout.addView(action)
        layout.addView(Button(this).apply { text = "Назад"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(layout) })
        lifecycleScope.launch {
            try {
                val release = Updates.latest()
                status.text = if (release == null) "Новых релизов нет. Установлена ${BuildConfig.VERSION_NAME}" else "Доступно ${release.version}\n\n${release.notes}"
                if (release != null) {
                    action.isEnabled = true
                    action.setOnClickListener {
                        action.isEnabled = false
                        lifecycleScope.launch {
                            try {
                                val apk = Updates.download(this@UpdateActivity, release) { p -> runOnUiThread { progress.progress = p } }
                                status.text = "APK проверен. Подтвердите установку."
                                action.text = "Установить"
                                action.isEnabled = true
                                action.setOnClickListener {
                                    try {
                                        if (!packageManager.canRequestPackageInstalls()) {
                                            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                                        } else {
                                            val uri = FileProvider.getUriForFile(this@UpdateActivity, "$packageName.updates", apk)
                                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                        }
                                    } catch (_: Exception) { status.text = "Системный установщик недоступен" }
                                }
                            } catch (e: Exception) { status.text = "Обновление не установлено: ${e.message}. Вернитесь и повторите проверку." }
                        }
                    }
                }
            } catch (e: Exception) { status.text = "Не удалось проверить обновления: ${e.message}" }
        }
    }
}
