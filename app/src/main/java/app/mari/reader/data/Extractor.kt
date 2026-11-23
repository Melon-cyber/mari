package app.mari.reader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** What was found on a page: the article's details and its text as blocks. */
data class Extracted(val title: String, val site: String, val author: String, val image: String, val blocks: List<Block>)

/**
 * Finds the main text of a web page and drops menus, ads, comments and other clutter,
 * the way "reader mode" in a browser does.
 */
object Extractor {
    private val junkTags = "script, style, noscript, iframe, form, button, svg, nav, footer, aside, select, input, template, dialog"
    private val junkHint = Regex("comment|share|social|related|promo|sidebar|footer|subscribe|advert|banner|cookie|popup|modal|newsletter|breadcrumb|navbar|menu|recommend|sponsor|toolbar|tags|rating|login|signup", RegexOption.IGNORE_CASE)
    private val goodHint = Regex("article|content|entry|post|story|text|body|main", RegexOption.IGNORE_CASE)

    fun extract(html: String, url: String): Extracted = extract(Jsoup.parse(html, url), url)

    fun extract(doc: Document, url: String): Extracted {
        val title = meta(doc, "og:title") ?: meta(doc, "twitter:title") ?: doc.title()
        val site = meta(doc, "og:site_name") ?: runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrDefault("")
        val author = meta(doc, "author") ?: meta(doc, "article:author")?.takeIf { !it.startsWith("http") } ?: doc.selectFirst("[rel=author], .author, [itemprop=author]")?.text()?.take(80) ?: ""
        val image = meta(doc, "og:image")?.let { absolute(doc, it) } ?: ""

        doc.select(junkTags).remove()
        doc.select("[role=navigation], [role=banner], [role=complementary], [aria-hidden=true], [hidden]").remove()
        doc.select("header").filter { it.select("p").size < 2 }.forEach { it.remove() }
        doc.allElements.filter { el ->
            el !== doc.body() && el.tagName() !in setOf("html", "body", "article", "main") &&
                junkHint.containsMatchIn(el.className() + " " + el.id()) && !goodHint.containsMatchIn(el.className() + " " + el.id()) && el.select("p").size < 3
        }.forEach { runCatching { it.remove() } }

        val root = pickRoot(doc) ?: doc.body() ?: return Extracted(title.trim(), site, author.trim(), image, emptyList())
        val blocks = mutableListOf<Block>()
        walk(root, blocks, doc)
        val cleaned = blocks.filterIndexed { i, b -> !(i < 3 && b.type == Block.HEADING && same(b.text, title)) }
            .fold(mutableListOf<Block>()) { list, b -> if (list.lastOrNull() != b) list += b; list }
            .dropWhile { it.type == Block.IMAGE && it.url == image }
        return Extracted(title.trim(), site.trim(), author.trim(), image, cleaned)
    }

