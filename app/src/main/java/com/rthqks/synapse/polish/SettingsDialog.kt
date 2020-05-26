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
import com.rthqks.synapse.exec.Properties
import com.rthqks.synapse.logic.NodeDef.Camera.FrameRate
import com.rthqks.synapse.logic.NodeDef.Camera.Stabilize
import com.rthqks.synapse.logic.NodeDef.Camera.VideoSize
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_settings.view.*
import javax.inject.Inject

class SettingsDialog() : DialogFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel
    private lateinit var customView: View

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d("Settings", "activity")
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        updateStates()

        listOf(
            customView.size_720p,
            customView.size_1080p,
            customView.fps_30,
            customView.fps_60,
            customView.stabilize_on,
            customView.stabilize_off
        ).forEach {
            it.setOnClickListener(throttleClick(1000, this::onClick))
        }
    }

    private fun updateStates() {
        viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.let {
            val properties = Properties(it.properties)

            when (properties[VideoSize]) {
                Size(1280, 720) -> customView.size_720p.isChecked = true
                Size(1920, 1080) -> customView.size_1080p.isChecked = true
            }
            when (properties[FrameRate]) {
                30 -> customView.fps_30.isChecked = true
                60 -> customView.fps_60.isChecked = true
            }
            when (properties[Stabilize]) {
                true -> customView.stabilize_on.isChecked = true
                false -> customView.stabilize_off.isChecked = true
            }
        }
    }

    private fun onClick(view: View) {
        when (view) {
            customView.size_720p -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                VideoSize,
                Size(1280, 720)
            )
            customView.size_1080p -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                VideoSize,
                Size(1920, 1080)
            )
            customView.fps_30 -> viewModel.editProperty(EffectExecutor.ID_CAMERA, FrameRate, 30)
            customView.fps_60 -> viewModel.editProperty(EffectExecutor.ID_CAMERA, FrameRate, 60)
            customView.stabilize_on -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                Stabilize,
                true
            )
            customView.stabilize_off -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                Stabilize,
                false
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d("Settings", "dialog")
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.fragment_settings, null)
        customView = view

        val titleView = inflater.inflate(R.layout.fragment_settings_title, null)
        return AlertDialog.Builder(requireContext(), R.style.DialogTheme).apply {
            setCustomTitle(titleView)
                .setNeutralButton(R.string.done) { dialogInterface: DialogInterface, i: Int ->
                    dismiss()
                }
            setView(view)

        }.create()
    }
}