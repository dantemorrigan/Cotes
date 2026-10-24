package io.github.dantemorrigan.cotes

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes
import java.util.concurrent.ConcurrentHashMap

/** Короткие звуки действий. Громкость у каждого своя: частые — тише, редкие — чуть заметнее. */
enum class Sound(@RawRes val res: Int, val volume: Float) {
    Create(R.raw.snd_create, 0.45f),
    Pin(R.raw.snd_pin, 0.45f),
    Delete(R.raw.snd_delete, 0.4f),
    Restore(R.raw.snd_restore, 0.35f),
    SyncDone(R.raw.snd_sync_done, 0.3f),
    SignedIn(R.raw.snd_signed_in, 0.4f),
    SignOut(R.raw.snd_sign_out, 0.35f),
}

/**
 * Звучат только осознанные действия: создание, закрепление, удаление и его отмена, вход и выход,
 * ручная синхронизация. Набор текста, открытие заметок, прокрутка и фоновая синхронизация — тихие.
 * Молчат, если звуки выключены в настройках или телефон в беззвучном режиме.
 */
class Sounds private constructor(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val settings = SettingsRepository.get(context)
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val ids: Map<Sound, Int>

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        ids = Sound.entries.associateWith { pool.load(context, it.res, 1) }
    }

    fun play(sound: Sound) {
        if (!settings.settings.value.sounds) return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[sound]?.takeIf { it in loaded } ?: return
        pool.play(id, sound.volume, sound.volume, 1, 0, 1f)
    }

    companion object {
        @Volatile
        private var instance: Sounds? = null

        fun get(context: Context): Sounds =
            instance ?: synchronized(this) {
                instance ?: Sounds(context.applicationContext).also { instance = it }
            }
    }
}
