package ru.karaoketv

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.karaoketv.data.KaraokeVersion
import ru.karaoketv.data.PlaybackHistoryDisplay
import ru.karaoketv.data.QueueItemDisplay
import ru.karaoketv.data.SongEntity
import ru.karaoketv.data.Source
import ru.karaoketv.source.PlaybackResolution
import ru.karaoketv.ui.ActiveSong
import ru.karaoketv.ui.KaraokeViewModel
import java.net.HttpURLConnection
import java.net.URL

private val Background = Color(0xFF080A1D)
private val Surface = Color(0xFF11142A)
private val SurfaceStrong = Color(0xFF171C37)
private val Purple = Color(0xFFA53BFF)
private val Pink = Color(0xFFFF3EAE)
private val Cyan = Color(0xFF29D9FF)
private val TextPrimary = Color(0xFFF6F1FF)
private val TextMuted = Color(0xFFB7B1CD)
private val Positive = Color(0xFF60E780)
private val Warning = Color(0xFFFFBF62)
private val AppShape = RoundedCornerShape(22.dp)
private val SmallShape = RoundedCornerShape(14.dp)

private enum class Screen(val label: String, val glyph: String) {
    HOME("Главная", "⌂"), SEARCH("Поиск", "⌕"), QUEUE("Очередь", "☷"),
    FAVORITES("Избранное", "♡"), HISTORY("История", "◷"), SETTINGS("Настройки", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaraokeTvApp() }
        if (savedInstanceState == null) Updates.checkAtStartup(this)
    }
}

