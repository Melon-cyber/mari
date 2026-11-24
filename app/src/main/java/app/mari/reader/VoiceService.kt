package app.mari.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mari.reader.data.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps reading aloud going with the screen off or in another app, and shows a notification
 * with pause and stop. Lives only while an article is being read.
 */
class VoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val voice get() = (application as MariApp).voice
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Чтение вслух", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        scope.launch {
            voice.state.collect { s ->
                if (!s.active) { ServiceCompat.stopForeground(this@VoiceService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
                else if (started) manager.notify(ID, build(s))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> voice.toggle()
            ACTION_STOP -> voice.stop()
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, ID, build(voice.state.value), type); started = true }
        if (!voice.state.value.active) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, VoiceService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun build(s: VoiceState) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(s.title.ifBlank { "Мари читает" })
        .setContentText(if (s.playing) "Читаю вслух" else "Пауза")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, if (s.playing) "Пауза" else "Читать", action(ACTION_TOGGLE))
        .addAction(0, "Стоп", action(ACTION_STOP))
        .setOngoing(s.playing)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    companion object {
        private const val CHANNEL = "voice"
        private const val ID = 7
        const val ACTION_TOGGLE = "app.mari.reader.TOGGLE"
        const val ACTION_STOP = "app.mari.reader.STOP"
    }
}
