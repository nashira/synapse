package xyz.rthqks.synapse.data

import android.util.Size

@Suppress("LeakingThis")
sealed class Key<T>(
    val name: String
) {
    init {
        Key[name] = this
    }

    object AudioChannel : Key<Int>("audio_channel")
    object AudioEncoding : Key<Int>("audio_encoding")
    object AudioSampleRate : Key<Int>("audio_sample_rate")
    object AudioSource : Key<Int>("audio_source")

    object CameraFacing: Key<Int>("camera_facing")
    object CameraCaptureSize : Key<Size>("camera_capture_size")
    object CameraFrameRate : Key<Int>("camera_frame_rate")

    object BlurSize : Key<Int>("blur_size")
    object NumPasses : Key<Int>("num_passes")
    object ScaleFactor : Key<Int>("scale_factor")

    companion object {
        private val map = mutableMapOf<String, Key<*>>()
        operator fun get(name: String) = map[name]
        private operator fun set(name: String, key: Key<*>) {
            map[name] = key
        }
    }

    object AccumulateFactor : Key<Float>("accumulate_factor")
    object MultiplyFactor : Key<Float>("multiply_factor")
    object Uri : Key<String>("uri")
}