@Composable
private fun isTvDevice(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun KaraokeTvApp(model: KaraokeViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val queue by model.queue.collectAsStateWithLifecycle()
    val playback by model.playback.collectAsStateWithLifecycle()
    val activeSong by model.activeSong.collectAsStateWithLifecycle()
    val remoteAddress by model.remoteAddress.collectAsStateWithLifecycle()
    val adminAddress by model.adminAddress.collectAsStateWithLifecycle()
    val remoteError by model.remoteError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTv = isTvDevice()
    val playerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getLongExtra(PlayerActivity.EXTRA_HISTORY_ID, -1L)?.takeIf { it > 0 && result.data?.getBooleanExtra(PlayerActivity.EXTRA_COMPLETED, false) == true }?.let { historyId ->
            model.completePlayback(historyId, result.data?.getIntExtra(PlayerActivity.EXTRA_QUALITY_SCORE, -1)?.takeIf { it >= 0 })
        }
        model.clearActivePlayback()
    }
    LaunchedEffect(playback) {
        playback?.let { resolution ->
            when (resolution) {
                is PlaybackResolution.Embedded -> playerLauncher.launch(
                    Intent(context, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_URL, resolution.url)
                        .putExtra(PlayerActivity.EXTRA_LRC, resolution.lyricsLrc)
                        .putExtra(PlayerActivity.EXTRA_HISTORY_ID, resolution.historyId ?: -1L)
                        .putExtra(PlayerActivity.EXTRA_TITLE, activeSong?.title ?: "Караоке")
                )
                is PlaybackResolution.External -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolution.url)))
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(context, "Установите браузер или приложение источника", Toast.LENGTH_LONG).show()
                    }
                    model.clearActivePlayback()
                }
                is PlaybackResolution.Unsupported -> {
                    Toast.makeText(context, resolution.reason, Toast.LENGTH_LONG).show(); model.clearActivePlayback()
                }
                PlaybackResolution.Unknown -> {
                    Toast.makeText(context, "Источник пока не предоставил способ воспроизведения", Toast.LENGTH_LONG).show(); model.clearActivePlayback()
                }
            }
            model.clearPlayback()
        }
    }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Pink, background = Background, surface = Surface, onPrimary = Color.White, onBackground = TextPrimary, onSurface = TextPrimary)) {
        if (isTv) {
            Row(Modifier.fillMaxSize().background(Background)) {
                TvSideBar(screen, queue.size) { screen = it }
                Column(Modifier.fillMaxHeight().weight(1f).padding(horizontal = 30.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScreenHost(screen, model, { screen = it }, remoteAddress, adminAddress, remoteError, Modifier.weight(1f))
                    activeSong?.let { MiniPlayer(it, { screen = Screen.QUEUE }) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding().imePadding()) {
                ScreenHost(screen, model, { screen = it }, remoteAddress, adminAddress, remoteError, Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp))
                activeSong?.let { MiniPlayer(it, { screen = Screen.QUEUE }, Modifier.padding(horizontal = 12.dp)) }
                PhoneBottomNavigation(screen, queue.size) { screen = it }
            }
        }
    }
}

@Composable
private fun ScreenHost(screen: Screen, model: KaraokeViewModel, onNavigate: (Screen) -> Unit, remoteAddress: String?, adminAddress: String?, remoteError: String?, modifier: Modifier) {
    when (screen) {
        Screen.HOME -> HomeScreen(model, onNavigate, modifier)
        Screen.SEARCH -> SearchScreen(model, modifier)
        Screen.QUEUE -> QueueScreen(model, modifier)
        Screen.FAVORITES -> FavoritesScreen(model, modifier)
        Screen.HISTORY -> HistoryScreen(model, modifier)
        Screen.SETTINGS -> SettingsScreen(model, remoteAddress, adminAddress, remoteError, modifier, { onNavigate(Screen.HISTORY) })
    }
}

@Composable
private fun TvSideBar(current: Screen, queueSize: Int, onSelect: (Screen) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(230.dp).background(Brush.verticalGradient(listOf(Color(0xFF11122D), Color(0xFF0B0D22)))).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Purple, Pink))).border(1.dp, Color(0xFFFFB5E1), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("♬",  fontSize = 30.sp, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column { Text("Karaoke TV", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Ваш вечер музыки", color = TextMuted, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(20.dp))
        Screen.entries.forEach { item -> TvNavItem(item, current == item, if (item == Screen.QUEUE && queueSize > 0) queueSize else null) { onSelect(item) } }
        Spacer(Modifier.height(16.dp))
        Text("Источники и диагностика", color = TextMuted, fontSize = 12.sp)
        Text("доступны в настройках", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TvNavItem(item: Screen, selected: Boolean, badge: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(shape).background(if (selected) Brush.horizontalGradient(listOf(Purple, Color(0xFF6025BF))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))).border(if (focused) BorderStroke(3.dp, Cyan) else if (selected) BorderStroke(1.dp, Color(0xFFD99AFF)) else BorderStroke(0.dp, Color.Transparent), shape).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.glyph, fontSize = 25.sp, color = if (selected) Color.White else TextMuted); Spacer(Modifier.width(16.dp))
        Text(item.label, color = if (selected) Color.White else TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); badge?.let { CountBadge(it) }
    }
}

@Composable
private fun PhoneBottomNavigation(current: Screen, queueSize: Int, onSelect: (Screen) -> Unit) {
    val items = listOf(Screen.HOME, Screen.SEARCH, Screen.QUEUE, Screen.FAVORITES, Screen.SETTINGS)
    Row(Modifier.fillMaxWidth().background(Color(0xFF10132B)).padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { item ->
            Column(Modifier.weight(1f).clip(SmallShape).background(if (current == item) Color(0xFF30205E) else Color.Transparent).clickable { onSelect(item) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.TopEnd) { Text(item.glyph, fontSize = 22.sp, color = if (current == item) Color(0xFFE8C5FF) else TextMuted); if (item == Screen.QUEUE && queueSize > 0) CountBadge(queueSize) }
                Text(item.label, fontSize = 10.sp, color = if (current == item) Color.White else TextMuted, maxLines = 1)
            }
        }
    }
}

@Composable private fun CountBadge(count: Int) = Box(Modifier.clip(RoundedCornerShape(50)).background(Purple).padding(horizontal = 7.dp, vertical = 2.dp), contentAlignment = Alignment.Center) { Text(count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

@Composable
private fun HomeScreen(model: KaraokeViewModel, onNavigate: (Screen) -> Unit, modifier: Modifier) {
    val query by model.searchQuery.collectAsStateWithLifecycle()
    val results by model.remoteResults.collectAsStateWithLifecycle()
    val localSongs by model.songs.collectAsStateWithLifecycle()
    val isTv = isTvDevice()
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { HeroPanel(query, model::setQuery, { onNavigate(Screen.SEARCH); if (query.isNotBlank()) model.searchAllSources() }, isTv) }
        item { SourceRow() }
        if (results.isNotEmpty()) item {
            SectionTitle("Результаты поиска", "Реальные результаты подключённых источников")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 10.dp)) { items(results, key = { it.id }) { version -> SongPosterCard(version, { model.playVersion(version) }, { model.addVersionToQueue(version, "Гость") }) } }
        } else item { EmptyCatalogCard("Каталог пока пуст", "Найдите исполнителя или название. Результаты появятся здесь только после ответа подключённого источника.", "Открыть поиск") { onNavigate(Screen.SEARCH) } }
        if (localSongs.isNotEmpty()) item {
            SectionTitle("Локальная библиотека", "Сохранённые песни на этом устройстве")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 10.dp)) { items(localSongs, key = { it.id }) { song -> LocalSongCard(song) { model.addSongToQueue(song, "Гость") } } }
        }
        item { SectionTitle("Категории", "Выберите настроение вечера"); CategoryGrid { queryText -> model.setQuery(queryText); onNavigate(Screen.SEARCH); model.searchAllSources() } }
    }
}

