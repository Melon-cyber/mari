package app.mari.reader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The reading list. The list itself lives in one small file; each article's text is a separate file,
 * so the list opens fast and saved articles are readable without internet.
 */
class Store(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "articles").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "library.json")
    private val settingsFile = File(context.filesDir, "settings.json")
    private val gson = Gson()
    private val lock = Any()
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private val mArticles = MutableStateFlow(readIndex())
    val articles: StateFlow<List<Article>> = mArticles.asStateFlow()
    private val mSettings = MutableStateFlow(runCatching { gson.fromJson(settingsFile.readText(), Settings::class.java) }.getOrNull()?.let(::repair) ?: Settings())
    val settings: StateFlow<Settings> = mSettings.asStateFlow()

    fun article(id: String): Article? = mArticles.value.firstOrNull { it.id == id }

    /** Saves a link and downloads its text in the background. Returns the article id. */
    fun add(rawUrl: String): String {
        val url = rawUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val id = idOf(url)
        val existing = article(id)
        if (existing != null && existing.error.isEmpty()) return id
        update(id) { (it ?: Article(id, url, title = url)).copy(loading = true, error = "") }
        scope.launch { fetch(id, url) }
        return id
    }

    fun retry(id: String) { article(id)?.let { update(id) { a -> a?.copy(loading = true, error = "") }; scope.launch { fetch(id, it.url) } } }

    private suspend fun fetch(id: String, url: String) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url)
                    // Many sites hide the text from unknown clients; ask the way a phone browser does.
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                    .header("Accept-Language", "ru,en;q=0.8").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Сайт ответил ошибкой ${response.code}")
                    val body = response.body ?: throw IOException("Пустой ответ")
                    val type = body.contentType()
                    if (type != null && type.subtype != "html" && type.subtype != "xhtml+xml") throw IOException("По ссылке не статья, а файл")
                    val bytes = body.bytes()
                    val doc = Jsoup.parse(bytes.inputStream(), type?.charset()?.name(), response.request.url.toString())
                    Extractor.extract(doc, response.request.url.toString())
                }
            }
        }
        val extracted = result.getOrNull()
        if (extracted == null || extracted.blocks.none { it.type == Block.PARAGRAPH }) {
            val why = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() && it.any { ch -> ch in 'а'..'я' } }
                ?: if (extracted != null) "Не удалось найти текст статьи на странице" else "Не удалось открыть страницу. Проверь интернет."
            update(id) { it?.copy(loading = false, error = why, title = extracted?.title?.ifBlank { null } ?: it.title, site = extracted?.site ?: it.site) }
            return
        }
        writeBlocks(id, extracted.blocks)
        val words = extracted.blocks.filter { it.type != Block.IMAGE }.sumOf { b -> b.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        val excerpt = extracted.blocks.firstOrNull { it.type == Block.PARAGRAPH && it.text.length > 60 }?.text?.take(220).orEmpty()
        update(id) { it?.copy(title = extracted.title.ifBlank { it.url }, site = extracted.site, author = extracted.author, image = extracted.image,
            excerpt = excerpt, words = words, loading = false, error = "") }
    }

    fun blocks(id: String): List<Block> = runCatching {
        gson.fromJson<List<Block>>(File(dir, "$id.json").readText(), object : TypeToken<List<Block>>() {}.type)
    }.getOrNull().orEmpty().filter { it?.type != null }

    fun setStatus(id: String, status: Status) = update(id) { it?.copy(status = status, progress = if (status == Status.UNREAD && it.progress >= .98f) 0f else it.progress, position = if (status == Status.UNREAD && it.progress >= .98f) 0 else it.position) }
    fun favorite(id: String, on: Boolean) = update(id) { it?.copy(favorite = on) }
    fun progress(id: String, position: Int, share: Float) = update(id) { it?.copy(position = position, progress = share.coerceIn(0f, 1f)) }

    /** Removes an article; returns what is needed to bring it back. */
    fun delete(id: String): Pair<Article, List<Block>>? {
        val article = article(id) ?: return null
        val blocks = blocks(id)
        synchronized(lock) { mArticles.value = mArticles.value.filter { it.id != id }; writeIndex() }
        File(dir, "$id.json").delete()
        return article to blocks
    }

    fun restore(article: Article, blocks: List<Block>) {
        if (blocks.isNotEmpty()) writeBlocks(article.id, blocks)
        update(article.id) { article }
    }

    fun settings(change: (Settings) -> Settings) = synchronized(lock) {
        mSettings.value = change(mSettings.value)
        runCatching { settingsFile.writeText(gson.toJson(mSettings.value)) }
    }

    private fun update(id: String, change: (Article?) -> Article?) = synchronized(lock) {
        val old = mArticles.value.firstOrNull { it.id == id }
        val next = change(old) ?: return@synchronized
        mArticles.value = if (old == null) listOf(next) + mArticles.value else mArticles.value.map { if (it.id == id) next else it }
        writeIndex()
    }

    private fun writeBlocks(id: String, blocks: List<Block>) { runCatching { File(dir, "$id.json").writeText(gson.toJson(blocks)) } }

    private fun writeIndex() {
        val temp = File(indexFile.parentFile, "library.json.tmp")
        runCatching {
            temp.writeText(gson.toJson(mArticles.value))
            if (!temp.renameTo(indexFile)) { indexFile.delete(); temp.renameTo(indexFile) }
        }
    }

    /** A download cut short by closing the app is retried when the list opens again. */
    private fun readIndex(): List<Article> = runCatching {
        gson.fromJson<List<Article>>(indexFile.readText(), object : TypeToken<List<Article>>() {}.type)
    }.getOrNull().orEmpty().filter { it?.id != null && it.url != null }.map { a ->
        @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
        a.copy(title = a.title ?: a.url, site = a.site ?: "", author = a.author ?: "", image = a.image ?: "", excerpt = a.excerpt ?: "",
            status = a.status ?: Status.UNREAD, error = if (a.loading) "Загрузка прервалась. Нажми «Повторить»." else a.error ?: "", loading = false)
    }

    @Suppress("USELESS_ELVIS")
    private fun repair(s: Settings) = s.copy(theme = s.theme ?: ReaderTheme.DARK, fontSize = s.fontSize.coerceIn(14, 30), lineHeight = s.lineHeight.takeIf { it in 1.2f..2.2f } ?: 1.6f,
        voiceRate = s.voiceRate.takeIf { it in .5f..2.5f } ?: 1f, searchEngine = s.searchEngine ?: "YANDEX")

    private fun idOf(url: String) = MessageDigest.getInstance("SHA-1").digest(url.substringBefore('#').toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
}