    /** The element holding most of the readable text. */
    private fun pickRoot(doc: Document): Element? {
        doc.select("article").maxByOrNull { textLength(it) }?.takeIf { textLength(it) > 600 }?.let { return it }
        val scores = HashMap<Element, Double>()
        for (p in doc.select("p, pre, blockquote")) {
            val text = p.text()
            if (text.length < 40) continue
            val score = 1 + text.count { it == ',' } + minOf(text.length / 100, 3)
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2.0 }
        }
        return scores.maxByOrNull { (el, score) -> score * (1 - linkDensity(el)) * (if (goodHint.containsMatchIn(el.className() + el.id())) 1.25 else 1.0) }?.key
    }

    private fun walk(el: Element, out: MutableList<Block>, doc: Document) {
        for (child in el.children()) {
            when (child.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> child.text().clean().takeIf { it.isNotEmpty() }?.let { out += Block(Block.HEADING, it) }
                "p" -> {
                    child.select("img").forEach { img -> imageOf(img, doc)?.let { out += Block(Block.IMAGE, img.attr("alt").clean(), it) } }
                    paragraph(child).takeIf { it.length > 1 }?.let { out += Block(Block.PARAGRAPH, it) }
                }
                "img" -> imageOf(child, doc)?.let { out += Block(Block.IMAGE, child.attr("alt").clean(), it) }
                "picture" -> child.selectFirst("img")?.let { img -> imageOf(img, doc)?.let { out += Block(Block.IMAGE, img.attr("alt").clean(), it) } }
                "figure" -> {
                    val img = child.selectFirst("img")
                    val src = img?.let { imageOf(it, doc) }
                    if (src != null) out += Block(Block.IMAGE, child.selectFirst("figcaption")?.text()?.clean() ?: img.attr("alt").clean(), src)
                    else walk(child, out, doc)
                }
                "ul", "ol" -> child.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
                    val text = paragraph(li)
                    if (text.isNotEmpty() && linkDensity(li) < .8) out += Block(Block.ITEM, if (child.tagName() == "ol") "${i + 1}. $text" else text)
                }
                "blockquote" -> paragraph(child).takeIf { it.isNotEmpty() }?.let { out += Block(Block.QUOTE, it) }
                "pre" -> child.wholeText().trimEnd().takeIf { it.isNotBlank() }?.let { out += Block(Block.CODE, it) }
                "table" -> child.select("tr").map { tr -> tr.select("th, td").joinToString(" · ") { it.text().clean() } }.filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }?.let { rows -> out += Block(Block.CODE, rows.joinToString("\n")) }
                "br", "hr" -> {}
                else -> {
                    // Text sitting right inside a <div> (common on simple sites) is a paragraph too.
                    val own = child.textNodes().joinToString(" ") { it.text() }.clean()
                    if (child.children().isEmpty() || own.length > 60) {
                        if (child.children().isEmpty()) paragraph(child).takeIf { it.length > 30 }?.let { out += Block(Block.PARAGRAPH, it) }
                        else { out += Block(Block.PARAGRAPH, own); walk(child, out, doc) }
                    } else walk(child, out, doc)
                }
            }
        }
    }

    /** Text of an element with line breaks kept. */
    private fun paragraph(el: Element): String {
        val sb = StringBuilder()
        fun visit(e: Element) {
            for (node in e.childNodes()) when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> if (node.tagName() == "br") sb.append('\n') else if (node.tagName() != "img") visit(node)
            }
        }
        visit(el)
        return sb.toString().lines().joinToString("\n") { it.clean() }.trim()
    }

    private fun imageOf(img: Element, doc: Document): String? {
        val src = listOf("data-src", "data-original", "data-lazy-src", "src").firstNotNullOfOrNull { img.attr(it).takeIf { s -> s.isNotBlank() && !s.startsWith("data:") } }
            ?: img.attr("srcset").split(',').lastOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: return null
        val w = img.attr("width").toIntOrNull()
        if (w != null && w < 80) return null
        return absolute(doc, src).takeIf { it.startsWith("http") && !it.contains("pixel") && !it.endsWith(".svg") }
    }

    private fun absolute(doc: Document, link: String): String = runCatching { java.net.URI(doc.location()).resolve(link.trim()).toString() }.getOrDefault(link)
    private fun meta(doc: Document, name: String): String? =
        doc.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.clean()?.takeIf { it.isNotEmpty() }
    private fun textLength(el: Element) = el.select("p").sumOf { it.text().length }
    private fun linkDensity(el: Element): Double {
        val all = el.text().length.coerceAtLeast(1)
        return el.select("a").sumOf { it.text().length }.toDouble() / all
    }
    private fun same(a: String, b: String) = a.lowercase().filter { it.isLetterOrDigit() }.let { x -> val y = b.lowercase().filter { it.isLetterOrDigit() }; x.isNotEmpty() && (x == y || y.startsWith(x) || x.startsWith(y)) }
    private fun String.clean() = replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
}
