package app.mari.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.mari.reader.ui.MariRoot
import app.mari.reader.ui.MariTheme

class MainActivity : ComponentActivity() {
    private val model: ReaderViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { MariTheme { MariRoot(model) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }
    /** "Share → Мари" from a browser or messenger saves the link. */
    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let { model.add(it) }
    }
}
