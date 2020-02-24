package com.rthqks.synapse.assets

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoStorage @Inject constructor(
    private val context: Context
) {
    // Container for information about each video.
    data class Video(
        val uri: Uri,
        val name: String,
        val date: Long,
        val size: Int
    )

    fun createMuxer(): Pair<String, MediaMuxer> {
        val date = FILENAME_FORMAT.format(Date())
        val fileName = "synapse_$date.mp4"
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
            val mediaMuxer = MediaMuxer(fd!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            assetFileDescriptor.close()
            val file = uri.toString()
            return Pair(file, mediaMuxer)
        } else {
            val baseDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .let { "$it/$SYNAPSE_VIDEO_DIR" }
            val file = "$baseDir/$fileName"

            File(baseDir).also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }

            File(file).createNewFile()

            val mediaMuxer = MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            return Pair(file, mediaMuxer)
        }
    }

    fun setVideoFileReady(fileUri: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(
                    fileUri.toUri(),
                    contentValues,
                    null,
                    null
                )

        } else {
                MediaScannerConnection.scanFile(
                    context,
                    Array(1) { fileUri },
                    Array(1) { MIME_MP4 }
                ) { _, _ -> }
        }
    }

    fun getLocalVideos(): List<Video> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            localFilesQ()
        } else {
            localFilesLegacy()
        }
    }

    private fun localFilesQ(): List<Video> {
        val videoList = mutableListOf<Video>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE
        )


        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )
        query?.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val date = cursor.getLong(dateColumn) * 1000 // s -> ms
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                videoList += Video(contentUri, name, date, size)
            }
        }
        return videoList
    }
    private fun localFilesLegacy(): List<Video> {
        val videoList = mutableListOf<Video>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE
        )

        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("synapse_%")

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        query?.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val date = cursor.getLong(dateColumn) * 1000 // s -> ms
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                videoList += Video(contentUri, name, date, size)
            }
        }
        return videoList
    }

    companion object {
        const val SYNAPSE_VIDEO_DIR = "Synapse"
        const val MIME_MP4 = "video/mp4"
        var FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}