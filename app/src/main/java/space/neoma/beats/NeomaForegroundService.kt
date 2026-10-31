package space.neoma.beats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

class NeomaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "neoma_beats_playback"
        const val NOTIFICATION_ID = 202610

        const val ACTION_START = "space.neoma.beats.ACTION_START"
        const val ACTION_UPDATE = "space.neoma.beats.ACTION_UPDATE"
        const val ACTION_STOP = "space.neoma.beats.ACTION_STOP"

        const val ACTION_PLAY = "space.neoma.beats.ACTION_PLAY"
        const val ACTION_PAUSE = "space.neoma.beats.ACTION_PAUSE"
        const val ACTION_NEXT = "space.neoma.beats.ACTION_NEXT"
        const val ACTION_PREV = "space.neoma.beats.ACTION_PREV"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    private var currentTitle: String = "Neoma Beats"
    private var currentArtist: String = "Streaming Musik Bebas Iklan"
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeomaBeats:AudioPlaybackWakeLock").apply {
            setReferenceCounted(false)
        }

        mediaSession = MediaSessionCompat(this, "NeomaMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    sendActionToActivity(ACTION_PLAY)
                }

                override fun onPause() {
                    sendActionToActivity(ACTION_PAUSE)
                }

                override fun onSkipToNext() {
                    sendActionToActivity(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    sendActionToActivity(ACTION_PREV)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val playing = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)

                currentTitle = title
                currentArtist = artist
                isPlaying = playing

                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
                }

                updateMediaSessionState(playing)
                val notification = buildNotification(title, artist, playing)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY -> sendActionToActivity(ACTION_PLAY)
            ACTION_PAUSE -> sendActionToActivity(ACTION_PAUSE)
            ACTION_NEXT -> sendActionToActivity(ACTION_NEXT)
            ACTION_PREV -> sendActionToActivity(ACTION_PREV)
        }
        return START_STICKY
    }

    private fun updateMediaSessionState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun buildNotification(title: String, artist: String, playing: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Control PendingIntents
        val playPauseIntent = Intent(this, NeomaForegroundService::class.java).apply {
            action = if (playing) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, NeomaForegroundService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val prevIntent = Intent(this, NeomaForegroundService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playPauseIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Neoma Beats Live")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (playing) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun sendActionToActivity(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("media_action", action)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neoma Beats Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi pemutaran lagu latar belakang Neoma Beats"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
