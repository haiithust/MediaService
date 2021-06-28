package ithust.hai.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import ithust.hai.player.extensions.isPlayEnabled
import ithust.hai.player.extensions.isPlaying
import ithust.hai.player.extensions.title

const val NOW_PLAYING_CHANNEL: String = "ithust.hai.player.NOW_PLAYING"
const val NOW_PLAYING_NOTIFICATION: Int = 0xb339

class PlayerNotificationBuilder(private val context: Context) {
    private val platformNotificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val skipToPreviousAction = NotificationCompat.Action(
        R.drawable.ic_player_previous,
        context.getString(R.string.exo_controls_previous_description),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_SKIP_TO_PREVIOUS)
    )

    private val skipToNextAction = NotificationCompat.Action(
        R.drawable.ic_player_next,
        context.getString(R.string.exo_controls_next_description),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_SKIP_TO_NEXT)
    )

    private val playAction = NotificationCompat.Action(
        R.drawable.ic_player_play,
        context.getString(R.string.exo_controls_play_description),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_PLAY_PAUSE)
    )
    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_player_pause,
        context.getString(R.string.exo_controls_pause_description),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_PLAY_PAUSE)
    )

    private val stopPendingIntent =
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_STOP)

    fun buildNotification(sessionToken: MediaSessionCompat.Token): Notification {
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        val controller = MediaControllerCompat(context, sessionToken)
        val playbackState = controller.playbackState

        val builder = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL)
            .setColor(Color.WHITE)

        builder.addAction(skipToPreviousAction)
        if (playbackState.isPlaying) {
            builder.addAction(pauseAction)
        } else if (playbackState.isPlayEnabled) {
            builder.addAction(playAction)
        }
        builder.addAction(skipToNextAction)
        builder.color = ContextCompat.getColor(context, R.color.colorPrimary)

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(1)
            .setMediaSession(sessionToken)
            .setCancelButtonIntent(stopPendingIntent)
            .setShowCancelButton(true)

        return builder.setContentIntent(controller.sessionActivity)
            .setContentTitle(controller.metadata.title)
            .setDeleteIntent(stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setColorized(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(mediaStyle)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_baby))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun shouldCreateNowPlayingChannel() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() =
        platformNotificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(
            NOW_PLAYING_CHANNEL,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel)
        }

        platformNotificationManager.createNotificationChannel(notificationChannel)
    }
}
