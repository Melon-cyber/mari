package app.mari.reader.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mari.reader.ReaderViewModel
import app.mari.reader.data.Article
import app.mari.reader.data.Status
import coil.compose.AsyncImage

@Composable fun MariRoot(model: ReaderViewModel) {
    val open by model.open.collectAsStateWithLifecycle()
    val note by model.note.collectAsStateWithLifecycle()
    val articles by model.articles.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(n.text, actionLabel = if (n.undo != null) "Вернуть" else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) n.undo?.invoke()
        model.note(null)
    }
    val article = articles.firstOrNull { it.id == open }
    BackHandler(enabled = open != null) { model.open(null) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (article != null) ReaderScreen(model, article, back = { model.open(null) })
        else LibraryScreen(model, snackbar)
        if (article != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

private enum class Shelf(val label: String) { UNREAD("Читать"), FAVORITE("Избранное"), ARCHIVE("Прочитано"), QUOTES("Цитаты") }

fun openInBrowser(context: Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

@Composable private fun LibraryScreen(model: ReaderViewModel, snackbar: SnackbarHostState) {
    val articles by model.articles.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.UNREAD) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    if (adding) AddDialog(close = { adding = false }) { adding = false; model.add(it) }
    BackHandler(enabled = searching) { searching = false; query = ""; model.clearSearch() }
    if (searching) { SearchScreen(model, close = { searching = false; model.clearSearch() }); return }

    val shown = articles.filter {
        when (shelf) { Shelf.UNREAD -> it.status == Status.UNREAD; Shelf.FAVORITE -> it.favorite; Shelf.ARCHIVE -> it.status == Status.ARCHIVED; Shelf.QUOTES -> false }
    }.filter { query.isBlank() || it.title.contains(query, true) || it.site.contains(query, true) || it.excerpt.contains(query, true) }
        .sortedByDescending { it.saved }

    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, containerColor = Accent, contentColor = Ink, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ссылка") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Мари", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Поиск") }
                }
            }
            if (articles.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Shelf.entries.forEach { s ->
                        val count = if (s == Shelf.QUOTES) articles.sumOf { it.highlights.size } else articles.count { when (s) { Shelf.UNREAD -> it.status == Status.UNREAD; Shelf.FAVORITE -> it.favorite; Shelf.ARCHIVE -> it.status == Status.ARCHIVED; Shelf.QUOTES -> false } }
                        FilterChip(shelf == s, { shelf = s }, label = { Text(if (count > 0) "${s.label} $count" else s.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink))
                    }
                }
            }
            if (articles.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoStories, null, tint = Accent, modifier = Modifier.size(32.dp))
                    Text("Сохраняй, чтобы прочитать потом", style = MaterialTheme.typography.titleLarge)
                    Text("В браузере или в Telegram нажми «Поделиться» → «Мари». Статья сохранится без рекламы и лишнего, её можно читать без интернета или слушать голосом.\n\nЕщё можно найти статью прямо здесь через поиск вверху или нажать «Ссылка» внизу и вставить адрес.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, null); Text("  Найти статью") }
                }
            } else if (shelf == Shelf.QUOTES) {
                val quotes = articles.flatMap { a -> a.highlights.map { a to it } }.sortedByDescending { it.second.created }
                    .filter { (a, h) -> query.isBlank() || h.text.contains(query, true) || a.title.contains(query, true) }
                if (quotes.isEmpty()) item { Text("Выдели слова в статье долгим нажатием и выбери «Выделить» — цитаты соберутся здесь.", color = Muted, modifier = Modifier.padding(20.dp)) }
                items(quotes, key = { "h" + it.second.id }) { (a, h) ->
                    Column(Modifier.fillMaxWidth().clickable { model.jumpTo(a.id, h.block) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text("«${h.text}»", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        Text(a.title, style = MaterialTheme.typography.labelMedium, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                    HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
                }
            } else if (shown.isEmpty()) item {
                Text(when { query.isNotBlank() -> "Ничего не нашлось."; shelf == Shelf.UNREAD -> "Всё прочитано 🎉"; shelf == Shelf.FAVORITE -> "Отмечай звёздочкой то, к чему захочешь вернуться."; else -> "Прочитанные статьи будут здесь." },
                    color = Muted, modifier = Modifier.padding(20.dp))
            }
            items(shown, key = { it.id }) { a ->
                ArticleRow(a, open = { if (a.error.isNotEmpty()) model.retry(a.id) else if (!a.loading) model.open(a.id) },
                    favorite = { model.favorite(a) }, archive = { model.archive(a) }, delete = { model.delete(a) }, retry = { model.retry(a.id) })
                HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable private fun ArticleRow(a: Article, open: () -> Unit, favorite: () -> Unit, archive: () -> Unit, delete: () -> Unit, retry: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.favorite) Icon(Icons.Rounded.Star, "В избранном", tint = Accent, modifier = Modifier.padding(end = 4.dp).size(14.dp))
                Text(a.site.ifBlank { "статья" }, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            when {
                a.loading -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
                    Text("Загружаем текст…", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                a.error.isNotEmpty() -> Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.error, style = MaterialTheme.typography.labelMedium, color = Coral, modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = retry) { Text("Повторить") }
                }
                else -> {
                    Text(buildString {
                        append("${a.minutes} мин")
                        if (a.progress > .02f && a.progress < .98f) append(" · прочитано ${(a.progress * 100).toInt()}%")
                    }, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    if (a.progress > .02f && a.progress < .98f) LinearProgressIndicator(progress = { a.progress }, color = Accent, trackColor = Line, drawStopIndicator = {},
                        modifier = Modifier.padding(top = 6.dp).width(120.dp).height(3.dp).clip(CircleShape))
                }
            }
        }
        if (a.image.isNotBlank()) AsyncImage(a.image, null, contentScale = ContentScale.Crop, modifier = Modifier.padding(start = 12.dp).size(76.dp).clip(RoundedCornerShape(12.dp)).background(Elevated))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = Muted) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(if (a.favorite) "Убрать из избранного" else "В избранное") }, onClick = { menu = false; favorite() })
                DropdownMenuItem(text = { Text(if (a.status == Status.ARCHIVED) "Вернуть в «Читать»" else "Отметить прочитанным") }, onClick = { menu = false; archive() })
                DropdownMenuItem(text = { Text("Открыть в браузере") }, onClick = { menu = false; openInBrowser(context, a.url) })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; delete() })
            }
        }
    }
}

@Composable private fun AddDialog(close: () -> Unit, add: (String) -> Unit) {
    val context = LocalContext.current
    val clip = remember {
        runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            ?.let { Regex("https?://\\S+").find(it)?.value }.orEmpty()
    }
    var text by remember { mutableStateOf(clip) }
    AlertDialog(onDismissRequest = close, title = { Text("Сохранить статью") },
        text = {
            Column {
                if (clip.isNotEmpty()) Text("Ссылка взята из буфера обмена.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("https://…") }, modifier = Modifier.padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) add(text) }))
            }
        },
        confirmButton = { TextButton(onClick = { add(text) }, enabled = text.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}
