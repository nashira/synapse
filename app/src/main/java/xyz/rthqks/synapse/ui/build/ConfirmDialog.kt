package xyz.rthqks.synapse.ui.build

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ConfirmDialog : DialogFragment() {
    var listener: ((Boolean) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context!!).apply {
            setTitle("Delete Node?")
            setNegativeButton("Cancel") { d, w ->
                listener?.invoke(false)
            }
            setPositiveButton("Delete") { d, w ->
                listener?.invoke(true)
            }
        }.create()

    }
}