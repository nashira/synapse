package xyz.rthqks.synapse.data

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.gson.Gson
import xyz.rthqks.synapse.R

@Suppress("LeakingThis")
sealed class PropertyType<T : Any>(
    val key: Key<T>,
    val default: T,
    @StringRes val name: Int = 0,
    @DrawableRes val icon: Int = 0
) {
    init {
        PropertyType[key] = this
    }

    abstract fun fromString(string: String): T
    abstract fun toString(value: T): String

    abstract class Discrete<T : Any>(
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
        override fun toString(value: Int): String = value.toString()
    }

    abstract class DiscreteSize(
        key: Key<Size>,
        default: Size,
        values: List<Size>,
        labels: List<Int>,
        @StringRes name: Int = 0,
        @DrawableRes icon: Int = 0
    ) : Discrete<Size>(
        key,
        default,
        values,
        labels,
        name,
        icon
    ) {
        override fun fromString(string: String): Size = gson.fromJson(string, Size::class.java)
        override fun toString(value: Size): String = gson.toJson(value)
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

    object CameraFacing : DiscreteInt(
        Key.CameraFacing,
        CameraCharacteristics.LENS_FACING_FRONT,
        listOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK),
        listOf(
            R.string.property_label_camera_lens_facing_front,
            R.string.property_label_camera_lens_facing_back
        ),
        R.string.property_name_camera_device,
        R.drawable.ic_camera
    )

    object CameraCaptureSize : DiscreteSize(
        Key.CameraCaptureSize,
        Size(1920, 1080),
        listOf(Size(1920, 1080), Size(1280, 720)),
        listOf(
            R.string.property_label_camera_capture_size_1080,
            R.string.property_label_camera_capture_size_720
        ),
        R.string.property_name_camera_capture_size,
        R.drawable.ic_camera
    )

    object CameraFrameRate : DiscreteInt(
        Key.CameraFrameRate,
        30,
        listOf(15, 20, 30, 60),
        listOf(
            R.string.property_label_camera_fps_15,
            R.string.property_label_camera_fps_20,
            R.string.property_label_camera_fps_30,
            R.string.property_label_camera_fps_60
        ),
        R.string.property_name_camera_frame_rate,
        R.drawable.ic_camera
    )

    companion object {
        private val map = mutableMapOf<Key<*>, PropertyType<*>>()
        val gson = Gson()

        operator fun <E : Any> get(key: Key<E>): PropertyType<E> {
            val p = map[key]
            @Suppress("UNCHECKED_CAST")
            return p as PropertyType<E>
        }

        operator fun <E : Any> set(key: Key<E>, value: PropertyType<E>) {
            @Suppress("UNCHECKED_CAST")
            map[key] = value as PropertyType<*>
        }

        fun toString(key: Key<*>, value: Any): String = when (key) {
            Key.AudioChannel -> (value as? Int)?.let {
                AudioChannel.toString(it)
            }
            Key.AudioEncoding -> (value as? Int)?.let {
                AudioEncoding.toString(it)
            }
            Key.AudioSampleRate -> (value as? Int)?.let {
                AudioSampleRate.toString(it)
            }
            Key.AudioSource -> (value as? Int)?.let {
                AudioSource.toString(it)
            }
            Key.CameraFacing -> (value as? Int)?.let {
                CameraFacing.toString(it)
            }
            Key.CameraCaptureSize -> (value as? Size)?.let {
                CameraCaptureSize.toString(it)
            }
            Key.CameraFrameRate ->  (value as? Int)?.let {
                CameraFrameRate.toString(it)
            }
        } ?: error("can't convert $value with key $key")

//        fun <T : Any> foo(key: Key<T>, value: T): String = PropertyType[key].toString(value)
//        fun <T : Any> fromString(key: Key<T>, value: String): T = PropertyType[key].fromString(value)
    }
}
