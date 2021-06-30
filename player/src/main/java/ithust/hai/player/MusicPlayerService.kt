package ithust.hai.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import ithust.hai.player.extensions.id
import ithust.hai.player.extensions.uriString
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * @author conghai on 4/7/20.
 */
const val EXTRA_MEDIA_DATA = "media_data"

abstract class MusicPlayerService : MediaBrowserServiceCompat() {
    private val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .build()

    private val player: SimpleExoPlayer by lazy(LazyThreadSafetyMode.NONE) {
        SimpleExoPlayer.Builder(this)
            .build().apply {
                addListener(listener)
            }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                if (!isSinglePlayAudio) {
                    updatePlaybackState(
                        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                        PLAY_ACTION_SUPPORT
                    )
                    skip(true)
                } else {
                    updatePlaybackState(PlaybackStateCompat.STATE_NONE, PLAY_ACTION_SUPPORT)
                }
            }
        }

        override fun onPositionDiscontinuity(reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, PLAY_ACTION_SUPPORT)
            }
        }
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Timber.d("onPlayFromMediaId")
            play(mediaId!!)
        }

        override fun onSkipToNext() {
            Timber.d("onSkipToNext")
            skip(true)
        }

        override fun onSkipToPrevious() {
            Timber.d("onSkipToPrevious")
            skip(false)
        }

        override fun onPlay() {
            Timber.d("onPlay")
            play()
        }

        override fun onPause() {
            Timber.d("onPause")
            pause()
        }

        override fun onStop() {
            Timber.d("onStop")
            stop()
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            Timber.d("onSetRepeatMode")
            repeat(repeatMode)
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            player.playbackParameters = PlaybackParameters(speed)
        }
    }

    protected lateinit var mediaSession: MediaSessionCompat
    private lateinit var extractor: ProgressiveMediaSource.Factory
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: PlayerNotificationBuilder

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isForegroundService = false
    private val mediaItems = mutableListOf<MediaMetadataCompat>()

    override fun onCreate() {
        super.onCreate()
        val userAgent = Util.getUserAgent(this, this::class.java.simpleName)
        extractor = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this, userAgent))
        val sessionActivityPendingIntent =
            PendingIntent.getActivity(this, 0, Intent(this, Class.forName(rootActivity)), 0)

        mediaSession = MediaSessionCompat(baseContext, this::class.java.simpleName).apply {
            setSessionActivity(sessionActivityPendingIntent)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    ).build()
            )
            setCallback(callback)
            setSessionToken(sessionToken)
        }
        mediaController = MediaControllerCompat(this, mediaSession).also {
            it.registerCallback(MediaControllerCallback())
        }

        notificationBuilder = PlayerNotificationBuilder(this, isSinglePlayAudio)
        notificationManager = NotificationManagerCompat.from(this)
        mediaItems.addAll(source.getMusicList())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        stop()
        serviceScope.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(
            mediaItems.map {
                MediaBrowserCompat.MediaItem(
                    it.description,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            }
                .toMutableList())
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        rootHints?.getParcelable<MediaMetadataCompat>(EXTRA_MEDIA_DATA)?.let {
            mediaItems.clear()
            mediaItems.add(it)
        }
        return BrowserRoot(rootId, null)
    }

    private fun play(mediaId: String) {
        val uri = Uri.parse(mediaItems.first { it.id == mediaId }.uriString)
        serviceScope.launch {
            if (source.sourceReady(uri)) {
                prepare(uri, mediaId)
            }
        }
    }

    private fun prepare(uri: Uri, id: String) {
        player.apply {
            setAudioAttributes(attrs, true)
            setMediaSource(extractor.createMediaSource(MediaItem.Builder().setUri(uri).build()))
            prepare()
            mediaSession.setMetadata(mediaItems.find { it.id == id })
        }
        play()
    }

    private fun play() {
        player.apply {
            playWhenReady = true
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, PLAY_ACTION_SUPPORT)
            mediaSession.isActive = true
        }
    }

    private fun pause() {
        player.apply {
            playWhenReady = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, PAUSE_ACTION_SUPPORT)
        }
    }

    private fun skip(isNext: Boolean) {
        val currentId = mediaController.metadata.id
        var currentIndex = mediaItems.indexOfFirst { it.id == currentId }
        if (isNext) currentIndex++ else currentIndex--
        currentIndex = currentIndex.coerceAtLeast(0)
        play(mediaItems[currentIndex.rem(mediaItems.size)].id)
    }

    private fun stop() {
        player.playWhenReady = false
        mediaSession.setMetadata(NOTHING_PLAYING)
        updatePlaybackState(
            PlaybackStateCompat.STATE_NONE,
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        )
        mediaSession.isActive = false
    }

    private fun repeat(@PlaybackStateCompat.RepeatMode mode: Int) {
        mediaSession.setRepeatMode(mode)
        player.repeatMode =
            if (mode == PlaybackStateCompat.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun updatePlaybackState(@PlaybackStateCompat.State state: Int, actions: Long) {
        // You need to change the state because the action taken in the controller depends on the state !!!
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    state, player.currentPosition, player.playbackParameters.speed
                ).build()
        )
    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mediaController.playbackState?.let { state ->
                updateNotification(state)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let {
                updateNotification(it)
            }
        }

        private fun updateNotification(state: PlaybackStateCompat) {
            val notification =
                if (mediaController.metadata != null && state.state != PlaybackStateCompat.STATE_NONE) {
                    notificationBuilder.buildNotification(mediaSession.sessionToken)
                } else {
                    null
                }

            when (state.state) {
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_PLAYING -> {
                    if (notification != null) {
                        notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)

                        if (!isForegroundService) {
                            ContextCompat.startForegroundService(
                                applicationContext,
                                Intent(applicationContext, this@MusicPlayerService.javaClass)
                            )
                            startForeground(NOW_PLAYING_NOTIFICATION, notification)
                            isForegroundService = true
                        }
                    }
                }
                else -> {
                    if (isForegroundService) {
                        isForegroundService = false

                        if (notification != null) {
                            stopForeground(false)
                            notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)
                        } else {
                            stopForeground(true)
                        }
                    }
                    if (state.state == PlaybackStateCompat.STATE_NONE) {
                        stopSelf()
                    }
                }
            }
        }
    }

    abstract val source: MusicSource
    abstract val rootId: String
    abstract val rootActivity: String
    protected open val isSinglePlayAudio: Boolean = false
}