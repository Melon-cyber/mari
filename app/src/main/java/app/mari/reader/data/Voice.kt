package app.mari.reader.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceState(val articleId: String = "", val index: Int = -1, val playing: Boolean = false, val error: String? = null) {
    val active get() = articleId.isNotEmpty()
}

/** Reads an article aloud with the phone's own speech engine, paragraph by paragraph. */
class Voice(context: Context) {
    private val mState = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = mState.asStateFlow()
    private var blocks: List<Block> = emptyList()
    private var ready = false
    private var pendingStart: (() -> Unit)? = null
    private var rate = 1f

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

    fun start(articleId: String, list: List<Block>, from: Int, speed: Float) {
        blocks = list
        rate = speed
        val first = if (readable(from)) from else next(from - 1)
        mState.value = VoiceState(articleId, first, playing = true)
        val run = {
            val cyrillic = list.take(10).joinToString(" ") { it.text }.count { it in 'а'..'я' || it in 'А'..'Я' } > 20
            tts.language = if (cyrillic) Locale.forLanguageTag("ru") else Locale.getDefault()
            tts.setSpeechRate(rate)
            speak(first)
        }
        if (ready) run() else pendingStart = run
    }

    fun toggle() { if (mState.value.playing) pause() else resume() }
    fun pause() { mState.value = mState.value.copy(playing = false); tts.stop() }
    fun resume() { if (!mState.value.active) return; mState.value = mState.value.copy(playing = true); speak(mState.value.index) }
    fun forward() = jump(next(mState.value.index))
    fun back() = jump(previous(mState.value.index))
    fun jump(index: Int) { if (index !in blocks.indices) return; tts.stop(); mState.value = mState.value.copy(index = index, playing = true); speak(index) }
    fun speed(value: Float) { rate = value; tts.setSpeechRate(value); if (mState.value.playing) speak(mState.value.index) }
    fun stop() { tts.stop(); mState.value = VoiceState() }
    fun release() { runCatching { tts.stop(); tts.shutdown() } }

    private fun readable(i: Int) = i in blocks.indices && blocks[i].type != Block.IMAGE && blocks[i].type != Block.CODE && blocks[i].text.isNotBlank()
    private fun next(i: Int): Int { var j = i + 1; while (j < blocks.size && !readable(j)) j++; return j }
    private fun previous(i: Int): Int { var j = i - 1; while (j >= 0 && !readable(j)) j--; return j.coerceAtLeast(0) }

    private fun speak(index: Int) {
        if (index >= blocks.size) { mState.value = mState.value.copy(playing = false, index = blocks.size); return }
        mState.value = mState.value.copy(index = index)
        val text = blocks[index].text.take(TextToSpeech.getMaxSpeechInputLength() - 1)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
    }
}
