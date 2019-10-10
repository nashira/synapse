package xyz.rthqks.synapse.data

import android.media.AudioFormat
import android.media.MediaRecorder
import xyz.rthqks.synapse.R

object PropertyType {
    val AUDIO_SAMPLE_RATE = DiscreteProperty(
        "audio_sample_rate",
        ValueType.Int,
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

    val AUDIO_ENCODING = DiscreteProperty(
        "audio_encoding",
        ValueType.Int,
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

    val AUDIO_CHANNEL = DiscreteProperty(
        "audio_channel",
        ValueType.Int,
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

    val AUDIO_SOURCE = DiscreteProperty(
        "audio_source",
        ValueType.Int,
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

    private val map = mapOf<String, Property>(
        AUDIO_SAMPLE_RATE.key to AUDIO_SAMPLE_RATE,
        AUDIO_ENCODING.key to AUDIO_ENCODING,
        AUDIO_CHANNEL.key to AUDIO_CHANNEL,
        AUDIO_SOURCE.key to AUDIO_SOURCE
    )

    operator fun get(string: String): Property? = map[string]
}