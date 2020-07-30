package com.rthqks.flow.codec

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.flow.assets.VideoStorage
import com.rthqks.flow.exec.ExecutionContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import java.util.concurrent.Executors


class Encoder(
    private val context: ExecutionContext
) : MediaCodec.Callback() {
    private val videoStorage: com.rthqks.flow.assets.VideoStorage = context.videoStorage

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + Job())
    private var trackCompletable: CompletableDeferred<Unit>? = null

    private val thread = HandlerThread("Encoder")
    private val handler: Handler

    init {
        thread.start()
        handler = Handler(thread.looper)
    }

    private var size: Size = Size(0, 0)
    private var fps = 0

    private var outputVideoFormat: MediaFormat? = null
    private var outputAudioFormat: MediaFormat? = null
    private var hasVideo = false
    private var hasAudio = false

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var inputAudioFormat: AudioFormat? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var running = 0
    private var currentFile: String? = null
    private var audioInputBuffers = Channel<Int>(20)
    private val inputSurface = MediaCodec.createPersistentInputSurface()
    private var stopDeferred: CompletableDeferred<Unit>? = null

    fun setVideo(size: Size, fps: Int): Surface {
        hasVideo = true
        this.size = size
        this.fps = fps
        videoEncoder = context.videoEncoder
        configureVideo()
        return inputSurface!!
    }

    private fun configureVideo() {
        if (!hasVideo) return

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            size.width,
            size.height
        )

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        // TODO: calculate bit rate
        format.setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        videoEncoder?.reset()
        videoEncoder?.setCallback(this, handler)
        videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoEncoder!!.setInputSurface(inputSurface!!)
    }

    fun setAudio(audioFormat: AudioFormat) {
        hasAudio = true
        inputAudioFormat = audioFormat
        audioEncoder = context.audioEncoder
//        configureAudio()
    }

    private fun configureAudio() {
        val audioFormat = inputAudioFormat ?: return
        val format = MediaFormat()
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioFormat.sampleRate)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioFormat.channelCount)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        audioEncoder?.reset()
        audioEncoder?.setCallback(this, handler)
        audioEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun startEncoding(rotation: Int) {

        videoTrack = -1
        audioTrack = -1
        audioInputBuffers = Channel(20)

        configureVideo()
        configureAudio()

        val (file, muxer) =  videoStorage.createMuxer()
        currentFile = file
        mediaMuxer = muxer
        Log.d(TAG, "startEncoding $file")

        mediaMuxer?.setOrientationHint(rotation)

        running = 0
        if (hasVideo) running++
        if (hasAudio) running++

        trackCompletable = CompletableDeferred()

        videoEncoder?.start()
        audioEncoder?.start()
    }

    suspend fun stopEncoding() {
        Log.d(TAG, "stopEncoding")
        stopDeferred = CompletableDeferred()
        videoEncoder?.signalEndOfInputStream()
        audioEncoder?.let {
            val index = audioInputBuffers.receive()
            it.getInputBuffer(index)
            it.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
//            while (audioInputBuffers.poll() != null);
        }
        stopDeferred?.await()
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
//        Log.d(TAG, "${codec == audioEncoder} $index ${info.presentationTimeUs}")
        scope.launch {
            trackCompletable?.await()
            handleOutputBuffer(codec, index, info)
            trackCompletable = null
        }
    }

    private fun handleOutputBuffer(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        when (codec) {
            audioEncoder -> {
//                Log.d(TAG, "audio $index ${info.size} ${info.offset} ${info.presentationTimeUs}")
                mediaMuxer?.also {
                    when {
                        isConfig -> Log.d(TAG, "audio config")
                        isEos -> {
                            running--
                            codec.flush()
                            codec.reset()
                            checkStopMuxer()
                        }
                        else -> {
                            val buffer = codec.getOutputBuffer(index)!!
                            it.writeSampleData(audioTrack, buffer, info)
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            videoEncoder -> {
//                Log.d(TAG, "video $index ${info.presentationTimeUs}")
                mediaMuxer?.also {
                    when {
                        isConfig -> Log.d(TAG, "video config")
                        isEos -> {
                            running--
                            codec.flush()
                            codec.reset()
                            checkStopMuxer()
                        }
                        else -> {
                            val buffer = codec.getOutputBuffer(index)!!
                            it.writeSampleData(videoTrack, buffer, info)
                            codec.releaseOutputBuffer(index, true)
                        }
                    }
                }
            }
        }
    }

    private fun checkStopMuxer() {
        Log.d(TAG, "stop muxer $running")
        if (running <= 0) {
            mediaMuxer?.stop()
            mediaMuxer?.release()
            mediaMuxer = null

            currentFile?.let { videoStorage.setVideoFileReady(it) }
            currentFile = null
            stopDeferred?.complete(Unit)
        }
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        when (codec) {
            audioEncoder -> {
                runBlocking {
                    audioInputBuffers.send(index)
                }
            }
            videoEncoder -> {
                // won't happen with input surface
            }
        }
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        when (codec) {
            audioEncoder -> {
                outputAudioFormat = format
                Log.d(TAG, "audio format: $format")
                mediaMuxer?.let {
                    audioTrack = it.addTrack(format)
                    Log.d(TAG, "muxer starting? ${(hasVideo && videoTrack > -1 || !hasVideo)}")
                    if (hasVideo && videoTrack > -1 || !hasVideo) {
                        it.start()
                        trackCompletable?.complete(Unit)
                    }
                }
            }
            videoEncoder -> {
                outputVideoFormat = format
                Log.d(TAG, "video format: $format")
                mediaMuxer?.let {
                    videoTrack = it.addTrack(format)
                    Log.d(TAG, "muxer starting? ${(hasAudio && audioTrack > -1 || !hasAudio)}")
                    if (hasAudio && audioTrack > -1 || !hasAudio) {
                        it.start()
                        trackCompletable?.complete(Unit)
                    }
                }
            }
        }
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        Log.d(TAG, "onError $e")
        when (codec) {
            audioEncoder -> {

            }
            videoEncoder -> {

            }
        }
    }

    suspend fun release() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
        dispatcher.close()
        thread.quitSafely()
    }

    suspend fun writeAudio(buffer: ByteBuffer, timestamp: Long) {
        val index = audioInputBuffers.receive()
//        Log.d(TAG, "write $timestamp")
        audioEncoder?.getInputBuffer(index)?.let {
            it.put(buffer)
            audioEncoder?.queueInputBuffer(
                index,
                0,
                buffer.limit(),
                timestamp,
                0
            )
        }
    }

    companion object {
        const val TAG = "Encoder"
    }
}