package ithust.hai.player

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import ithust.hai.player.extensions.buildDuration
import ithust.hai.player.extensions.buildId

/**
 * @author conghai on 4/20/20.
 */
const val PLAY_ACTION_SUPPORT = PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_SEEK_TO or
        PlaybackStateCompat.ACTION_STOP

const val PAUSE_ACTION_SUPPORT = PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_STOP

val NOTHING_PLAYING: MediaMetadataCompat = MediaMetadataCompat.Builder().apply {
    buildId = ""
    buildDuration = 0
}.build()

val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = PlaybackStateCompat.Builder()
    .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
    .build()