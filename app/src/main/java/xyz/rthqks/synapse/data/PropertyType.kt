package xyz.rthqks.synapse.data

//abstract class Text(
//    key: Key<String>,
//    default: String,
//    @StringRes name: Int = 0,
//    @DrawableRes icon: Int = 0
//) : PropertyType<String>(key, default, name, icon) {
//    override fun fromString(string: String): String = string
//    override fun toString(value: String): String = value
//}
//
//abstract class Discrete<T>(
//    key: Key<T>,
//    default: T,
//    val values: List<T>,
//    val labels: List<Int>,
//    @StringRes name: Int = 0,
//    @DrawableRes icon: Int = 0
//) : PropertyType<T>(key, default, name, icon)
//
//class DiscreteInt(
//    key: Key<Int>,
//    default: Int,
//    values: List<Int>,
//    labels: List<Int>,
//    @StringRes name: Int = 0,
//    @DrawableRes icon: Int = 0
//) : Discrete<Int>(
//    key,
//    default,
//    values,
//    labels,
//    name,
//    icon
//) {
//    override fun fromString(string: String): Int = string.toInt()
//    override fun toString(value: Int): String = value.toString()
//}
//
//abstract class DiscreteFloat(
//    key: Key<Float>,
//    default: Float,
//    values: List<Float>,
//    labels: List<Int>,
//    @StringRes name: Int = 0,
//    @DrawableRes icon: Int = 0
//) : Discrete<Float>(
//    key,
//    default,
//    values,
//    labels,
//    name,
//    icon
//) {
//    override fun fromString(string: String): Float = string.toFloat()
//    override fun toString(value: Float): String = value.toString()
//}
//
//abstract class DiscreteSize(
//    key: Key<Size>,
//    default: Size,
//    values: List<Size>,
//    labels: List<Int>,
//    @StringRes name: Int = 0,
//    @DrawableRes icon: Int = 0
//) : Discrete<Size>(
//    key,
//    default,
//    values,
//    labels,
//    name,
//    icon
//) {
//    override fun fromString(string: String): Size = string.split(':').let {
//        Size(it[0].toInt(), it[1].toInt())
//    }
//
//    override fun toString(value: Size): String = "${value.width}:${value.height}"
//}

//object AudioSampleRate : DiscreteInt(
//    AudioSampleRate,
//    44100,
//    listOf(16000, 22050, 32000, 44100, 48000),
//    listOf(
//        R.string.property_label_sample_rate_16000,
//        R.string.property_label_sample_rate_22050,
//        R.string.property_label_sample_rate_32000,
//        R.string.property_label_sample_rate_44100,
//        R.string.property_label_sample_rate_48000
//    ),
//    R.string.property_name_sample_rate,
//    R.drawable.ic_equalizer
//)

//object AudioEncoding : DiscreteInt(
//    AudioEncoding,
//    AudioFormat.ENCODING_PCM_16BIT,
//    listOf(
//        AudioFormat.ENCODING_PCM_16BIT,
//        AudioFormat.ENCODING_PCM_8BIT,
//        AudioFormat.ENCODING_PCM_FLOAT
//    ),
//    listOf(
//        R.string.property_label_encoding_PCM_16BIT,
//        R.string.property_label_encoding_PCM_8BIT,
//        R.string.property_label_encoding_PCM_FLOAT
//    ),
//    R.string.property_name_audio_encoding,
//    R.drawable.ic_equalizer
//)

//object AudioChannel : DiscreteInt(
//    AudioChannel,
//    AudioFormat.CHANNEL_IN_DEFAULT,
//    listOf(
//        AudioFormat.CHANNEL_IN_DEFAULT,
//        AudioFormat.CHANNEL_IN_MONO,
//        AudioFormat.CHANNEL_IN_STEREO
//    ),
//    listOf(
//        R.string.property_label_channel_default,
//        R.string.property_label_channel_mono,
//        R.string.property_label_channel_stereo
//    ),
//    R.string.property_name_audio_channels,
//    R.drawable.ic_equalizer
//)

//object AudioSource : DiscreteInt(
//    AudioSource,
//    MediaRecorder.AudioSource.DEFAULT,
//    listOf(
//        MediaRecorder.AudioSource.DEFAULT,
//        MediaRecorder.AudioSource.MIC,
//        MediaRecorder.AudioSource.CAMCORDER,
//        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
//        MediaRecorder.AudioSource.VOICE_RECOGNITION
//    ),
//    listOf(
//        R.string.property_label_audio_source_default,
//        R.string.property_label_audio_source_mic,
//        R.string.property_label_audio_source_camcorder,
//        R.string.property_label_audio_source_voice_communication,
//        R.string.property_label_audio_source_voice_recognition
//    ),
//    R.string.property_name_audio_source,
//    R.drawable.ic_equalizer
//)

