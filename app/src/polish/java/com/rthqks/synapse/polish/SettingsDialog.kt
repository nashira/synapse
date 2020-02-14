package com.rthqks.synapse.polish

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Stabilize
import com.rthqks.synapse.logic.VideoSize
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.polish.fragment_settings.view.*
import javax.inject.Inject

class SettingsDialog() : DialogFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[PolishViewModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_settings, null)

        view.size_720p.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(VideoSize, Size(1280, 720))
            }
        }

        view.size_1080p.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(VideoSize, Size(1920, 1080))
            }
        }

        view.fps_30.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(FrameRate, 30)
            }
        }

        view.fps_60.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(FrameRate, 60)
            }
        }

        view.stabilize_on.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(Stabilize, true)
            }
        }

        view.stabilize_off.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                viewModel.editProperty(Stabilize, false)
            }
        }

        return AlertDialog.Builder(context!!, R.style.DialogTheme).apply {
            setTitle(R.string.title_camera_settings)
                .setPositiveButton(R.string.done) { dialogInterface: DialogInterface, i: Int ->
                    dismiss()
                }
            setView(view)

        }.create()
    }
}