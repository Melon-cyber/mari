package app.mari.reader.data

enum class Status { UNREAD, ARCHIVED }

/** Marked words in a paragraph: block number and character range inside it. */
data class Highlight(val id: String, val block: Int, val start: Int, val end: Int, val text: String, val created: Long = System.currentTimeMillis())

/** One piece of an article: heading, paragraph, image, list item, quote or code. */
data class Block(val type: String, val text: String = "", val url: String = "") {
    companion object {
        const val HEADING = "h"
        const val PARAGRAPH = "p"
        const val IMAGE = "img"
        const val ITEM = "li"
        const val QUOTE = "q"
        const val CODE = "code"
    }
}

/** A saved article without its text, for the library list. */
data class Article(
    val id: String,
    val url: String,
    val title: String = "",
    val site: String = "",
    val author: String = "",
    val image: String = "",
    val excerpt: String = "",
    val saved: Long = System.currentTimeMillis(),
    val words: Int = 0,
    val status: Status = Status.UNREAD,
    val favorite: Boolean = false,
    /** Index of the block the reader stopped at, and share read 0..1. */
    val position: Int = 0,
    val progress: Float = 0f,
    /** True while the page is being downloaded. */
    val loading: Boolean = false,
    val error: String = "",
    val highlights: List<Highlight> = emptyList(),
) {
    val minutes: Int get() = (words / 180).coerceAtLeast(1)
}

enum class ReaderTheme(val label: String) { DARK("Тёмная"), SEPIA("Сепия"), LIGHT("Светлая") }

data class Settings(
    val fontSize: Int = 19,
    val serif: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.DARK,
    val lineHeight: Float = 1.6f,
    val voiceRate: Float = 1f,
    val searchEngine: String = "YANDEX",
) {
    val engine: Engine get() = runCatching { Engine.valueOf(searchEngine) }.getOrDefault(Engine.YANDEX)
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100; val m10 = n % 10
    return when { m100 in 11..14 -> many; m10 == 1 -> one; m10 in 2..4 -> few; else -> many }
}
