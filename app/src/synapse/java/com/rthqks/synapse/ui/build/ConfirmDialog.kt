package com.rthqks.synapse.ui.build

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ConfirmDialog() : DialogFragment() {
    var title: Int = 0
    var cancel: Int = 0
    var confirm: Int = 0
    var listener: ((Boolean) -> Unit)? = null

    constructor(
        title: Int,
        cancel: Int,
        confirm: Int,
        listener: ((Boolean) -> Unit)): this() {
        this.listener = listener
        arguments = Bundle().apply {
            putInt("title", title)
            putInt("cancel", cancel)
            putInt("confirm", confirm)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            title = getInt("title")
            cancel = getInt("cancel")
            confirm = getInt("confirm")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context!!).apply {
            setTitle(title)
            setNegativeButton(cancel) { d, w ->
                listener?.invoke(false)
            }
            setPositiveButton(confirm) { d, w ->
                listener?.invoke(true)
            }
        }.create()
    }
}