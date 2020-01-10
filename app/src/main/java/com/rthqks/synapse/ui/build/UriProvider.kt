package com.rthqks.synapse.ui.build

import android.app.Activity
import android.content.Intent
import android.net.Uri


class UriProvider(
    private val intentHandler: (Intent) -> Unit
) {
    private var callback: ((Uri) -> Unit)? = null

    fun getUri(mime: String, callback: (Uri) -> Unit) {
        this.callback = callback

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        intentHandler(intent)
    }

    fun onActivityResult(activity: Activity, data: Uri) {
        activity.contentResolver.takePersistableUriPermission(
            data,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        callback?.invoke(data)
    }

    companion object {

    }
}