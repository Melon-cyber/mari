package app.mari.reader.data

import java.net.URLEncoder

/** Search engines the built-in search page can use. */
enum class Engine(val label: String, private val template: String, private val hosts: List<String>) {
    YANDEX("Яндекс", "https://ya.ru/search/?text=%s", listOf("ya.ru", "yandex.ru", "yandex.com", "yandex.net", "yastatic.net")),
    GOOGLE("Google", "https://www.google.com/search?q=%s", listOf("google.com", "google.ru", "gstatic.com", "googleusercontent.com", "consent.google.com")),
    DUCK("DuckDuckGo", "https://duckduckgo.com/?q=%s&kl=ru-ru", listOf("duckduckgo.com"));

    fun url(query: String): String = template.format(URLEncoder.encode(query, "UTF-8"))

    /** True for the engine's own pages (results, next page, settings); anything else is a found article. */
    fun ownsHost(host: String): Boolean = hosts.any { host == it || host.endsWith(".$it") } ||
        (this == YANDEX && Regex("(^|\\.)yandex\\.[a-z]+$").containsMatchIn(host)) || (this == GOOGLE && Regex("(^|\\.)google\\.[a-z.]+$").containsMatchIn(host))
}

/** A hit inside a saved article: where the words were found. */
data class SavedHit(val article: Article, val snippet: String)

/** Finds words in the full text of saved articles, not only in their titles. */
fun searchSaved(store: Store, query: String): List<SavedHit> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
    if (words.isEmpty()) return emptyList()
    return store.articles.value.filter { !it.loading && it.error.isEmpty() }.mapNotNull { a ->
        val head = (a.title + " " + a.site).lowercase()
        val text = store.blocks(a.id).joinToString("\n") { it.text }
        val low = text.lowercase()
        if (!words.all { it in head || it in low }) return@mapNotNull null
        val at = words.map { low.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val snippet = if (at == null) a.excerpt else "…" + text.substring((at - 60).coerceAtLeast(0), (at + 140).coerceAtMost(text.length)).replace('\n', ' ').trim() + "…"
        SavedHit(a, snippet)
    }.sortedWith(compareByDescending<SavedHit> { hit -> words.count { it in hit.article.title.lowercase() } }.thenByDescending { it.article.saved })
}
