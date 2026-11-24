package app.mari.reader.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Translate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mari.reader.ReaderViewModel
import app.mari.reader.data.Article
import app.mari.reader.data.Block
import app.mari.reader.data.ReaderTheme
import app.mari.reader.data.Settings
import app.mari.reader.data.Status
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

private data class Palette(val background: Color, val text: Color, val muted: Color, val accent: Color, val mark: Color)

private fun palette(theme: ReaderTheme) = when (theme) {
    ReaderTheme.DARK -> Palette(Ink, SoftWhite, Muted, Accent, Color(0x33E8B98A))
    ReaderTheme.SEPIA -> Palette(Color(0xFFF4ECDD), Color(0xFF3B3024), Color(0xFF7D6E5C), Color(0xFF9A5B2A), Color(0x339A5B2A))
    ReaderTheme.LIGHT -> Palette(Color(0xFFFFFFFF), Color(0xFF1C1B1A), Color(0xFF6E6A64), Color(0xFF9A5B2A), Color(0x22E8B98A))
}

private val speeds = listOf(.8f, 1f, 1.2f, 1.5f, 1.8f, 2f)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable fun ReaderScreen(model: ReaderViewModel, article: Article, back: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val voice by model.voice.collectAsStateWithLifecycle()
    val original = remember(article.id, article.words) { model.blocks(article.id) }
    val tr by model.translation.collectAsStateWithLifecycle()
    val mine = tr.articleId == article.id
    val lines = tr.lines?.takeIf { mine && tr.on && it.size == original.size }
    // With the translation on, the same blocks carry the translated text; highlights stay with the original.
    val blocks = remember(original, lines) { if (lines == null) original else original.mapIndexed { i, b -> if (lines[i].isNotBlank()) b.copy(text = lines[i]) else b } }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val p = palette(settings.theme)
    // Items before the blocks: the header. Keeps scroll positions and block indexes apart.
    val headerItems = 1
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (article.position > 0) article.position + headerItems else 0)
    var textSheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listening = voice.articleId == article.id
    var marksOpen by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<app.mari.reader.data.Highlight?>(null) }
    val jump by model.jump.collectAsStateWithLifecycle()
    LaunchedEffect(jump, blocks.size) { jump?.let { b -> if (b in blocks.indices) list.scrollToItem(b + headerItems, -120); model.jumped() } }
    if (marksOpen) ModalBottomSheet(onDismissRequest = { marksOpen = false }, containerColor = Panel) {
        HighlightList(article) { h -> marksOpen = false; model.jumpTo(article.id, h.block) }
    }
    tapped?.let { h ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { tapped = null }, title = { Text("Выделение") },
            text = { Text("«${h.text}»", fontStyle = FontStyle.Italic) },
            confirmButton = { TextButton(onClick = {
                tapped = null
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "«${h.text}»\n— ${article.title}\n${article.url}"), "Поделиться цитатой")) }
            }) { Text("Поделиться") } },
            dismissButton = { TextButton(onClick = { tapped = null; model.unmark(article, h) }) { Text("Убрать выделение") } })
    }

    val share = if (blocks.isEmpty()) 0f else {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last >= blocks.size + headerItems) 1f else ((list.firstVisibleItemIndex - headerItems).coerceAtLeast(0).toFloat() / blocks.size).coerceIn(0f, 1f)
    }
    LaunchedEffect(article.id) {
        snapshotFlow { list.firstVisibleItemIndex to (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }.distinctUntilChanged().debounce(800).collect { (first, last) ->
            val position = (first - headerItems).coerceAtLeast(0)
            val done = last >= blocks.size + headerItems
            model.progress(article.id, position, if (done) 1f else if (blocks.isEmpty()) 0f else position.toFloat() / blocks.size)
        }
    }
    // While reading aloud, the page follows the voice.
    LaunchedEffect(voice.index, listening) {
        if (listening && voice.index in blocks.indices) {
            val target = voice.index + headerItems
            val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
            if (target !in visible.drop(1).dropLast(1)) list.animateScrollToItem(target, -200)
        }
    }

    if (textSheet) ModalBottomSheet(onDismissRequest = { textSheet = false }, containerColor = Panel) { TextSettings(settings) { change -> model.settings(change) } }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = if (listening) 140.dp else 60.dp)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp).padding(horizontal = 22.dp)) {
                    Text(article.site, style = MaterialTheme.typography.labelLarge, color = p.accent)
                    Text(article.title, color = p.text, fontSize = (settings.fontSize + 9).sp, lineHeight = (settings.fontSize + 9).sp * 1.22f,
                        fontWeight = FontWeight.Bold, fontFamily = if (settings.serif) FontFamily.Serif else Manrope, modifier = Modifier.padding(top = 8.dp))
                    Text(listOfNotNull(article.author.takeIf { it.isNotBlank() }, "${article.minutes} мин чтения").joinToString(" · "),
                        color = p.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    if (article.image.isNotBlank()) AsyncImage(article.image, null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.padding(top = 18.dp).fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(12.dp))
                    when {
                        article.loading -> Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp).height(20.dp).width(20.dp))
                            Text("Загружаем текст статьи…", color = p.muted)
                        }
                        article.error.isNotEmpty() -> Column(Modifier.padding(top = 20.dp)) {
                            Text(article.error, color = p.muted)
                            Row {
                                TextButton(onClick = { model.retry(article.id) }) { Text("Повторить", color = p.accent) }
                                TextButton(onClick = { openInBrowser(context, article.url) }) { Text("Открыть в браузере", color = p.accent) }
                            }
                        }
                        blocks.isEmpty() -> Text("Текст статьи не сохранился. Открой её в браузере из меню «⋮».", color = p.muted, modifier = Modifier.padding(top = 20.dp))
                    }
                }
            }
            itemsIndexed(blocks) { i, b ->
                val current = listening && voice.index == i
                Box(Modifier.fillMaxWidth().background(if (current) p.mark else Color.Transparent).then(if (listening) Modifier.clickable { model.voiceJump(i) } else Modifier)
                    .padding(horizontal = 22.dp, vertical = 6.dp)) {
                    BlockView(b, settings, p, if (lines != null) emptyList() else article.highlights.filter { it.block == i }, markerColor(settings.theme),
                        onMark = { s, e -> if (lines != null) model.note(app.mari.reader.Note("Выделять можно в оригинале: меню ⋮ → «Показать оригинал»")) else model.mark(article, i, s, e) },
                        onTapMark = { tapped = it })
                }
            }
            if (blocks.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Конец статьи", color = p.muted, style = MaterialTheme.typography.labelLarge)
                    if (article.status == Status.UNREAD) Button(onClick = { model.archive(article); back() }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                        modifier = Modifier.padding(top = 12.dp)) { Text("Прочитано") }
                    TextButton(onClick = { openInBrowser(context, article.url) }) { Text("Открыть оригинал", color = p.accent) }
                }
            }
        }
        // Top bar over the text.
        Column(Modifier.fillMaxWidth().background(p.background.copy(alpha = .94f)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = p.text) }
                Spacer(Modifier.weight(1f))
                if (blocks.isNotEmpty()) IconButton(onClick = {
                    if (listening) model.voiceToggle() else {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        model.listen(article, blocks, (list.firstVisibleItemIndex - headerItems).coerceAtLeast(0))
                    }
                }) {
                    Icon(Icons.Rounded.Headphones, "Слушать", tint = if (listening) p.accent else p.text)
                }
                IconButton(onClick = { textSheet = true }) { Icon(Icons.Rounded.TextFields, "Оформление текста", tint = p.text) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = p.text) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (article.favorite) "Убрать из избранного" else "В избранное") }, onClick = { menu = false; model.favorite(article) })
                        DropdownMenuItem(text = { Text(if (article.status == Status.ARCHIVED) "Вернуть в «Читать»" else "Отметить прочитанным") }, onClick = { menu = false; model.archive(article) })
                        if (original.isNotEmpty()) DropdownMenuItem(text = { Text(if (lines != null) "Показать оригинал" else if (ArticleLang.isRussian(original)) "Перевести на английский" else "Перевести на русский") },
                            leadingIcon = { Icon(Icons.Rounded.Translate, null) },
                            onClick = { menu = false; if (lines != null) model.original() else model.translate(article) })
                        DropdownMenuItem(text = { Text(if (article.highlights.isEmpty()) "Выделения" else "Выделения (${article.highlights.size})") }, onClick = { menu = false; marksOpen = true })
                        DropdownMenuItem(text = { Text("Открыть в браузере") }, onClick = { menu = false; openInBrowser(context, article.url) })
                        DropdownMenuItem(text = { Text("Поделиться ссылкой") }, onClick = {
                            menu = false
                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}"), "Поделиться")) }
                        })
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; model.delete(article) })
                    }
                }
            }
            LinearProgressIndicator(progress = { share }, color = p.accent, trackColor = Color.Transparent, drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(2.dp))
            if (mine && (tr.loading || tr.error != null || lines != null)) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Translate, null, tint = p.accent, modifier = Modifier.padding(end = 8.dp).height(18.dp))
                Text(when {
                    tr.loading -> if (tr.total > 0) "Переводим… ${tr.done} из ${tr.total} абзацев" else "Переводим…"
                    tr.error != null -> tr.error!!
                    else -> if (tr.to == "en") "Перевод на английский" else "Перевод на русский"
                }, color = p.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (tr.error != null) TextButton(onClick = { model.translate(article) }) { Text("Ещё раз", color = p.accent) }
                else TextButton(onClick = model::original) { Text(if (tr.loading) "Отмена" else "Оригинал", color = p.accent) }
            }
        }
        if (listening) VoiceBar(voice.playing, settings.voiceRate, voice.error, voice.sleepAt, Modifier.align(Alignment.BottomCenter),
            toggle = model::voiceToggle, back = model::voiceBack, forward = model::voiceForward, close = model::voiceStop, speed = model::voiceSpeed, sleep = model::voiceSleep)
    }
}

