package xyz.rthqks.synapse.data

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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


    abstract class Text(
        key: Key<String>,
        default: String,
        @StringRes name: Int = 0,
        @DrawableRes icon: Int = 0
    ) : PropertyType<String>(key, default, name, icon) {
        override fun fromString(string: String): String = string
        override fun toString(value: String): String = value
    }

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

    abstract class DiscreteFloat(
        key: Key<Float>,
        default: Float,
        values: List<Float>,
        labels: List<Int>,
        @StringRes name: Int = 0,
        @DrawableRes icon: Int = 0
    ) : Discrete<Float>(
        key,
        default,
        values,
        labels,
        name,
        icon
    ) {
        override fun fromString(string: String): Float = string.toFloat()
        override fun toString(value: Float): String = value.toString()
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
        override fun fromString(string: String): Size = string.split(':').let {
            Size(it[0].toInt(), it[1].toInt())
        }

        override fun toString(value: Size): String = "${value.width}:${value.height}"
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
        listOf(Size(1920, 1080), Size(1280, 720), Size(640, 480)),
        listOf(
            R.string.property_label_camera_capture_size_1080,
            R.string.property_label_camera_capture_size_720,
            R.string.property_label_camera_capture_size_480
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

    object BlurSize : DiscreteInt(
        Key.BlurSize,
        9,
        listOf(5, 9, 13),
        listOf(
            R.string.property_label_blur_size_5,
            R.string.property_label_blur_size_9,
            R.string.property_label_blur_size_13
        ),
        R.string.property_name_blur_size,
        R.drawable.ic_blur
    )

    object NumPasses : DiscreteInt(
        Key.NumPasses,
        1,
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
        listOf(
            R.string.property_label_1,
            R.string.property_label_2,
            R.string.property_label_3,
            R.string.property_label_4,
            R.string.property_label_5,
            R.string.property_label_6,
            R.string.property_label_7,
            R.string.property_label_8,
            R.string.property_label_9,
            R.string.property_label_10
        ),
        R.string.property_name_num_passes,
        R.drawable.ic_blur
    )

    object ScaleFactor : DiscreteInt(
        Key.ScaleFactor,
        1,
        listOf(1, 2, 4, 8, 16),
        listOf(
            R.string.property_label_scale_factor_1,
            R.string.property_label_scale_factor_2,
            R.string.property_label_scale_factor_4,
            R.string.property_label_scale_factor_8,
            R.string.property_label_scale_factor_16
        ),
        R.string.property_name_scale_factor,
        R.drawable.ic_photo_size_select
    )

    object AspectRatio : DiscreteInt(
        Key.BlurSize,
        9,
        listOf(5, 9, 13),
        listOf(
            R.string.property_label_blur_size_5,
            R.string.property_label_blur_size_9,
            R.string.property_label_blur_size_13
        ),
        R.string.property_name_blur_size,
        R.drawable.ic_blur
    )

    object MultiplyFactor : DiscreteFloat(
        Key.MultiplyFactor,
        0.9f,
        listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
        listOf(
            R.string.property_label_float_zero,
            R.string.property_label_float_point_one,
            R.string.property_label_float_point_two,
            R.string.property_label_float_point_three,
            R.string.property_label_float_point_four,
            R.string.property_label_float_point_five,
            R.string.property_label_float_point_six,
            R.string.property_label_float_point_seven,
            R.string.property_label_float_point_eight,
            R.string.property_label_float_point_nine,
            R.string.property_label_float_one
        ),
        R.string.property_name_multiply_factor,
        R.drawable.ic_blur
    )

    object AccumulateFactor : DiscreteFloat(
        Key.AccumulateFactor,
        0.9f,
        listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
        listOf(
            R.string.property_label_float_zero,
            R.string.property_label_float_point_one,
            R.string.property_label_float_point_two,
            R.string.property_label_float_point_three,
            R.string.property_label_float_point_four,
            R.string.property_label_float_point_five,
            R.string.property_label_float_point_six,
            R.string.property_label_float_point_seven,
            R.string.property_label_float_point_eight,
            R.string.property_label_float_point_nine,
            R.string.property_label_float_one
        ),
        R.string.property_name_accumulate_factor,
        R.drawable.ic_blur
    )

    object Uri : Text(
        Key.Uri,
        "",
        R.string.property_name_uri,
        R.drawable.ic_blur)

    companion object {

        val map = mutableMapOf<Key<*>, PropertyType<*>>()

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
            Key.CameraFrameRate -> (value as? Int)?.let {
                CameraFrameRate.toString(it)
            }
            Key.BlurSize -> (value as? Int)?.let {
                BlurSize.toString(it)
            }
            Key.NumPasses -> (value as? Int)?.let {
                NumPasses.toString(it)
            }
            Key.ScaleFactor -> (value as? Int)?.let {
                ScaleFactor.toString(it)
            }
            Key.AccumulateFactor -> (value as? Float)?.let {
                AccumulateFactor.toString(it)
            }
            Key.MultiplyFactor -> (value as? Float)?.let {
                MultiplyFactor.toString(it)
            }
            Key.Uri -> value as? String
        } ?: error("can't convert $value with key $key")

//        fun <T : Any> foo(key: Key<T>, value: T): String = PropertyType[key].toString(value)
//        fun <T : Any> fromString(key: Key<T>, value: String): T = PropertyType[key].fromString(value)
    }
}
