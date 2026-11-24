package app.mari.reader.data

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Translates an article paragraph by paragraph with the public Google Translate web endpoint.
 * Paragraphs go in batches of a few thousand letters, one per line, so a long article takes a few requests.
 */
object ArticleTranslator {
    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** Russian articles are translated into English, all others into Russian. */
    fun target(blocks: List<Block>): String {
        val sample = blocks.filter { it.type != Block.IMAGE && it.type != Block.CODE }.take(12).joinToString(" ") { it.text }
        val cyrillic = sample.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }
        val letters = sample.count { it.isLetter() }.coerceAtLeast(1)
        return if (cyrillic * 2 > letters) "en" else "ru"
    }

    /** Returns the translation of each block (empty for pictures and code); [progress] gets done/total blocks. */
    suspend fun translate(blocks: List<Block>, to: String, progress: (Int, Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val result = MutableList(blocks.size) { "" }
        val todo = blocks.indices.filter { blocks[it].type != Block.IMAGE && blocks[it].type != Block.CODE && blocks[it].text.isNotBlank() }
        val batches = mutableListOf<MutableList<Int>>()
        var size = 0
        for (i in todo) {
            val len = blocks[i].text.length
            if (batches.isEmpty() || size + len > 3500) { batches += mutableListOf<Int>(); size = 0 }
            batches.last() += i; size += len + 1
        }
        var done = 0
        for (batch in batches) {
            val lines = batch.map { blocks[it].text.replace(Regex("\\s*\\n\\s*"), " ") }
            val out = runCatching { request(lines.joinToString("\n"), to).split("\n").map { it.trim() } }.getOrNull()
            if (out != null && out.size == lines.size) batch.forEachIndexed { k, i -> result[i] = out[k] }
            // When lines do not match up, each paragraph goes on its own.
            else batch.forEachIndexed { k, i -> result[i] = request(lines[k], to).replace("\n", " ").trim() }
            done += batch.size
            progress(done, todo.size)
        }
        result
    }

    private fun request(text: String, to: String): String {
        val body = FormBody.Builder().add("q", text).build()
        val request = Request.Builder().url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$to&dt=t")
            .header("User-Agent", AGENT).post(body).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val root = JsonParser.parseString(r.body?.string().orEmpty()).asJsonArray
            val parts = root.get(0)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            return parts.joinToString("") { seg -> seg.takeIf { it.isJsonArray }?.asJsonArray?.get(0)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() }
        }
    }
}
