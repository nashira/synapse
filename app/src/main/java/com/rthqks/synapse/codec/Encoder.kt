package com.rthqks.synapse.codec

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors


class Encoder(
    private val context: Context
) : MediaCodec.Callback() {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + Job())
    private var trackCompletable: CompletableDeferred<Unit>? = null

    private val thread = HandlerThread("BackgroundHandler")
    private val handler: Handler

    init {
        thread.start()
        handler = Handler(thread.looper)
    }

    private var size: Size = Size(0, 0)
    private var fps = 0
    private var rotation = 0

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
    private var inputSurface: Surface? = null

//    private val commands = scope.actor {
//
//    }

    fun setVideo(size: Size, fps: Int, rotation: Int): Surface {
        hasVideo = true
        this.size = size
        this.fps = fps
        this.rotation = rotation
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        inputSurface = MediaCodec.createPersistentInputSurface()
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
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
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

    fun startEncoding() {
        val fileName = FILENAME_FORMAT.format(Date()) + ".mp4"
        Log.d(TAG, "startEncoding $fileName")

        videoTrack = -1
        audioTrack = -1
        audioInputBuffers = Channel(20)

        configureVideo()
        configureAudio()

        createMuxer(fileName)

        running = 0
        if (hasVideo) running++
        if (hasAudio) running++

        trackCompletable = CompletableDeferred()

        videoEncoder?.start()
        audioEncoder?.start()
    }

    suspend fun stopEncoding() {
        Log.d(TAG, "stopEncoding")
        videoEncoder?.signalEndOfInputStream()
        audioEncoder?.let {
            val index = audioInputBuffers.receive()
            it.getInputBuffer(index)
            it.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
//            while (audioInputBuffers.poll() != null);
        }
    }

    private fun createMuxer(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, MIME_MP4)
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$SYNAPSE_VIDEO_DIR"
            )
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            val assetFileDescriptor = resolver.openAssetFileDescriptor(uri!!, "w")!!
            val fd = assetFileDescriptor.fileDescriptor
            mediaMuxer = MediaMuxer(fd!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            assetFileDescriptor.close()
            currentFile = uri.toString()
        } else {
            val baseDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .let { "$it/$SYNAPSE_VIDEO_DIR" }
            val file = "$baseDir/$fileName"
            currentFile = file

            File(baseDir).also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }

            File(file).createNewFile()

            mediaMuxer = MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
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
//                Log.d(TAG, "audio $index ${info.presentationTimeUs}")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                currentFile?.let {
                    val contentValues = ContentValues()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(
                        it.toUri(),
                        contentValues,
                        null,
                        null
                    )
                    currentFile = null
                }
            } else {
                currentFile?.let { file ->
                        MediaScannerConnection.scanFile(
                            context,
                            Array(1) { file },
                            Array(1) { MIME_MP4 }
                        ) { _, _ -> }
                }
            }
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
        audioEncoder?.release()
        videoEncoder?.release()
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
        const val SYNAPSE_VIDEO_DIR = "Synapse"
        const val MIME_MP4 = "video/mp4"
        var FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}