package app.mari.reader.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import app.mari.reader.R
import app.mari.reader.data.Article
import app.mari.reader.data.Highlight
import app.mari.reader.data.ReaderTheme

/** The marker colour on each reader theme. */
fun markerColor(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.DARK -> 0x66E8A24A
    ReaderTheme.SEPIA -> 0x88F2C14E.toInt()
    ReaderTheme.LIGHT -> 0x88FFE066.toInt()
}

private const val MENU_MARK = 0x4D41
private const val MENU_MANTRA = 0x4D42
private const val MANTRA = "app.mantra.cards"

/** The sentence around [start]..[end] in [text], as an example for a flashcard. */
private fun sentenceAround(text: String, start: Int, end: Int): String {
    val stops = ".!?…\n"
    var s = start; while (s > 0 && text[s - 1] !in stops) s--
    var e = end; while (e < text.length && text[e] !in stops) e++
    if (e < text.length) e++
    return text.substring(s, e).trim().take(400)
}

/**
 * A paragraph that can be marked: select words with a long press and choose «Выделить».
 * A tap on marked words opens what can be done with that highlight.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable fun MarkableText(
    text: String,
    highlights: List<Highlight>,
    sizeSp: Float,
    lineHeight: Float,
    serif: Boolean,
    italic: Boolean,
    bold: Boolean,
    color: Color,
    marker: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onMark: (start: Int, end: Int) -> Unit,
    onTapMark: (Highlight) -> Unit,
) {
    val mark by rememberUpdatedState(onMark)
    val tap by rememberUpdatedState(onTapMark)
    val marks by rememberUpdatedState(highlights)
    AndroidView(modifier = modifier.fillMaxWidth(), factory = { context ->
        TextView(context).apply {
            setTextIsSelectable(true)
            highlightColor = accent.copy(alpha = .35f).toArgb()
            customSelectionActionModeCallback = object : ActionMode.Callback {
                private val mantra = runCatching { context.packageManager.getLaunchIntentForPackage(MANTRA) != null }.getOrDefault(false)
                // Мантра also offers itself for any selected text; our own item sends the sentence too, so that one goes.
                private fun dropDuplicate(menu: Menu): Boolean {
                    val extra = (0 until menu.size()).map { menu.getItem(it) }.filter { it.itemId != MENU_MANTRA && it.intent?.component?.packageName == MANTRA }
                    extra.forEach { menu.removeItem(it.itemId) }
                    return extra.isNotEmpty()
                }
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(Menu.NONE, MENU_MARK, 0, "Выделить")
                    if (mantra) menu.add(Menu.NONE, MENU_MANTRA, 1, "В Мантру")
                    dropDuplicate(menu)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = mantra && dropDuplicate(menu)
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val start = minOf(selectionStart, selectionEnd); val end = maxOf(selectionStart, selectionEnd)
                    if (item.itemId == MENU_MANTRA) {
                        if (end > start) {
                            val all = text.toString()
                            val word = all.substring(start, end).trim()
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).setPackage(MANTRA).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, word).putExtra("$MANTRA.CONTEXT", sentenceAround(all, start, end)))
                            }
                        }
                        mode.finish()
                        return true
                    }
                    if (item.itemId != MENU_MARK) return false
                    if (end > start) mark(start, end)
                    mode.finish()
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) {}
            }
            // A short tap (not a long press for selecting) on marked words opens that highlight.
            var downAt = 0L; var downX = 0f; var downY = 0f
            setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downAt = e.eventTime; downX = e.x; downY = e.y }
                    MotionEvent.ACTION_UP -> if (e.eventTime - downAt < 300 && kotlin.math.abs(e.x - downX) < 20 && kotlin.math.abs(e.y - downY) < 20) {
                        val tv = view as TextView
                        val offset = tv.getOffsetForPosition(e.x, e.y)
                        marks.firstOrNull { offset in it.start until it.end }?.let { tap(it); return@setOnTouchListener true }
                    }
                }
                false
            }
        }
    }, update = { tv ->
        val family = if (serif) Typeface.SERIF else (ResourcesCompat.getFont(tv.context, R.font.manrope) ?: Typeface.SANS_SERIF)
        tv.typeface = Typeface.create(family, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        tv.setLineSpacing(0f, lineHeight)
        tv.setTextColor(color.toArgb())
        val spannable = SpannableString(text)
        highlights.forEach { h ->
            val s = h.start.coerceIn(0, text.length); val e = h.end.coerceIn(s, text.length)
            if (e > s) spannable.setSpan(BackgroundColorSpan(marker), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tv.text.toString() != text || tv.tag != highlights) { tv.setText(spannable, TextView.BufferType.SPANNABLE); tv.tag = highlights }
    })
}

/** All highlights of one article, in reading order; a tap jumps to the place. */
@Composable fun HighlightList(article: Article, jump: (Highlight) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text("Выделения", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (article.highlights.isEmpty()) Text("Выдели слова долгим нажатием и выбери «Выделить» — они появятся здесь.", color = Muted, modifier = Modifier.padding(20.dp))
        LazyColumn {
            items(article.highlights.sortedWith(compareBy({ it.block }, { it.start })), key = { it.id }) { h ->
                Text("«${h.text}»", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.fillMaxWidth().clickable { jump(h) }.padding(horizontal = 20.dp, vertical = 12.dp))
                HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}
