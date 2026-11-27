package app.mari.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mari.reader.ReaderViewModel
import app.mari.reader.data.DayReading
import app.mari.reader.data.plural
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru")

/** Reading totals over a range of days. */
private data class Totals(val seconds: Int, val articles: Int)

private fun Map<String, DayReading>.totals(from: LocalDate, to: LocalDate): Totals {
    val days = filterKeys { k -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }.values
    return Totals(days.sumOf { it.seconds }, days.sumOf { it.finished.size })
}

private fun minutesText(seconds: Int): String {
    val m = seconds / 60
    return when {
        m >= 60 -> "${m / 60} ч ${m % 60} мин"
        else -> "$m мин"
    }
}

private fun articlesText(n: Int) = "$n ${plural(n, "статья", "статьи", "статей")}"

/** A line under the title: what was read in the last seven days. Hidden until there is something to show. */
@Composable fun ReadingLine(model: ReaderViewModel, open: () -> Unit) {
    val log by model.reading.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val week = log.totals(today.minusDays(6), today)
    if (log.isEmpty()) return
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("За неделю: ${articlesText(week.articles)} · ${minutesText(week.seconds)}", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Итоги чтения", tint = Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadingSheet(model: ReaderViewModel, close: () -> Unit) {
    val log by model.reading.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val week = log.totals(today.minusDays(6), today)
    val before = log.totals(today.minusDays(13), today.minusDays(7))
    val month = log.totals(today.withDayOfMonth(1), today)
    val all = log.totals(LocalDate.MIN, today)
    val streak = generateSequence(if ((log[today.toString()]?.seconds ?: 0) > 0) today else today.minusDays(1)) { it.minusDays(1) }
        .takeWhile { (log[it.toString()]?.seconds ?: 0) > 0 }.take(3650).count()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val most = days.maxOf { log[it.toString()]?.seconds ?: 0 }.coerceAtLeast(60)

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Итоги чтения", style = MaterialTheme.typography.headlineSmall)
            Text("За 7 дней", style = MaterialTheme.typography.labelLarge, color = Muted)
            Text("${articlesText(week.articles)} · ${minutesText(week.seconds)}", style = MaterialTheme.typography.titleLarge)
            val diff = (week.seconds - before.seconds) / 60
            if (before.seconds > 0 || week.seconds > 0) Text(when {
                before.seconds == 0 -> "Неделей раньше чтения не было"
                diff > 0 -> "На $diff мин больше, чем неделей раньше"
                diff < 0 -> "На ${-diff} мин меньше, чем неделей раньше"
                else -> "Столько же, сколько неделей раньше"
            }, style = MaterialTheme.typography.bodyMedium, color = if (diff > 0) Accent else Muted)

            // Minutes per day, today on the right.
            Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { d ->
                    val s = log[d.toString()]?.seconds ?: 0
                    Column(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, RU)}: ${minutesText(s)}" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (s >= 60) Text("${s / 60}", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false).height((90f * s / most).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(6.dp)).background(if (d == today) Accent else Accent.copy(alpha = .45f)))
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, RU), style = MaterialTheme.typography.labelSmall,
                            color = if (d == today) Accent else Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            HorizontalDivider(color = Line)
            StatRow("В этом месяце", "${articlesText(month.articles)} · ${minutesText(month.seconds)}")
            StatRow("Всего", "${articlesText(all.articles)} · ${minutesText(all.seconds)}")
            StatRow("Дней подряд с чтением", if (streak > 0) "🔥 $streak" else "—")
            Text("Время считается, пока статья открыта и ты её листаешь или слушаешь. Прочитанной статья считается, когда дочитана до конца или отмечена прочитанной.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable private fun StatRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