@Composable
private fun HeroPanel(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, isTv: Boolean) {
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF21164B), Color(0xFF151B4D), Color(0xFF4E185C)))).border(1.dp, Color(0xFF8F47D4), shape).padding(if (isTv) 32.dp else 22.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Караоке для всей семьи", color = Color.White, fontSize = if (isTv) 39.sp else 28.sp, fontWeight = FontWeight.Black)
            Text("Ищите караоке-версии, минусовки и видео с текстом", color = Color(0xFFE0D8FA), fontSize = if (isTv) 19.sp else 15.sp)
            NeonSearchField(query, onQueryChange, onSearch)
        }
    }
}

@Composable
private fun NeonSearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xDD0B102A)).border(2.dp, Purple, RoundedCornerShape(20.dp)).padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = Color.White, fontSize = 31.sp); Spacer(Modifier.width(12.dp))
        BasicTextField(value, onValueChange, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }), textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White), decorationBox = { inner -> if (value.isBlank()) Text("Песня или исполнитель…", color = TextMuted, fontSize = 17.sp, maxLines = 1); inner() })
        Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)) { Text("Искать") }
    }
}

@Composable
private fun SourceRow() = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Источники поиска", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SourceChip("YouTube", "▶", BuildConfig.YOUTUBE_API_KEY.isNotBlank()) }; item { SourceChip("VK Видео", "VK", BuildConfig.VK_API_TOKEN.isNotBlank()) }; item { SourceChip("RUTUBE", "R", BuildConfig.RUTUBE_SEARCH_ENDPOINT.isNotBlank()) }; item { SourceChip("Локальные", "▣", true) }
    }
}

@Composable
private fun SourceChip(name: String, mark: String, configured: Boolean) = Row(Modifier.clip(SmallShape).background(SurfaceStrong).border(1.dp, if (configured) Color(0xFF326B85) else Color(0xFF3E415B), SmallShape).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(mark, color = Cyan, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Text(if (name == "Локальные") "на устройстве" else if (configured) "ключ указан" else "нужна настройка", color = TextMuted, fontSize = 11.sp)
}

@Composable private fun SectionTitle(title: String, subtitle: String) = Column { Text(title, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 13.sp) }

@Composable
private fun CategoryGrid(onCategory: (String) -> Unit) {
    val categories = listOf("🎙" to "Русский поп", "🎸" to "Рок", "◉" to "Дискотека 90-х", "▥" to "Хиты 2000-х", "☆" to "Современные хиты", "♧" to "Детские песни", "♡" to "Любовные хиты", "▣" to "Из кино")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 8.dp)) { items(categories, key = { it.second }) { (icon, name) -> Card(onClick = { onCategory("$name караоке") }, modifier = Modifier.width(142.dp).height(94.dp), shape = SmallShape, colors = CardDefaults.cardColors(containerColor = SurfaceStrong), border = BorderStroke(1.dp, Color(0xFF8137B7))) { Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(icon, fontSize = 24.sp); Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2) } } } }
}