@Composable private fun BlockView(b: Block, s: Settings, p: Palette, marks: List<app.mari.reader.data.Highlight>, marker: Int, onMark: (Int, Int) -> Unit, onTapMark: (app.mari.reader.data.Highlight) -> Unit) {
    val markable = @Composable { size: Float, italic: Boolean, bold: Boolean, m: Modifier ->
        MarkableText(b.text, marks, size, s.lineHeight, s.serif, italic, bold, p.text, marker, p.accent, m, onMark, onTapMark)
    }
    val family = if (s.serif) FontFamily.Serif else Manrope
    val body = TextStyle(fontFamily = family, fontSize = s.fontSize.sp, lineHeight = (s.fontSize * s.lineHeight).sp, color = p.text)
    when (b.type) {
        Block.HEADING -> markable(s.fontSize + 4f, false, true, Modifier.padding(top = 14.dp))
        Block.IMAGE -> Column(Modifier.padding(vertical = 8.dp)) {
            AsyncImage(b.url, b.text.ifBlank { null }, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(RoundedCornerShape(12.dp)))
            if (b.text.isNotBlank()) Text(b.text, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        }
        Block.ITEM -> Row {
            if (!b.text.first().isDigit()) Text("•", style = body, color = p.accent, modifier = Modifier.width(18.dp))
            markable(s.fontSize.toFloat(), false, false, Modifier)
        }
        Block.QUOTE -> Row(Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(p.accent))
            markable(s.fontSize.toFloat(), true, false, Modifier.padding(start = 14.dp))
        }
        Block.CODE -> Text(b.text, style = body.copy(fontFamily = FontFamily.Monospace, fontSize = (s.fontSize - 4).sp, lineHeight = (s.fontSize - 4).sp * 1.4f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.mark).padding(12.dp))
        else -> markable(s.fontSize.toFloat(), false, false, Modifier)
    }
}

