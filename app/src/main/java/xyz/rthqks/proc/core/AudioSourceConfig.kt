package xyz.rthqks.proc.core

import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.proc.R
import xyz.rthqks.proc.data.DiscreteProperty

data class AudioSourceConfig(
    val source: Int = SOURCE.default,
    val channelMask: Int = CHANNELS.default,
    val audioEncoding: Int = ENCODING.default,
    val sampleRate: Int = SAMPLE_RATE.default
) {
    companion object {
        val SAMPLE_RATE = DiscreteProperty(
            44100,
            R.string.property_name_sample_rate,
            R.drawable.ic_equalizer,
            listOf(16000, 22050, 32000, 44100, 48000),
            listOf(
                R.string.property_label_sample_rate_16000,
                R.string.property_label_sample_rate_22050,
                R.string.property_label_sample_rate_32000,
                R.string.property_label_sample_rate_44100,
                R.string.property_label_sample_rate_48000
            )
        )
        val ENCODING = DiscreteProperty(
            AudioFormat.ENCODING_PCM_16BIT,
            R.string.property_name_audio_encoding,
            R.drawable.ic_equalizer,
            listOf(
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_FLOAT
            ),
            listOf(
                R.string.property_label_encoding_PCM_16BIT,
                R.string.property_label_encoding_PCM_8BIT,
                R.string.property_label_encoding_PCM_FLOAT
            )
        )
        val CHANNELS = DiscreteProperty(
            AudioFormat.CHANNEL_IN_DEFAULT,
            R.string.property_name_audio_channels,
            R.drawable.ic_equalizer,
            listOf(
                AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.CHANNEL_IN_STEREO
            ),
            listOf(
                R.string.property_label_channel_default,
                R.string.property_label_channel_mono,
                R.string.property_label_channel_stereo
            )
        )
        val SOURCE = DiscreteProperty(
            MediaRecorder.AudioSource.DEFAULT,
            R.string.property_name_audio_channels,
            R.drawable.ic_equalizer,
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
            )
        )
    }
}