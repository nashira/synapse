package xyz.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R
import kotlin.reflect.KProperty

class Properties {
    private val map = mutableMapOf<Property.Key<*>, Property<*>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> set(type: Property.Type<T>, value: T) {
        val property = map.getOrPut(type.key) {
            Property(value)
        } as Property<T>
        property.value = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(type: Property.Type<T>): T {
        return map[type.key]?.let {
            it.value as? T
        } ?: type.default
    }

    operator fun plus(other: Properties): Properties {
        return Properties().also {
            it.putAll(this)
            it.putAll(other)
        }
    }

    fun putAll(properties: Properties) {
        map.putAll(properties.map)
    }

    fun <T> delegate(type: Property.Type<T>, default: T) = Delegate(type, default)

    inner class Delegate<T>(
        private val type: Property.Type<T>,
        private val default: T
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return get(type) ?: default
        }
    }
}

class Property<T>(var value: T) {

    class Key<T>(val name: String)

    sealed class Type<T>(
        val key: Key<T>,
        val default: T,
        @StringRes val title: Int = 0,
        @DrawableRes val icon: Int = 0
    ) {

        object AudioSampleRate : Type<Int>(
            Key("audio_sample_rate"),
            48000,
            R.string.property_name_sample_rate,
            R.drawable.ic_equalizer
        )

        object AudioEncoding : Type<Int>(
            Key("audio_encoding"),
            AudioFormat.ENCODING_DEFAULT,
            R.string.property_name_audio_encoding,
            R.drawable.ic_equalizer
        )

        object AudioChannel : Type<Int>(
            Key("audio_channel"),
            R.string.property_name_audio_channels,
            AudioFormat.CHANNEL_IN_DEFAULT,
            R.drawable.ic_equalizer
        )

        object AudioSource : Type<Int>(
            Key<Int>("audio_source"),
            R.string.property_name_audio_source,
            MediaRecorder.AudioSource.DEFAULT,
            R.drawable.ic_equalizer
        )

        object CameraFacing : Type<Int>(
            Key("camera_facing"),
            R.string.property_name_camera_device,
            CameraCharacteristics.LENS_FACING_BACK,
            R.drawable.ic_camera
        )

        object CameraCaptureSize : Type<Size>(
            Key("camera_capture_size"),
            Size(1920, 1080),
            R.string.property_name_camera_capture_size,
            R.drawable.ic_camera
        )

        object CameraFrameRate : Type<Int>(
            Key("camera_frame_rate"),
            30,
            R.string.property_name_camera_frame_rate,
            R.drawable.ic_camera
        )

        object BlurSize : Type<Int>(
            Key("blur_size"),
            9,
            R.string.property_name_blur_size,
            R.drawable.ic_blur
        )

        object NumPasses : Type<Int>(
            Key("num_passes"),
            1,
            R.string.property_name_num_passes,
            R.drawable.ic_blur
        )

        object ScaleFactor : Type<Int>(
            Key("scale_factor"),
            1,
            R.string.property_name_scale_factor,
            R.drawable.ic_photo_size_select
        )

        object AccumulateFactor : Type<Float>(
            Key("accumulate_factor"),
            0.9f,
            R.string.property_name_accumulate_factor,
            R.drawable.ic_blur
        )

        object MultiplyFactor : Type<Float>(
            Key("multiply_factor"),
            1f,
            R.string.property_name_multiply_factor,
            R.drawable.ic_blur
        )

        object Uri : Type<String>(
            Key("uri"),
            "https://www.sample-videos.com/video123/mp4/720/big_buck_bunny_720p_1mb.mp4",
            R.string.property_name_uri,
            R.drawable.ic_blur
        )

        object ScreenCrop : Type<Boolean>(
            Key("screen_crop"),
            false,
            R.string.property_title_crop_to_fit,
            R.drawable.ic_crop
        )
    }
}