@Composable private fun VoiceBar(playing: Boolean, rate: Float, error: String?, sleepAt: Long, modifier: Modifier, toggle: () -> Unit, back: () -> Unit, forward: () -> Unit,
                                  close: () -> Unit, speed: (Float) -> Unit, sleep: (Int) -> Unit) {
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    // Minutes left on the sleep timer, refreshed every few seconds.
    val left by produceState(0, sleepAt) { while (true) { value = if (sleepAt == 0L) 0 else ((sleepAt - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(0); delay(5_000) } }
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Elevated).padding(horizontal = 8.dp, vertical = 6.dp)) {
        if (error != null) Text(error, color = Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Остановить чтение") }
            Box {
                TextButton(onClick = { sleepMenu = true }) {
                    Icon(Icons.Rounded.Bedtime, "Таймер сна", tint = if (sleepAt > 0) Accent else SoftWhite)
                    if (sleepAt > 0) Text(" $left мин", color = Accent, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(sleepMenu, { sleepMenu = false }) {
                    Text("Остановить через", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    listOf(10, 20, 30, 45, 60).forEach { m -> DropdownMenuItem(text = { Text("$m мин") }, onClick = { sleepMenu = false; sleep(m) }) }
                    if (sleepAt > 0) DropdownMenuItem(text = { Text("Выключить таймер") }, onClick = { sleepMenu = false; sleep(0) })
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = back) { Icon(Icons.Rounded.FastRewind, "Предыдущий абзац") }
            Box(Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable(onClick = toggle).padding(12.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Пауза" else "Читать", tint = Ink)
            }
            IconButton(onClick = forward) { Icon(Icons.Rounded.FastForward, "Следующий абзац") }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { speedMenu = true }) { Text(speedText(rate)) }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    speeds.forEach { v -> DropdownMenuItem(text = { Text(speedText(v)) }, onClick = { speedMenu = false; speed(v) }) }
                }
            }
        }
    }
}

private object ArticleLang { fun isRussian(blocks: List<Block>) = app.mari.reader.data.ArticleTranslator.target(blocks) == "en" }

private fun speedText(v: Float) = (if (v % 1f == 0f) "%.0f".format(java.util.Locale.US, v) else v.toString()).replace('.', ',') + "×"

@Composable private fun TextSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Оформление", style = MaterialTheme.typography.headlineSmall)
        Text("Размер текста: ${s.fontSize}", style = MaterialTheme.typography.titleSmall)
        Slider(s.fontSize.toFloat(), { v -> change { it.copy(fontSize = v.toInt()) } }, valueRange = 14f..30f, steps = 15,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line))
        Text("Шрифт", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.serif, { change { it.copy(serif = true) } }, label = { Text("С засечками", fontFamily = FontFamily.Serif) }, colors = chipColors())
            FilterChip(!s.serif, { change { it.copy(serif = false) } }, label = { Text("Без засечек") }, colors = chipColors())
        }
        Text("Межстрочный интервал", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.4f to "Плотно", 1.6f to "Обычно", 1.85f to "Свободно").forEach { (v, label) ->
                FilterChip(kotlin.math.abs(s.lineHeight - v) < .05f, { change { it.copy(lineHeight = v) } }, label = { Text(label) }, colors = chipColors())
            }
        }
        Text("Тема", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { t -> FilterChip(s.theme == t, { change { it.copy(theme = t) } }, label = { Text(t.label) }, colors = chipColors()) }
        }
        Text("Пример текста. Так будет выглядеть статья.", fontFamily = if (s.serif) FontFamily.Serif else Manrope, fontSize = s.fontSize.sp, lineHeight = s.lineHeight.em,
            color = palette(s.theme).text, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette(s.theme).background).padding(16.dp))
    }
}

@Composable private fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink)