@Composable
private fun SearchScreen(model: KaraokeViewModel, modifier: Modifier) {
    val searching by model.searching.collectAsStateWithLifecycle()
    val query by model.searchQuery.collectAsStateWithLifecycle(); val results by model.remoteResults.collectAsStateWithLifecycle(); val localSongs by model.songs.collectAsStateWithLifecycle(); val error by model.remoteError.collectAsStateWithLifecycle(); var participant by remember { mutableStateOf("Гость") }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { Text("Поиск караоке", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Технические причины недоступности источников показаны только здесь и в настройках.", color = TextMuted, fontSize = 13.sp) }
        item { NeonSearchField(query, model::setQuery, model::searchAllSources) }
        item { BasicTextField(participant, { participant = it }, singleLine = true, modifier = Modifier.fillMaxWidth().clip(SmallShape).background(Surface).border(1.dp, Color(0xFF373B66), SmallShape).padding(16.dp), textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary), decorationBox = { inner -> if (participant.isBlank()) Text("Имя участника", color = TextMuted); inner() }) }
        error?.let { message -> item { DiagnosticCard(message) } }
        if (searching) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Purple)
                Text("Ищем караоке…", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
            }
        } else if (results.isNotEmpty()) {
            item { SectionTitle("Найдено", "Нажмите «Петь» или добавьте версию в очередь") }
            items(results, key = { it.id }) { version -> SearchResultCard(version, participant, { model.playVersion(version) }, { model.addVersionToQueue(version, participant) }) }
        } else item { EmptyCatalogCard(if (query.isBlank()) "Введите запрос" else "Нет подтверждённых результатов", if (query.isBlank()) "Поиск запускается по названию песни или исполнителю." else "Проверьте настройки источников либо попробуйте другой запрос. Приложение не подставляет вымышленные результаты.", null) {} }
        if (localSongs.isNotEmpty()) { item { SectionTitle("Локальные песни", "Сохранены в базе этого устройства") }; items(localSongs, key = { "local-${it.id}" }) { song -> LocalSongListCard(song, participant) { model.addSongToQueue(song, participant) } } }
    }
}

@Composable
private fun SongPosterCard(version: KaraokeVersion, onPlay: () -> Unit, onAdd: () -> Unit) = Card(onClick = onPlay, modifier = Modifier.width(250.dp), shape = AppShape, colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Color(0xFF41457A))) {
    Column { CoverArt(version.thumbnailUrl, version.title, Modifier.fillMaxWidth().height(138.dp)); Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(version.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${sourceLabel(version.source)} · ${version.durationSeconds?.let(::formatDuration) ?: "длительность неизвестна"}", color = TextMuted, fontSize = 12.sp); Text("Караоке: ${version.karaokeConfidence}%", color = Cyan, fontSize = 12.sp); OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Purple)) { Text("В очередь") } } }
}

@Composable
private fun SearchResultCard(version: KaraokeVersion, participant: String, onPlay: () -> Unit, onAdd: () -> Unit) = Card(modifier = Modifier.fillMaxWidth(), shape = AppShape, colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Color(0xFF353965))) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(version.thumbnailUrl, version.title, Modifier.size(94.dp).clip(SmallShape)); Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(version.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${sourceLabel(version.source)} · ${version.durationSeconds?.let(::formatDuration) ?: "длительность неизвестна"}", color = TextMuted, fontSize = 13.sp); Text("Караоке-confidence ${version.karaokeConfidence}%", color = Cyan, fontSize = 12.sp); if (participant.isNotBlank()) Text("Исполнитель: $participant", color = TextMuted, fontSize = 12.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Purple), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Петь") }; OutlinedButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+ Очередь", fontSize = 11.sp) } }
    }
}

@Composable
private fun LocalSongCard(song: SongEntity, onAdd: () -> Unit) = Card(onClick = onAdd, modifier = Modifier.width(220.dp), shape = AppShape, colors = CardDefaults.cardColors(containerColor = SurfaceStrong), border = BorderStroke(1.dp, Color(0xFF41457A))) {
    Column { CoverArt(null, song.title, Modifier.fillMaxWidth().height(126.dp)); Column(Modifier.padding(14.dp)) { Text(song.artist, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.title, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(8.dp)); Text("Добавить в очередь", color = Cyan, fontSize = 13.sp) } }
}

@Composable
private fun LocalSongListCard(song: SongEntity, participant: String, onAdd: () -> Unit) = Card(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = SmallShape, colors = CardDefaults.cardColors(containerColor = Surface)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${song.artist} — ${song.title}", color = TextPrimary, fontWeight = FontWeight.SemiBold); Text("Локальная библиотека · $participant", color = TextMuted, fontSize = 12.sp) }; Text("+ Очередь", color = Cyan, fontSize = 13.sp) }
}

