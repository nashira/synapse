package com.rthqks.flow.exec_dep.node

import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec.Properties
import com.rthqks.flow.exec_dep.NodeExecutor
import com.rthqks.flow.exec_dep.link.*
import com.rthqks.flow.logic.NodeDef.Image.MediaUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream

class ImageSourceNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val glesManager = context.glesManager

    private var startJob: Job? = null
    private val imageUri: Uri get() = properties[MediaUri]
    private var size = Size(0, 0)
    private var rotation = 0f
    private var frameCount = 0

    private var texture: com.rthqks.flow.gl.Texture2d? = null

    private fun getInputStream(): InputStream? {
        return when (imageUri.scheme) {
            "assets" -> context.context.assets.open(imageUri.pathSegments.joinToString("/"))
            else -> context.context.contentResolver.openInputStream(imageUri)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun onCreate() {
        val asset = getInputStream() ?: return
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(asset, null, options)
        asset.close()
        size = Size(options.outWidth, options.outHeight)

//        getInputStream()?.let {
//            val exif = ExifInterface(it)
//            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
//
//            when (orientation) {
//                ExifInterface.ORIENTATION_ROTATE_90 -> {
//                    rotation = 90f
//                    size = Size(size.height, size.width)
//                }
//                ExifInterface.ORIENTATION_ROTATE_180 -> {
//                    rotation = 180f
//                }
//                ExifInterface.ORIENTATION_ROTATE_270 -> {
//                    rotation = 270f
//                    size = Size(size.height, size.width)
//                }
//            }
//            it.close()
//        }
        Log.d(TAG, "options $size")
    }

    override suspend fun onInitialize() {
        val connection = connection(OUTPUT) ?: return
        val inputStream = getInputStream() ?: return

        val texture = com.rthqks.flow.gl.Texture2d(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_CLAMP_TO_EDGE,
            GLES30.GL_LINEAR
        )
        this.texture = texture

        BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
            glesManager.glContext {
                texture.initialize()
                texture.initData(0, bitmap)
            }
            bitmap.recycle()
        }
        inputStream.close()

        val item1 = VideoEvent(texture)
        val item2 = VideoEvent(texture)
        Matrix.translateM(item1.matrix, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(item1.matrix, 0, 1f, -1f, 1f)
        Matrix.rotateM(item1.matrix, 0, rotation, 0f, 0f, 1f)
        Matrix.translateM(item1.matrix, 0, -0.5f, -0.5f, 0f)
        System.arraycopy(item1.matrix, 0, item2.matrix, 0, 16)
        connection.prime(item1, item2)
    }

    override suspend fun onStart() {
        val channel = channel(OUTPUT) ?: return
        startJob = scope.launch {
            // TODO: this delay is because the frames get sent before a surface is available for preview
            // non-surfaceviewnodes are not affected
//            delay(250)
            channel.receive().also {
                Log.d(TAG, "sending event $it")
                it.eos = false
                it.count = ++frameCount
                it.queue()
            }
        }
    }

    override suspend fun onStop() {
        val channel = channel(OUTPUT) ?: return
        startJob?.join()
        channel.receive().also {
            it.eos = true
            it.queue()
        }
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            texture?.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                VideoConfig(
                    GLES30.GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    GLES30.GL_RGBA8,
                    GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE,
                    0
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    companion object {
        const val TAG = "ImageSourceNode"
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("image_1")
    }
}
