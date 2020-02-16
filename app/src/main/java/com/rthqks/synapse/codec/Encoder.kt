package com.rthqks.synapse.codec

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.util.*


class Encoder(
    private val context: Context,
    private val handler: Handler
) : MediaCodec.Callback() {

    private var size: Size = Size(0, 0)
    private var fps = 0
    private var rotation = 0

    var outputVideoFormat: MediaFormat? = null
    var outputAudioFormat: MediaFormat? = null
    var hasVideo = false
    var hasAudio = false

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var inputAudioFormat: AudioFormat? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var videoConfigured = false
    private val videoInputBuffers = LinkedList<Int>()
    private val audioInputBuffers = LinkedList<Int>()
    private var extractState = STOPPED
    private var releaseState = EOS
    private var eventIndex = 0
    private val events = Array(100) {
        Event(0, BUFFER_INFO, null)
    }

    private var videoChannel: SendChannel<Event>? = null
    private var audioChannel: SendChannel<Event>? = null

    fun setVideo(size: Size, fps: Int, rotation: Int) {
        hasVideo = true
        this.size = size
        this.fps = fps
        this.rotation = rotation
    }

    fun setAudio(audioFormat: AudioFormat) {
        hasAudio = true
        inputAudioFormat = audioFormat
    }

    fun createSurface(): Surface? {
        return videoEncoder?.createInputSurface()
    }

    suspend fun startEncoding(fileName: String) {
        Log.d(TAG, "startEncoding $fileName")
        mediaMuxer = MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder?.setCallback(this, handler)

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder?.setCallback(this, handler)
        audioEncoder?.configure(outputAudioFormat, null, null, 0)
    }


    fun start(
        surface: Surface?,
        videoChannel: SendChannel<Event>?,
        audioChannel: SendChannel<Event>?
    ) {
        this.videoChannel = videoChannel
        this.audioChannel = audioChannel

        if ((videoChannel == null || !hasVideo)
            && (audioChannel == null || !hasAudio)
        ) {
            Log.w(TAG, "nothing to decode")
            return
        }

        extractState = RUNNING
//
//        if (videoConfigured) {
//            videoEncoder?.setOutputSurface(surface!!)
//        } else {
//            videoConfigured = true
//            videoEncoder?.configure(inputVideoFormat, surface, null, 0)
//        }
//
//        if (videoChannel == null) {
//            videoEncoder?.let {
//                extractor.unselectTrack(videoTrack)
//            }
//        } else {
//            videoEncoder?.let {
//                extractor.selectTrack(videoTrack)
//            }
//            videoEncoder?.start()
//        }
//        if (audioChannel == null) {
//            audioEncoder?.let {
//                extractor.unselectTrack(audioTrack)
//            }
//        } else {
//            audioEncoder?.let {
//                extractor.selectTrack(audioTrack)
//            }
//            audioEncoder?.start()
//        }
    }

    private fun nextEvent(): Event {
        eventIndex = (eventIndex + 1) % events.size
        return events[eventIndex]
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        when (codec) {
            audioEncoder -> {
                val buffer = if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                    codec.getOutputBuffer(index)
                } else {
                    null
                }
                val event = nextEvent()
//                Log.d(TAG, "audio $event")
                event.set(index, info, buffer)
                runBlocking {
                    audioChannel?.send(event)
                }
            }
            videoEncoder -> {
                val event = nextEvent()
//                Log.d(TAG, "video $event")
                event.set(index, info, null)
                runBlocking {
                    videoChannel?.send(event)
                }
            }
        }
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        when (codec) {
            audioEncoder -> {
//                Log.d(TAG, "audio onInputBufferAvailable $index")
                audioInputBuffers.add(index)
            }
            videoEncoder -> {
//                Log.d(TAG, "video onInputBufferAvailable $index")
                videoInputBuffers.add(index)
            }
        }

//        tryExtract()
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        when (codec) {
            audioEncoder -> {
                outputAudioFormat = format
                Log.d(TAG, "audio format: $format")
                if (releaseState == DECODING_FORMAT) {
                    extractState = STOPPING
                }
            }
            videoEncoder -> {
                outputVideoFormat = format
                Log.d(TAG, "video format: $format")
            }
        }
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        when (codec) {
            audioEncoder -> {

            }
            videoEncoder -> {

            }
        }
    }


    fun releaseAudioBuffer(index: Int, eos: Boolean) {
//        Log.d(TAG, "release audio buffer $index $eos")
        when {
            !eos -> {
                audioEncoder?.releaseOutputBuffer(index, false)
            }
            releaseState == DECODING_FORMAT
                    || releaseState == PAUSE
                    || releaseState == EOS -> {
                audioInputBuffers.clear()
                audioEncoder?.flush()
            }
        }
    }

    fun releaseVideoBuffer(index: Int, eos: Boolean) {
        when {
            !eos -> {
                videoEncoder?.releaseOutputBuffer(index, true)
            }
            releaseState == PAUSE
                    || releaseState == EOS -> {
                videoInputBuffers.clear()
                videoEncoder?.flush()
            }
        }
    }

    fun stop() {
        releaseState = PAUSE
        extractState = STOPPING
    }

    fun release() {
        mediaMuxer?.release()
        audioEncoder?.release()
        videoEncoder?.release()
    }


    class Event(
        var index: Int,
        var info: MediaCodec.BufferInfo,
        var buffer: ByteBuffer?
    ) {
        fun set(
            index: Int,
            info: MediaCodec.BufferInfo,
            buffer: ByteBuffer?
        ) {
            this.index = index
            this.info = info
            this.buffer = buffer
        }
    }

    companion object {
        const val TAG = "Decoder"
        val BUFFER_INFO = MediaCodec.BufferInfo()
        const val DECODING_FORMAT = 0
        const val PAUSE = 1
        const val EOS = 2

        const val RUNNING = 0
        const val STOPPING = 1
        const val STOPPED = 2
    }
}