//object CameraFacing : DiscreteInt(
//    CameraFacing,
//    CameraCharacteristics.LENS_FACING_FRONT,
//    listOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK),
//    listOf(
//        R.string.property_label_camera_lens_facing_front,
//        R.string.property_label_camera_lens_facing_back
//    ),
//    R.string.property_name_camera_device,
//    R.drawable.ic_camera
//)

//object CameraCaptureSize : DiscreteSize(
//    CameraCaptureSize,
//    Size(1920, 1080),
//    listOf(Size(1920, 1080), Size(1280, 720), Size(640, 480)),
//    listOf(
//        R.string.property_label_camera_capture_size_1080,
//        R.string.property_label_camera_capture_size_720,
//        R.string.property_label_camera_capture_size_480
//    ),
//    R.string.property_name_camera_capture_size,
//    R.drawable.ic_camera
//)

//object CameraFrameRate : DiscreteInt(
//    CameraFrameRate,
//    30,
//    listOf(15, 20, 30, 60),
//    listOf(
//        R.string.property_label_camera_fps_15,
//        R.string.property_label_camera_fps_20,
//        R.string.property_label_camera_fps_30,
//        R.string.property_label_camera_fps_60
//    ),
//    R.string.property_name_camera_frame_rate,
//    R.drawable.ic_camera
//)

//object BlurSize : DiscreteInt(
//    BlurSize,
//    9,
//    listOf(5, 9, 13),
//    listOf(
//        R.string.property_label_blur_size_5,
//        R.string.property_label_blur_size_9,
//        R.string.property_label_blur_size_13
//    ),
//    R.string.property_name_blur_size,
//    R.drawable.ic_blur
//)

//object NumPasses : DiscreteInt(
//    NumPasses,
//    1,
//    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
//    listOf(
//        R.string.property_label_1,
//        R.string.property_label_2,
//        R.string.property_label_3,
//        R.string.property_label_4,
//        R.string.property_label_5,
//        R.string.property_label_6,
//        R.string.property_label_7,
//        R.string.property_label_8,
//        R.string.property_label_9,
//        R.string.property_label_10
//    ),
//    R.string.property_name_num_passes,
//    R.drawable.ic_blur
//)

//object ScaleFactor : DiscreteInt(
//    ScaleFactor,
//    1,
//    listOf(1, 2, 4, 8, 16),
//    listOf(
//        R.string.property_label_scale_factor_1,
//        R.string.property_label_scale_factor_2,
//        R.string.property_label_scale_factor_4,
//        R.string.property_label_scale_factor_8,
//        R.string.property_label_scale_factor_16
//    ),
//    R.string.property_name_scale_factor,
//    R.drawable.ic_photo_size_select
//)

//object AspectRatio : DiscreteInt(
//    BlurSize,
//    9,
//    listOf(5, 9, 13),
//    listOf(
//        R.string.property_label_blur_size_5,
//        R.string.property_label_blur_size_9,
//        R.string.property_label_blur_size_13
//    ),
//    R.string.property_name_blur_size,
//    R.drawable.ic_blur
//)

//object MultiplyFactor : DiscreteFloat(
//    MultiplyFactor,
//    0.9f,
//    listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
//    listOf(
//        R.string.property_label_float_zero,
//        R.string.property_label_float_point_one,
//        R.string.property_label_float_point_two,
//        R.string.property_label_float_point_three,
//        R.string.property_label_float_point_four,
//        R.string.property_label_float_point_five,
//        R.string.property_label_float_point_six,
//        R.string.property_label_float_point_seven,
//        R.string.property_label_float_point_eight,
//        R.string.property_label_float_point_nine,
//        R.string.property_label_float_one
//    ),
//    R.string.property_name_multiply_factor,
//    R.drawable.ic_blur
//)

//object AccumulateFactor : DiscreteFloat(
//    AccumulateFactor,
//    0.9f,
//    listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
//    listOf(
//        R.string.property_label_float_zero,
//        R.string.property_label_float_point_one,
//        R.string.property_label_float_point_two,
//        R.string.property_label_float_point_three,
//        R.string.property_label_float_point_four,
//        R.string.property_label_float_point_five,
//        R.string.property_label_float_point_six,
//        R.string.property_label_float_point_seven,
//        R.string.property_label_float_point_eight,
//        R.string.property_label_float_point_nine,
//        R.string.property_label_float_one
//    ),
//    R.string.property_name_accumulate_factor,
//    R.drawable.ic_blur
//)

//object Uri : Text(
//    Uri,
//    "",
//    R.string.property_name_uri,
//    R.drawable.ic_blur)
