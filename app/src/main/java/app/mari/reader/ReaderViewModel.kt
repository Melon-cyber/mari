package app.mari.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.mari.reader.data.Article
import app.mari.reader.data.Block
import app.mari.reader.data.Settings
import app.mari.reader.data.Status
import androidx.lifecycle.viewModelScope
import app.mari.reader.data.SavedHit
import app.mari.reader.data.Engine
import app.mari.reader.data.Highlight
import app.mari.reader.data.searchSaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Note(val text: String, val undo: (() -> Unit)? = null)

data class SearchState(
    val query: String = "",
    val web: Boolean = true,
    val loading: Boolean = false,
    /** The search page to show for a web search; changes each time the search is run. */
    val webUrl: String? = null,
    val hits: List<SavedHit> = emptyList(),
    val error: String? = null,
    val done: Boolean = false,
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MariApp
    private val store = app.store
    val articles = store.articles
    val settings = store.settings
    val voice = app.voice.state
    private val mNote = MutableStateFlow<Note?>(null)
    val note = mNote.asStateFlow()
    private val mOpen = MutableStateFlow<String?>(null)
    /** The article on screen, or null for the list. */
    val open = mOpen.asStateFlow()

    fun note(value: Note?) { mNote.value = value }

    private val mSearch = MutableStateFlow(SearchState())
    val search = mSearch.asStateFlow()
    private var searchJob: Job? = null

    fun searchQuery(text: String) { mSearch.value = mSearch.value.copy(query = text, done = false, error = null); if (!mSearch.value.web) runSearch() }
    fun searchMode(webMode: Boolean) { mSearch.value = mSearch.value.copy(web = webMode, done = false, error = null); if (mSearch.value.query.isNotBlank()) runSearch() }
    fun clearSearch() { searchJob?.cancel(); mSearch.value = SearchState() }

    /** Runs the search: on the web when [SearchState.web], otherwise in the text of saved articles. */
    fun runSearch() {
        val s = mSearch.value
        val q = s.query.trim()
        searchJob?.cancel()
        if (q.length < 2) { mSearch.value = s.copy(webUrl = null, hits = emptyList(), loading = false, done = false); return }
        searchJob = viewModelScope.launch {
            if (!s.web) delay(250)
            mSearch.value = mSearch.value.copy(loading = true, error = null)
            if (s.web) {
                mSearch.value = mSearch.value.copy(loading = false, done = true, webUrl = settings.value.engine.url(q))
            } else {
                val hits = withContext(Dispatchers.IO) { searchSaved(app.store, q) }
                mSearch.value = mSearch.value.copy(loading = false, done = true, hits = hits, error = if (hits.isEmpty()) "В сохранённых статьях такого нет." else null)
            }
        }
    }

    /** Saves a search result and opens it; the text appears as soon as it is downloaded. */
    fun saveAndOpen(url: String) { mOpen.value = store.add(url) }
    fun saveOnly(url: String) { store.add(url); note(Note("Сохранено в «Читать»")) }
    fun engine(e: Engine) { store.settings { it.copy(searchEngine = e.name) }; if (mSearch.value.web && mSearch.value.query.isNotBlank()) runSearch() }
    fun open(id: String?) { mOpen.value = id }

    private val mJump = MutableStateFlow<Int?>(null)
    /** A paragraph the reader should scroll to (from the list of highlights). */
    val jump = mJump.asStateFlow()
    fun jumpTo(articleId: String, block: Int) { mOpen.value = articleId; mJump.value = block }
    fun jumped() { mJump.value = null }

    fun mark(article: Article, block: Int, start: Int, end: Int) {
        val text = store.blocks(article.id).getOrNull(block)?.text ?: return
        val s = start.coerceIn(0, text.length); val e = end.coerceIn(s, text.length)
        if (e - s < 2) return
        store.addHighlight(article.id, Highlight(java.util.UUID.randomUUID().toString(), block, s, e, text.substring(s, e)))
    }
    fun unmark(article: Article, h: Highlight) {
        store.removeHighlight(article.id, h.id)
        note(Note("Выделение убрано") { store.addHighlight(article.id, h) })
    }

    fun add(text: String, openIt: Boolean = false) {
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '»', '"')
            ?: text.trim().takeIf { !it.contains(' ') && it.contains('.') }
            ?: run { note(Note("Здесь нет ссылки на статью")); return }
        val id = store.add(url)
        note(Note("Статья сохранена, загружаем текст…"))
        if (openIt) mOpen.value = id
    }

    fun retry(id: String) = store.retry(id)
    fun blocks(id: String): List<Block> = store.blocks(id)
    fun progress(id: String, position: Int, share: Float) = store.progress(id, position, share)
    fun favorite(a: Article) { store.favorite(a.id, !a.favorite); note(Note(if (a.favorite) "Убрано из избранного" else "Добавлено в избранное")) }
    fun archive(a: Article) {
        val to = if (a.status == Status.ARCHIVED) Status.UNREAD else Status.ARCHIVED
        store.setStatus(a.id, to)
        note(Note(if (to == Status.ARCHIVED) "Отмечено как прочитанное" else "Снова в списке «Читать»") { store.setStatus(a.id, a.status) })
    }
    fun delete(a: Article) {
        if (voice.value.articleId == a.id) app.voice.stop()
        val saved = store.delete(a.id) ?: return
        if (mOpen.value == a.id) mOpen.value = null
        note(Note("Статья удалена") { store.restore(saved.first, saved.second) })
    }
    fun settings(change: (Settings) -> Settings) = store.settings(change)

    fun listen(article: Article, blocks: List<Block>, from: Int) = app.voice.start(article.id, blocks, from, settings.value.voiceRate)
    fun voiceToggle() = app.voice.toggle()
    fun voiceForward() = app.voice.forward()
    fun voiceBack() = app.voice.back()
    fun voiceJump(index: Int) = app.voice.jump(index)
    fun voiceStop() = app.voice.stop()
    fun voiceSpeed(value: Float) { store.settings { it.copy(voiceRate = value) }; app.voice.speed(value) }
}