@Composable
private fun QueueScreen(model: KaraokeViewModel, modifier: Modifier) {
    val queue by model.queue.collectAsStateWithLifecycle(); val favoriteIds by model.favoriteSongIds.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Моя очередь", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("${queue.size} ${pluralSongs(queue.size)}", color = TextMuted, fontSize = 13.sp) }; if (queue.isNotEmpty()) OutlinedButton(onClick = model::clearQueue, border = BorderStroke(1.dp, Color(0xFF804A77))) { Text("Очистить") } }
        if (queue.isEmpty()) EmptyCatalogCard("Очередь пуста", "Добавьте песню из поиска или локальной библиотеки. Здесь не отображаются демонстрационные треки.", null) {} else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) { items(queue, key = { it.id }) { item -> QueueCard(item, item.songId in favoriteIds, model) } }
    }
}

@Composable
private fun QueueCard(item: QueueItemDisplay, favorite: Boolean, model: KaraokeViewModel) = Card(modifier = Modifier.fillMaxWidth(), shape = AppShape, colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Color(0xFF383B67))) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple, Pink))), contentAlignment = Alignment.Center) { Text((item.position + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${item.participant} · ${sourceLabel(item.source)}", color = TextMuted, fontSize = 13.sp) }; Text(if (favorite) "♥" else "♡", color = if (favorite) Pink else TextMuted, fontSize = 28.sp, modifier = Modifier.clickable { model.toggleFavorite(item.songId) }) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { model.playQueueItem(item) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Петь") }; OutlinedButton(onClick = { model.moveQueueItem(item.id, -1) }) { Text("↑") }; OutlinedButton(onClick = { model.moveQueueItem(item.id, 1) }) { Text("↓") }; OutlinedButton(onClick = { model.repeatQueueItem(item.id) }) { Text("Повтор") }; OutlinedButton(onClick = { model.removeFromQueue(item.id) }, border = BorderStroke(1.dp, Color(0xFF82455B))) { Text("Удалить") } }
        item.ratingStars?.let { Text("Личная оценка: $it/5", color = Warning, fontSize = 13.sp) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { stars ->
                OutlinedButton(onClick = { model.rateVersion(item.versionId, stars) }) {
                    Text("$stars ★", color = if (item.ratingStars == stars) Warning else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(model: KaraokeViewModel, modifier: Modifier) {
    val favorites by model.favorites.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Избранное", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Песни, отмеченные в очереди", color = TextMuted, fontSize = 13.sp)
        }
        if (favorites.isEmpty()) item {
            EmptyCatalogCard("Пока нет избранного", "Нажмите сердечко у песни в очереди, чтобы сохранить её здесь.", null) {}
        } else items(favorites, key = { it.id }) { song ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LocalSongListCard(song, "Гость") { model.addSongToQueue(song, "Гость") }
                OutlinedButton(onClick = { model.toggleFavorite(song.id) }) { Text("Удалить из избранного") }
            }
        }
    }
}

