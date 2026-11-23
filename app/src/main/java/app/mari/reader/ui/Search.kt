package app.mari.reader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mari.reader.ReaderViewModel
import app.mari.reader.data.Engine

/**
 * Built-in search. On the web, the chosen search engine's page opens right inside Mari; tapping any found link
 * opens that article in the reader and saves it. Saved articles are searched by their full text.
 */
@Composable fun SearchScreen(model: ReaderViewModel, close: () -> Unit) {
    val s by model.search.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            TextField(s.query, model::searchQuery, singleLine = true, modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text(if (s.web) "Что почитать?" else "Слово из сохранённой статьи") },
                trailingIcon = { if (s.query.isNotEmpty()) IconButton(onClick = { model.searchQuery("") }) { Icon(Icons.Rounded.Close, "Очистить") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); model.runSearch() }),
                colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedIndicatorColor = Accent, unfocusedIndicatorColor = Line),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!s.web, { model.searchMode(false) }, label = { Text("В сохранённых") }, colors = chip())
            Engine.entries.forEach { e -> FilterChip(s.web && settings.engine == e, { if (!s.web) model.searchMode(true); model.engine(e) }, label = { Text(e.label) }, colors = chip()) }
        }
        if (s.loading || (s.web && pageLoading)) LinearProgressIndicator(color = Accent, trackColor = Line, modifier = Modifier.fillMaxWidth())

        if (s.web) {
            val url = s.webUrl
            if (url == null) Text("Напиши тему и нажми «Найти» на клавиатуре. Откроется обычный поиск ${settings.engine.label}, а любая найденная статья откроется в Мари в режиме чтения и сохранится.",
                color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            else SearchPage(url, settings.engine, onLoading = { pageLoading = it }, onView = { web = it }) { found -> keyboard?.hide(); model.saveAndOpen(found) }
        } else {
            s.error?.let { Text(it, color = Muted, modifier = Modifier.padding(20.dp)) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(s.hits, key = { it.article.id }) { h ->
                    Column(Modifier.fillMaxWidth().clickable { close(); model.open(h.article.id) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(h.article.site, style = MaterialTheme.typography.labelMedium, color = Accent, maxLines = 1)
                        Text(h.article.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(h.snippet, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable private fun chip() = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink)

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun SearchPage(url: String, engine: Engine, onLoading: (Boolean) -> Unit, onView: (WebView) -> Unit, found: (String) -> Unit) {
    val currentFound by rememberUpdatedState(found)
    val currentEngine by rememberUpdatedState(engine)
    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    /** Links to the engine's own pages stay here; a link anywhere else is an article for the reader. */
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (target.scheme != "http" && target.scheme != "https") return true
                        if (currentEngine.ownsHost(target.host.orEmpty())) return false
                        if (!request.isForMainFrame) return false
                        currentFound(target.toString())
                        return true
                    }
                    override fun onPageStarted(view: WebView, address: String?, favicon: Bitmap?) { onLoading(true) }
                    override fun onPageFinished(view: WebView, address: String?) { onLoading(false) }
                }
                onView(this)
                loadUrl(url)
            }
        }, update = { view -> if (view.tag != url) { view.tag = url; if (view.url == null || Uri.parse(view.url).toString() != url) view.loadUrl(url) } })
    }
}
