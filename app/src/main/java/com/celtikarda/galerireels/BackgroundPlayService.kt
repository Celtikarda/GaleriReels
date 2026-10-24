package com.celtikarda.galerireels

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

/**
 * Uygulama arka plana geçtiğinde ya da ekran kapandığında videonun sesinin devam etmesini
 * sağlayan ön plan servisi. Bildirim çubuğunda ve kilit ekranında Oynat/Duraklat ve Sonraki
 * kontrollerini gösterir.
 */
class BackgroundPlayService : Service() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        mediaSession = MediaSession(this, "GaleriReels").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    onTogglePause?.invoke()
                }

                override fun onPause() {
                    onTogglePause?.invoke()
                }

                override fun onSkipToNext() {
                    onNext?.invoke()
                }

                override fun onStop() {
                    onStopPlayback?.invoke()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> onTogglePause?.invoke()
            ACTION_NEXT -> onNext?.invoke()
            ACTION_STOP -> {
                onStopPlayback?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_STOP,
            )
            .setState(
                if (currentPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1f,
            )
            .build()
        mediaSession?.setPlaybackState(state)
        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentSubtitle)
                .build(),
        )

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val toggleAction = Notification.Action.Builder(
            if (currentPaused) R.drawable.ic_play else R.drawable.ic_pause,
            if (currentPaused) "Oynat" else "Duraklat",
            servicePendingIntent(ACTION_TOGGLE_PAUSE, 1),
        ).build()

        val nextAction = Notification.Action.Builder(
            R.drawable.ic_skip_next,
            "Sonraki",
            servicePendingIntent(ACTION_NEXT, 2),
        ).build()

        val style = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setContentIntent(openIntent)
            .setdeleteIntent(servicePendingIntent(ACTION_STOP, 3))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(!currentPaused)
            .addAction(toggleAction)
            .addAction(nextAction)
            .setStyle(style)
            .build()
    }

    private fun Notification.Builder.setdeleteIntent(pi: PendingIntent): Notification.Builder =
        setDeleteIntent(pi)

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, BackgroundPlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Arka planda oynatma",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ekran kapalıyken ya da uygulamadan çıkınca oynatma kontrolleri"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "galerireels_playback"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_UPDATE = "com.celtikarda.galerireels.BG_UPDATE"
        private const val ACTION_TOGGLE_PAUSE = "com.celtikarda.galerireels.BG_TOGGLE_PAUSE"
        private const val ACTION_NEXT = "com.celtikarda.galerireels.BG_NEXT"
        private const val ACTION_STOP = "com.celtikarda.galerireels.BG_STOP"

        private var currentTitle: String = "Galeri Reels"
        private var currentSubtitle: String = ""
        private var currentPaused: Boolean = false

        private var onTogglePause: (() -> Unit)? = null
        private var onNext: (() -> Unit)? = null
        private var onStopPlayback: (() -> Unit)? = null

        fun startOrUpdate(
            context: Context,
            title: String,
            subtitle: String,
            paused: Boolean,
            onTogglePause: () -> Unit,
            onNext: () -> Unit,
            onStop: () -> Unit,
        ) {
            this.currentTitle = title
            this.currentSubtitle = subtitle
            this.currentPaused = paused
            this.onTogglePause = onTogglePause
            this.onNext = onNext
            this.onStopPlayback = onStop
            val intent = Intent(context, BackgroundPlayService::class.java).setAction(ACTION_UPDATE)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            onTogglePause = null
            onNext = null
            onStopPlayback = null
            runCatching { context.stopService(Intent(context, BackgroundPlayService::class.java)) }
        }
    }
}
