package com.rthqks.synapse.codec

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.net.toUri
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileDescriptor
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*


class Encoder(
    private val context: Context,
    private val handler: Handler
) : MediaCodec.Callback() {

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

    fun setVideo(size: Size, fps: Int, rotation: Int): Surface {
        hasVideo = true
        this.size = size
        this.fps = fps
        this.rotation = rotation
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            size.width,
            size.height
        )

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoEncoder?.setCallback(this, handler)
        return videoEncoder!!.createInputSurface()
    }

    fun setAudio(audioFormat: AudioFormat) {
        hasAudio = true
        inputAudioFormat = audioFormat
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

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder?.setCallback(this, handler)
        audioEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun startEncoding() {
        val fileName = FILENAME_FORMAT.format(Date()) + ".mp4"
        Log.d(TAG, "startEncoding $fileName")

        createMuxer(fileName)

        videoEncoder?.start()
    }

    private fun createMuxer(fileName: String) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val resolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/Synapse"
            )
            val imageUri: Uri? =
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            val fd = resolver.openAssetFileDescriptor(imageUri!!, "w")!!.fileDescriptor
            mediaMuxer = MediaMuxer(fd!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).let { "$it/Synapse" }
            val file = "$baseDir/$fileName"

            File(baseDir).also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }

            File(file).createNewFile()

            mediaMuxer = MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
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
                Log.d(TAG, "audio $event")
                event.set(index, info, buffer)
                runBlocking {
                    audioChannel?.send(event)
                }
            }
            videoEncoder -> {
                Log.d(TAG, "video")
                mediaMuxer?.let {
                    val buffer = codec.getOutputBuffer(index)!!
                    it.writeSampleData(videoTrack, buffer, info)
                    codec.releaseOutputBuffer(index, true)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        it.stop()
                    }
                }
            }
        }
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        when (codec) {
            audioEncoder -> {
                Log.d(TAG, "audio onInputBufferAvailable $index")
                audioInputBuffers.add(index)
            }
            videoEncoder -> {
                Log.d(TAG, "video onInputBufferAvailable $index")
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
                mediaMuxer?.let {
                    audioTrack = it.addTrack(format)
                    if (hasVideo && videoTrack > -1) {
                        it.start()
                    }
                }
            }
            videoEncoder -> {
                outputVideoFormat = format
                Log.d(TAG, "video format: $format")
                mediaMuxer?.let {
                    videoTrack = it.addTrack(format)
                    it.start()
//                    if (hasAudio && audioTrack> -1) {
//                        it.start()
//                    }
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
        const val TAG = "Encoder"
        const val SYNAPSE_VIDEO_DIR = "Synapse"
        var FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val BUFFER_INFO = MediaCodec.BufferInfo()
        const val DECODING_FORMAT = 0
        const val PAUSE = 1
        const val EOS = 2

        const val RUNNING = 0
        const val STOPPING = 1
        const val STOPPED = 2
    }
}