@Composable
private fun HistoryScreen(model: KaraokeViewModel, modifier: Modifier) {
    val history by model.history.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("История выступлений", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Завершённые и прерванные выступления", color = TextMuted, fontSize = 13.sp) }; if (history.isEmpty()) item { EmptyCatalogCard("Выступлений ещё не было", "После запуска песни здесь появится история с техническим баллом микрофона, если он доступен.", null) {} }; items(history, key = { it.id }) { HistoryCard(it) } }
}

@Composable
private fun HistoryCard(entry: PlaybackHistoryDisplay) = Card(modifier = Modifier.fillMaxWidth(), shape = SmallShape, colors = CardDefaults.cardColors(containerColor = Surface)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (entry.completed) "✓" else "…", color = if (entry.completed) Positive else Warning, fontSize = 24.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(entry.title, color = TextPrimary, fontWeight = FontWeight.Bold); Text("${entry.participant} · ${if (entry.completed) "завершено" else "не завершено"}", color = TextMuted, fontSize = 13.sp) }; entry.qualityScore?.let { Text("$it/100", color = Cyan, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun SettingsScreen(model: KaraokeViewModel, remoteAddress: String?, adminAddress: String?, remoteError: String?, modifier: Modifier, onHistory: () -> Unit) = LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item {
        val context = LocalContext.current
        OutlinedButton(onClick = { context.startActivity(Intent(context, UpdateActivity::class.java)) }) { Text("Проверить обновления · ${BuildConfig.VERSION_NAME}") }
    }
    item { OutlinedButton(onClick = onHistory) { Text("История выступлений") } }
    item { Text("Настройки и диагностика", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Состояние подключений вынесено из главного экрана.", color = TextMuted, fontSize = 13.sp) }
    item { SettingsPanel("Поисковые источники") { DiagnosticSource("YouTube", BuildConfig.YOUTUBE_API_KEY.isNotBlank(), if (BuildConfig.YOUTUBE_API_KEY.isBlank()) "Нужен API key" else "Ключ указан локально"); DiagnosticSource("VK Видео", BuildConfig.VK_API_TOKEN.isNotBlank(), if (BuildConfig.VK_API_TOKEN.isBlank()) "Нужен официальный token" else "Токен указан локально"); DiagnosticSource("RUTUBE", BuildConfig.RUTUBE_SEARCH_ENDPOINT.isNotBlank(), if (BuildConfig.RUTUBE_SEARCH_ENDPOINT.isBlank()) "Нужен одобренный endpoint" else "Endpoint указан локально") } }
    item { SettingsPanel("Управление с телефона") { Button(onClick = model::startRemoteControl, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Подключить телефон") }; remoteAddress?.let { Text("Гостевой доступ: $it", color = Positive, fontSize = 13.sp) }; adminAddress?.let { Text("Администратор: $it", color = Warning, fontSize = 13.sp) }; remoteError?.let { Text(it, color = Color(0xFFFF9AA7), fontSize = 13.sp) } } }
    item { SettingsPanel("Аудио") { Text("Оценка сигнала доступна только после разрешения микрофона и не является оценкой вокального мастерства.", color = TextMuted, fontSize = 14.sp) } }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) = Card(shape = AppShape, colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Color(0xFF3A3F70))) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold); content() } }

@Composable
private fun DiagnosticSource(name: String, configured: Boolean, details: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold); Text(details, color = TextMuted, fontSize = 12.sp) }; Text(if (configured) "Настроен" else "Не настроен", color = if (configured) Positive else Warning, fontSize = 13.sp) }

@Composable
private fun MiniPlayer(active: ActiveSong, onOpenQueue: () -> Unit, modifier: Modifier = Modifier) = Card(onClick = onOpenQueue, modifier = modifier.fillMaxWidth(), shape = AppShape, colors = CardDefaults.cardColors(containerColor = Color(0xFF131634)), border = BorderStroke(1.dp, Purple)) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { CoverArt(active.thumbnailUrl, active.title, Modifier.size(52.dp).clip(SmallShape)); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(active.title, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Сейчас воспроизводится · ${sourceLabel(active.source)}", color = TextMuted, fontSize = 12.sp) }; Text("Ⅱ", color = Color.White, fontSize = 28.sp) }
}

@Composable
private fun EmptyCatalogCard(title: String, body: String, action: String?, onAction: () -> Unit) = Card(modifier = Modifier.fillMaxWidth(), shape = AppShape, colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Color(0xFF373C68))) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("♫", color = Purple, fontSize = 44.sp); Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(body, color = TextMuted, fontSize = 14.sp); action?.let { Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(it) } } }
}

@Composable
private fun DiagnosticCard(message: String) = Card(shape = SmallShape, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1B2A)), border = BorderStroke(1.dp, Color(0xFF88445E))) { Text("Диагностика источников: $message", Modifier.padding(14.dp), color = Color(0xFFFFB2BE), fontSize = 13.sp) }

@Composable
private fun CoverArt(thumbnailUrl: String?, title: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, thumbnailUrl) {
        value = thumbnailUrl?.takeIf { Uri.parse(it).scheme == "https" }?.let { imageUrl -> withContext(Dispatchers.IO) { runCatching { (URL(imageUrl).openConnection() as HttpURLConnection).run { connectTimeout = 4_000; readTimeout = 4_000; instanceFollowRedirects = false; inputStream.use(BitmapFactory::decodeStream) } }.getOrNull() } }
    }
    Box(modifier.clip(SmallShape).background(Brush.linearGradient(listOf(Color(0xFF382165), Color(0xFF154C72), Color(0xFF762453)))), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Обложка: $title", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("♫", color = Color.White, fontSize = 30.sp); Text("Нет обложки", color = Color(0xFFE2D8F7), fontSize = 10.sp) }
    }
}

private fun sourceLabel(source: Source): String = when (source) { Source.YOUTUBE -> "YouTube"; Source.VK_VIDEO -> "VK Видео"; Source.RUTUBE -> "RUTUBE" }
private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
private fun pluralSongs(count: Int): String = when { count % 10 == 1 && count % 100 != 11 -> "песня"; count % 10 in 2..4 && count % 100 !in 12..14 -> "песни"; else -> "песен" }
