package xyz.rthqks.synapse.data

import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

@Suppress("LeakingThis")
sealed class PropertyType<T>(
    val key: Key<T>,
    val default: T,
    @StringRes val name: Int = 0,
    @DrawableRes val icon: Int = 0
) {
    init {
        PropertyType[key] = this
    }

    abstract fun fromString(string: String): T

    abstract class Discrete<T>(
        key: Key<T>,
        default: T,
        val values: List<T>,
        val labels: List<Int>,
        @StringRes name: Int = 0,
        @DrawableRes icon: Int = 0
    ) : PropertyType<T>(key, default, name, icon)

    abstract class DiscreteInt(
        key: Key<Int>,
        default: Int,
        values: List<Int>,
        labels: List<Int>,
        @StringRes name: Int = 0,
        @DrawableRes icon: Int = 0
    ) : Discrete<Int>(
        key,
        default,
        values,
        labels,
        name,
        icon
    ) {
        override fun fromString(string: String): Int = string.toInt()
    }

    object AudioSampleRate : DiscreteInt(
        Key.AudioSampleRate,
        44100,
        listOf(16000, 22050, 32000, 44100, 48000),
        listOf(
            R.string.property_label_sample_rate_16000,
            R.string.property_label_sample_rate_22050,
            R.string.property_label_sample_rate_32000,
            R.string.property_label_sample_rate_44100,
            R.string.property_label_sample_rate_48000
        ),
        R.string.property_name_sample_rate,
        R.drawable.ic_equalizer
    )

    object AudioEncoding : DiscreteInt(
        Key.AudioEncoding,
        AudioFormat.ENCODING_PCM_16BIT,
        listOf(
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT
        ),
        listOf(
            R.string.property_label_encoding_PCM_16BIT,
            R.string.property_label_encoding_PCM_8BIT,
            R.string.property_label_encoding_PCM_FLOAT
        ),
        R.string.property_name_audio_encoding,
        R.drawable.ic_equalizer
    )

    object AudioChannel : DiscreteInt(
        Key.AudioChannel,
        AudioFormat.CHANNEL_IN_DEFAULT,
        listOf(
            AudioFormat.CHANNEL_IN_DEFAULT,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.CHANNEL_IN_STEREO
        ),
        listOf(
            R.string.property_label_channel_default,
            R.string.property_label_channel_mono,
            R.string.property_label_channel_stereo
        ),
        R.string.property_name_audio_channels,
        R.drawable.ic_equalizer
    )

    object AudioSource : DiscreteInt(
        Key.AudioSource,
        MediaRecorder.AudioSource.DEFAULT,
        listOf(
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        ),
        listOf(
            R.string.property_label_audio_source_default,
            R.string.property_label_audio_source_mic,
            R.string.property_label_audio_source_camcorder,
            R.string.property_label_audio_source_voice_communication,
            R.string.property_label_audio_source_voice_recognition
        ),
        R.string.property_name_audio_source,
        R.drawable.ic_equalizer
    )

    companion object {
        private val map = mutableMapOf<Key<*>, PropertyType<*>>()

        operator fun <E> get(key: Key<E>): PropertyType<E>? {
            val p = map[key]
            @Suppress("UNCHECKED_CAST")
            return p as PropertyType<E>?
        }

        operator fun <E> set(key: Key<E>, value: PropertyType<E>?) {
            @Suppress("UNCHECKED_CAST")
            map[key] = value as PropertyType<*>
        }
    }
}
