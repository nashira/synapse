package xyz.rthqks.proc.data

import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.proc.R

sealed class Properties {
    object Audio : Properties() {
        val SAMPLE_RATE = DiscreteProperty(
            "audio_sample_rate",
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
        val ENCODING = DiscreteProperty(
            "audio_encoding",
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
        val CHANNELS = DiscreteProperty(
            "audio_channel",
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
        val SOURCE = DiscreteProperty(
            "audio_source",
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
            R.string.property_name_audio_channels,
            R.drawable.ic_equalizer
        )
    }
}

abstract class Property<out T>(
    val key: String,
    val default: T,
    @StringRes val name: Int = 0,
    @DrawableRes val icon: Int = 0
)

class DiscreteProperty<out T>(
    key: String,
    default: T,
    val values: List<T>,
    val labels: List<Int>,
    @StringRes name: Int = 0,
    @DrawableRes icon: Int = 0
) : Property<T>(key, default, name, icon)