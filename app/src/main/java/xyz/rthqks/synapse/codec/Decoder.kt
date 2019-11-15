package xyz.rthqks.synapse.codec

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import java.nio.ByteBuffer
import java.util.*


class Decoder(
    private val handler: Handler
) : MediaCodec.Callback() {

    var size: Size = Size(0, 0)
    var surfaceRotation: Int = 0
    var outputVideoFormat: MediaFormat? = null
    var outputAudioFormat: MediaFormat? = null

    private val extractor = MediaExtractor()
    private var videoDecoder: MediaCodec? = null
    private var audioDecoder: MediaCodec? = null
    private var inputVideoFormat = MediaFormat()
    private var inputAudioFormat = MediaFormat()
    private var videoTrack = -1
    private var audioTrack = -1
    private var hasVideo = false
    private var hasAudio = false
    private var running = false
    private var videoConfigured = false
    private val videoInputBuffers = LinkedList<Int>()
    private val audioInputBuffers = LinkedList<Int>()

    private var audioAvailable: ((index: Int, buffer: ByteBuffer?, MediaCodec.BufferInfo) -> Unit)? =
        null
    private var videoAvailable: ((index: Int, MediaCodec.BufferInfo) -> Unit)? = null

    fun setDataSource(uri: String) {
        Log.d(TAG, "setDataSource $uri")
        extractor.setDataSource(uri, emptyMap())

        findTrack("video/")?.let {
            videoTrack = it.first
            inputVideoFormat = it.second
            inputVideoFormat.setInteger("allow-frame-drop", 0)
            extractor.selectTrack(videoTrack)
            hasVideo = true
            videoDecoder =
                MediaCodec.createDecoderByType(inputVideoFormat.getString(MediaFormat.KEY_MIME)!!)
            videoDecoder?.setCallback(this)
        }

        findTrack("audio/")?.let {
            audioTrack = it.first
            inputAudioFormat = it.second
            extractor.selectTrack(audioTrack)
            hasAudio = true
            audioDecoder =
                MediaCodec.createDecoderByType(inputAudioFormat.getString(MediaFormat.KEY_MIME)!!)
            audioDecoder?.setCallback(this)
            audioDecoder?.configure(inputAudioFormat, null, null, 0)
        }

        val width = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        if (inputVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            surfaceRotation = inputVideoFormat.getInteger(MediaFormat.KEY_ROTATION)
        }
        size = Size(width, height)
    }

    fun start(
        surface: Surface?,
        videoAvailable: (index: Int, MediaCodec.BufferInfo) -> Unit,
        audioAvailable: (index: Int, buffer: ByteBuffer?, MediaCodec.BufferInfo) -> Unit
    ) {
        this.videoAvailable = videoAvailable
        this.audioAvailable = audioAvailable
        running = true

        if (videoConfigured) {
            videoDecoder?.setOutputSurface(surface!!)
        } else {
            videoConfigured = true
            videoDecoder?.configure(inputVideoFormat, surface, null, 0)
        }
        videoDecoder?.start()
        audioDecoder?.start()
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        when (codec) {
            audioDecoder -> {
                val buffer = if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                    codec.getOutputBuffer(index)
                } else {
                    null
                }
                audioAvailable?.invoke(index, buffer, info)
            }
            videoDecoder -> {
                videoAvailable?.invoke(index, info)
            }
        }
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        when (codec) {
            audioDecoder -> {
//                Log.d(TAG, "audio onInputBufferAvailable $index")
                audioInputBuffers.add(index)
            }
            videoDecoder -> {
//                Log.d(TAG, "video onInputBufferAvailable $index")
                videoInputBuffers.add(index)
            }
        }

        tryExtract()
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        when (codec) {
            audioDecoder -> {
                outputAudioFormat = format
                Log.d(TAG, "audio format: $format")
            }
            videoDecoder -> {
                outputVideoFormat = format
                Log.d(TAG, "video format: $format")
            }
        }
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        when (codec) {
            audioDecoder -> {

            }
            videoDecoder -> {

            }
        }
    }

    private fun tryExtract() {
        var remaining = true
        while (running && remaining) {
            val track = extractor.sampleTrackIndex
//            Log.d(TAG, "track $track")
            when {
                track == videoTrack && videoInputBuffers.isNotEmpty() -> {
                    val index = videoInputBuffers.remove()
                    videoDecoder?.let {
                        val buffer = it.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
//                        Log.d(TAG, "video size $size flags ${extractor.sampleFlags}")
                        it.queueInputBuffer(
                            index,
                            0,
                            size,
                            extractor.sampleTime,
                            extractor.sampleFlags
                        )
                    }
                    remaining = extractor.advance()
                }

                track == audioTrack && audioInputBuffers.isNotEmpty() -> {
                    val index = audioInputBuffers.remove()
                    audioDecoder?.let {
                        val buffer = it.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
//                        Log.d(TAG, "audio size $size flags ${extractor.sampleFlags}")
                        it.queueInputBuffer(
                            index,
                            0,
                            size,
                            extractor.sampleTime,
                            extractor.sampleFlags
                        )
                    }
                    remaining = extractor.advance()
                }

                track == -1 && videoInputBuffers.isNotEmpty()
                        && audioInputBuffers.isNotEmpty() -> {
                    Log.d(
                        TAG,
                        "audio inputs ${audioInputBuffers.size} video ${videoInputBuffers.size}"
                    )
                    Log.d(TAG, "flags ${extractor.sampleFlags}")
                    val videoIndex = videoInputBuffers.remove()
                    videoDecoder?.queueInputBuffer(
                        videoIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )

                    val index = audioInputBuffers.remove()
                    audioDecoder?.queueInputBuffer(
                        index,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    running = false
                }
                else -> {
                    remaining = false
                }
            }
        }
    }

    fun releaseAudioBuffer(index: Int, eos: Boolean) {
        if (eos) {
            audioInputBuffers.clear()
            audioDecoder?.flush()
        } else {
            audioDecoder?.releaseOutputBuffer(index, true)
        }
    }

    fun releaseVideoBuffer(index: Int, eos: Boolean) {
        if (eos) {
            videoInputBuffers.clear()
            videoDecoder?.flush()
        } else {
            videoDecoder?.releaseOutputBuffer(index, true)
        }
    }

    fun stop() {
        running = false
        videoDecoder?.stop()
        audioDecoder?.stop()
    }

    fun release() {
        extractor.release()
        audioDecoder?.release()
        videoDecoder?.release()
    }

    private fun findTrack(prefix: String): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            if (mime.startsWith(prefix)) {
                return Pair(i, format)
            }
        }
        return null
    }

    companion object {
        const val TAG = "Decoder"
    }
}