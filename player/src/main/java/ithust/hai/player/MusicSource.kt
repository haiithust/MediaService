package ithust.hai.player

import android.net.Uri
import android.support.v4.media.MediaMetadataCompat

/**
 * @author conghai on 4/16/20.
 */
interface MusicSource {
    fun getMusicList(): List<MediaMetadataCompat>
    suspend fun sourceReady(uri: Uri): Boolean
}