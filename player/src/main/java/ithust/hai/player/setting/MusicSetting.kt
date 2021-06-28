package ithust.hai.player.setting

import android.content.Context

/**
 * @author conghai on 4/21/20.
 */
class MusicSetting(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(MUSIC_SETTING_PREFERENCE, Context.MODE_PRIVATE)

    var isRepeatOne: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_REPEAT_ONE, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_IS_REPEAT_ONE, value).apply()
        }

    companion object {
        private const val MUSIC_SETTING_PREFERENCE = "music_setting"
        private const val KEY_IS_REPEAT_ONE = "a"

        @Volatile
        private var instance: MusicSetting? = null

        fun getInstance(context: Context): MusicSetting {
            return instance ?: synchronized(this) {
                instance ?: MusicSetting(context).also {
                    instance = it
                }
            }
        }
    }
}