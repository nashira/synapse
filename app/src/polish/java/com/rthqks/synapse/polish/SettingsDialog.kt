package com.rthqks.synapse.polish

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
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
    private lateinit var customView: View

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d("Settings", "activity")
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[PolishViewModel::class.java]

        updateStates()

        listOf(customView.size_720p,
        customView.size_1080p,
        customView.fps_30,
        customView.fps_60,
        customView.stabilize_on,
        customView.stabilize_off).forEach {
            it.setOnClickListener(this::onClick)
        }
    }

    private fun updateStates() {
        when (viewModel.properties[VideoSize]) {
            Size(1280, 720) -> customView.size_720p.isChecked = true
            Size(1920, 1080) -> customView.size_1080p.isChecked = true
        }
        when (viewModel.properties[FrameRate]) {
            30 -> customView.fps_30.isChecked = true
            60 -> customView.fps_60.isChecked = true
        }
        when (viewModel.properties[Stabilize]) {
            true -> customView.stabilize_on.isChecked = true
            false -> customView.stabilize_off.isChecked = true
        }
    }

    private fun onClick(view: View) {
        when (view) {
            customView.size_720p -> viewModel.editProperty(VideoSize, Size(1280, 720), recreate = true)
            customView.size_1080p -> viewModel.editProperty(VideoSize, Size(1920, 1080), recreate = true)
            customView.fps_30 -> viewModel.editProperty(FrameRate, 30, restart = true)
            customView.fps_60 -> viewModel.editProperty(FrameRate, 60, restart = true)
            customView.stabilize_on -> viewModel.editProperty(Stabilize, true, restart = true)
            customView.stabilize_off -> viewModel.editProperty(Stabilize, false, restart = true)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d("Settings", "dialog")
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_settings, null)
        customView = view

        return AlertDialog.Builder(context!!, R.style.DialogTheme).apply {
            setTitle(R.string.title_camera_settings)
                .setNeutralButton(R.string.done) { dialogInterface: DialogInterface, i: Int ->
                    dismiss()
                }
            setView(view)

        }.create()
    }
}