package app.mari.reader

import android.app.Application
import app.mari.reader.data.Store
import app.mari.reader.data.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MariApp : Application() {
    /** Downloads keep going when a screen closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { Store(this, scope) }
    val voice by lazy { Voice(this) }
}
