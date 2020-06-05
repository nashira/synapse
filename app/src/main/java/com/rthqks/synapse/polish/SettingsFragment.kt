package com.rthqks.synapse.polish

import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.rthqks.synapse.R
import com.rthqks.synapse.exec.Properties
import com.rthqks.synapse.logic.NodeDef.Camera.FrameRate
import com.rthqks.synapse.logic.NodeDef.Camera.Stabilize
import com.rthqks.synapse.logic.NodeDef.Camera.VideoSize
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_settings.*
import javax.inject.Inject

class SettingsFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d("Settings", "activity")
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        updateStates()

        listOf(
            size_720p,
            size_1080p,
            fps_30,
            fps_60,
            stabilize_on,
            stabilize_off
        ).forEach {
            it.setOnClickListener(throttleClick(1000, this::onClick))
        }
    }

    private fun updateStates() {
        viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.let {
            val properties = Properties(it.properties)

            when (properties[VideoSize]) {
                Size(1280, 720) -> size_720p.isChecked = true
                Size(1920, 1080) -> size_1080p.isChecked = true
            }
            when (properties[FrameRate]) {
                30 -> fps_30.isChecked = true
                60 -> fps_60.isChecked = true
            }
            when (properties[Stabilize]) {
                true -> stabilize_on.isChecked = true
                false -> stabilize_off.isChecked = true
            }
        }
    }

    private fun onClick(view: View) {
        when (view) {
            size_720p -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                VideoSize,
                Size(1280, 720)
            )
            size_1080p -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                VideoSize,
                Size(1920, 1080)
            )
            fps_30 -> viewModel.editProperty(EffectExecutor.ID_CAMERA, FrameRate, 30)
            fps_60 -> viewModel.editProperty(EffectExecutor.ID_CAMERA, FrameRate, 60)
            stabilize_on -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                Stabilize,
                true
            )
            stabilize_off -> viewModel.editProperty(
                EffectExecutor.ID_CAMERA,
                Stabilize,
                false
            )
        }
    }
}