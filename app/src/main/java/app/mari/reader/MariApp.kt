package app.mari.reader

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import app.mari.reader.data.Store
import app.mari.reader.data.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MariApp : Application() {
    /** Downloads keep going when a screen closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { Store(this, scope) }
    /** Reading aloud runs in [VoiceService], so it goes on with the screen off. */
    val voice by lazy { Voice(this) { runCatching { ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java)) } } }
}
