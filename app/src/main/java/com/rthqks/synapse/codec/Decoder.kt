package com.rthqks.synapse.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.util.*


class Decoder(
    private val context: Context,
    private val handler: Handler
) : MediaCodec.Callback() {

    var size: Size = Size(0, 0)
    var surfaceRotation: Int = 0
    var outputVideoFormat: MediaFormat? = null
    var outputAudioFormat: MediaFormat? = null
    var hasVideo = false
    var hasAudio = false

    private val extractor = MediaExtractor()
    private var videoDecoder: MediaCodec? = null
    private var audioDecoder: MediaCodec? = null
    private var inputVideoFormat = MediaFormat()
    private var inputAudioFormat = MediaFormat()
    private var videoTrack = -1
    private var audioTrack = -1
    private var videoConfigured = false
    private val videoInputBuffers = LinkedList<Int>()
    private val audioInputBuffers = LinkedList<Int>()
    private var extractState = STOPPED
    private var releaseState = EOS
    private var eventIndex = 0
    private val events = Array(50) {
        Event(0, MediaCodec.BufferInfo(), null)
    }

    private var videoChannel: SendChannel<Event>? = null
    private var audioChannel: SendChannel<Event>? = null

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun setDataSource(uri: Uri) {
        Log.d(TAG, "setDataSource $uri")
        when (uri.scheme) {
            "content" -> {
                val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                val fd = afd!!.fileDescriptor
                extractor.setDataSource(fd!!)
                afd.close()
            }
            "assets" -> {
                val afd = context.assets.openFd(uri.path!!)
                val fd = afd.fileDescriptor
                extractor.setDataSource(fd!!)
                afd.close()
            }
            else -> {
                extractor.setDataSource(context, uri, emptyMap())
            }
        }

        findTrack("video/")?.let {
            videoTrack = it.first
            inputVideoFormat = it.second
            inputVideoFormat.setInteger("allow-frame-drop", 0)
            extractor.selectTrack(videoTrack)
            hasVideo = true
            videoDecoder =
                MediaCodec.createDecoderByType(inputVideoFormat.getString(MediaFormat.KEY_MIME)!!)
            videoDecoder?.setCallback(this, handler)
        }

        findTrack("audio/")?.let {
            audioTrack = it.first
            inputAudioFormat = it.second
            extractor.selectTrack(audioTrack)
            hasAudio = true
            audioDecoder =
                MediaCodec.createDecoderByType(inputAudioFormat.getString(MediaFormat.KEY_MIME)!!)
            audioDecoder?.setCallback(this, handler)
            audioDecoder?.configure(inputAudioFormat, null, null, 0)
        }

        val width = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        if (inputVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            surfaceRotation = inputVideoFormat.getInteger(MediaFormat.KEY_ROTATION)
        }
        size = Size(width, height)

        decodeOutputFormats()
    }

    private suspend fun decodeOutputFormats() {
        audioDecoder ?: return

        videoDecoder?.let {
            extractor.unselectTrack(videoTrack)
        }

        val channel = Channel<Event>(Channel.UNLIMITED)
        audioChannel = channel

        extractState = RUNNING
        releaseState = DECODING_FORMAT

        audioDecoder?.start()

        do {
            val event = channel.receive()
//            Log.d(TAG, "received $event")
            val eos = event.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            releaseAudioBuffer(event.index, eos)
        } while (!eos)
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

        if (videoConfigured) {
            videoDecoder?.setOutputSurface(surface!!)
        } else {
            videoConfigured = true
            videoDecoder?.configure(inputVideoFormat, surface, null, 0)
        }

        if (videoChannel == null) {
            videoDecoder?.let {
                extractor.unselectTrack(videoTrack)
            }
        } else {
            videoDecoder?.let {
                extractor.selectTrack(videoTrack)
            }
            videoDecoder?.start()
        }
        if (audioChannel == null) {
            audioDecoder?.let {
                extractor.unselectTrack(audioTrack)
            }
        } else {
            audioDecoder?.let {
                extractor.selectTrack(audioTrack)
            }
            audioDecoder?.start()
        }
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
            audioDecoder -> {
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
            videoDecoder -> {
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
                if (releaseState == DECODING_FORMAT) {
                    extractState = STOPPING
                }
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
        while (extractState != STOPPED && remaining) {
            val track = extractor.sampleTrackIndex
            if (track == -1) {
                releaseState = EOS
                extractState = STOPPING
            }
//            Log.d(TAG, "track $track")
            when {
                extractState == STOPPING
                        && releaseState == DECODING_FORMAT
                        && audioInputBuffers.isNotEmpty() -> {
                    val index = audioInputBuffers.remove()
                    audioDecoder?.queueInputBuffer(
                        index,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    videoDecoder?.let {
                        extractor.selectTrack(videoTrack)
                    }
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    extractState = STOPPED
                    Log.d(TAG, "audio configured")
                }
                extractState == STOPPING
                        && (releaseState == EOS || releaseState == PAUSE)
                        && (videoDecoder == null || videoChannel == null || videoInputBuffers.isNotEmpty())
                        && (audioDecoder == null || audioChannel == null || audioInputBuffers.isNotEmpty()) -> {
                    Log.d(
                        TAG,
                        "audio inputs ${audioInputBuffers.size} video ${videoInputBuffers.size}"
                    )
                    Log.d(TAG, "flags ${extractor.sampleFlags}")

                    if (videoChannel != null) {
                        videoDecoder?.let {
                            val videoIndex = videoInputBuffers.remove()
                            it.queueInputBuffer(
                                videoIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }

                    if (audioChannel != null) {
                        audioDecoder?.let {
                            val index = audioInputBuffers.remove()
                            it.queueInputBuffer(
                                index,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }

                    when (releaseState) {
                        EOS -> extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        PAUSE -> extractor.seekTo(
                            extractor.sampleTime,
                            MediaExtractor.SEEK_TO_PREVIOUS_SYNC
                        )
                    }
                    extractState = STOPPED
                }

                extractState == STOPPING -> {
                    remaining = false
                }

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
//                        Log.d(TAG, "audio $index size $size flags ${extractor.sampleFlags}")
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
                else -> {
                    remaining = false
                }
            }
        }
    }

    fun releaseAudioBuffer(index: Int, eos: Boolean) {
//        Log.d(TAG, "release audio buffer $index $eos")
        when {
            !eos -> {
                audioDecoder?.releaseOutputBuffer(index, false)
            }
            releaseState == DECODING_FORMAT
                    || releaseState == PAUSE
                    || releaseState == EOS -> {
                audioInputBuffers.clear()
                audioDecoder?.flush()
            }
        }
    }

    fun releaseVideoBuffer(index: Int, eos: Boolean) {
        when {
            !eos -> {
                videoDecoder?.releaseOutputBuffer(index, true)
            }
            releaseState == PAUSE
                    || releaseState == EOS -> {
                videoInputBuffers.clear()
                videoDecoder?.flush()
            }
        }
    }

    fun stop() {
        releaseState = PAUSE
        extractState = STOPPING
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
        const val DECODING_FORMAT = 0
        const val PAUSE = 1
        const val EOS = 2

        const val RUNNING = 0
        const val STOPPING = 1
        const val STOPPED = 2
    }
}