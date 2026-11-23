package app.mari.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.mari.reader.data.Article
import app.mari.reader.data.Block
import app.mari.reader.data.Settings
import app.mari.reader.data.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Note(val text: String, val undo: (() -> Unit)? = null)

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
    fun open(id: String?) { mOpen.value = id }

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
