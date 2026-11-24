package app.mari.reader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceState(
    val articleId: String = "",
    val title: String = "",
    val index: Int = -1,
    val playing: Boolean = false,
    val error: String? = null,
    /** When the sleep timer pauses reading (milliseconds since 1970), or 0 without a timer. */
    val sleepAt: Long = 0,
) {
    val active get() = articleId.isNotEmpty()
}

/**
 * Reads an article aloud with the phone's own speech engine, paragraph by paragraph.
 * [onStart] is called when reading begins, so the app can keep it going with the screen off.
 */
class Voice(context: Context, private val onStart: () -> Unit = {}) {
    private val mState = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = mState.asStateFlow()
    private var blocks: List<Block> = emptyList()
    private var ready = false
    private var pendingStart: (() -> Unit)? = null
    private var rate = 1f
    private val main = Handler(Looper.getMainLooper())
    private val sleep = Runnable { pause(); mState.value = mState.value.copy(sleepAt = 0) }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) { pendingStart?.invoke(); pendingStart = null }
        else mState.value = mState.value.copy(playing = false, error = "На телефоне нет синтеза речи. Его можно установить в настройках Android: «Синтез речи».")
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                val done = utteranceId?.toIntOrNull() ?: return
                if (mState.value.playing && done == mState.value.index) speak(next(done))
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { mState.value = mState.value.copy(playing = false) }
        })
    }

    fun start(articleId: String, title: String, list: List<Block>, from: Int, speed: Float) {
        blocks = list
        rate = speed
        val first = if (readable(from)) from else next(from - 1)
        mState.value = VoiceState(articleId, title, first, playing = true, sleepAt = mState.value.sleepAt)
        val run = {
            val sample = list.take(12).joinToString(" ") { it.text }
            val cyrillic = sample.count { it in 'а'..'я' || it in 'А'..'Я' } * 2 > sample.count { it.isLetter() }.coerceAtLeast(1)
            // A Russian phone would read English text with a Russian voice; Latin text gets an English one.
            tts.language = when {
                cyrillic -> Locale.forLanguageTag("ru")
                Locale.getDefault().language == "ru" -> Locale.US
                else -> Locale.getDefault()
            }
            tts.setSpeechRate(rate)
            speak(first)
        }
        if (ready) run() else pendingStart = run
        onStart()
    }

    fun toggle() { if (mState.value.playing) pause() else resume() }
    fun pause() { mState.value = mState.value.copy(playing = false); tts.stop() }
    fun resume() {
        if (!mState.value.active) return
        val at = if (mState.value.index >= blocks.size) previous(blocks.size) else mState.value.index
        mState.value = mState.value.copy(playing = true, index = at); speak(at); onStart()
    }
    fun forward() = jump(next(mState.value.index))
    fun back() = jump(previous(mState.value.index))
    fun jump(index: Int) { if (index !in blocks.indices) return; tts.stop(); mState.value = mState.value.copy(index = index, playing = true); speak(index) }
    fun speed(value: Float) { rate = value; tts.setSpeechRate(value); if (mState.value.playing) speak(mState.value.index) }
    fun stop() { tts.stop(); main.removeCallbacks(sleep); mState.value = VoiceState() }
    fun release() { runCatching { tts.stop(); tts.shutdown() } }

    /** Pauses reading after [minutes]; 0 turns the timer off. */
    fun sleepIn(minutes: Int) {
        main.removeCallbacks(sleep)
        if (minutes <= 0) { mState.value = mState.value.copy(sleepAt = 0); return }
        main.postDelayed(sleep, minutes * 60_000L)
        mState.value = mState.value.copy(sleepAt = System.currentTimeMillis() + minutes * 60_000L)
    }

    private fun readable(i: Int) = i in blocks.indices && blocks[i].type != Block.IMAGE && blocks[i].type != Block.CODE && blocks[i].text.isNotBlank()
    private fun next(i: Int): Int { var j = i + 1; while (j < blocks.size && !readable(j)) j++; return j }
    private fun previous(i: Int): Int { var j = i - 1; while (j >= 0 && !readable(j)) j--; return j.coerceAtLeast(0) }

    private fun speak(index: Int) {
        if (index >= blocks.size) { main.removeCallbacks(sleep); mState.value = mState.value.copy(playing = false, index = blocks.size, sleepAt = 0); return }
        mState.value = mState.value.copy(index = index)
        val text = blocks[index].text.take(TextToSpeech.getMaxSpeechInputLength() - 1)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
    }
}
