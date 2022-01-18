package com.rthqks.synapse.polish

import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.flow.logic.NodeDef
import com.rthqks.flow.logic.NodeDef.Camera.CameraFacing
import com.rthqks.flow.logic.NodeDef.Camera.FrameRate
import com.rthqks.flow.logic.NodeDef.Camera.Stabilize
import com.rthqks.flow.logic.NodeDef.Camera.VideoSize
import com.rthqks.flow.logic.Property
import com.rthqks.synapse.databinding.FragmentSettingsBinding
import com.rthqks.synapse.ui.*
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class SettingsFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        binding.buttonClose.setOnClickListener {
            viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.listCapture.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(VideoSize.name)
                    ?: return@apply
            val ui = expandedUi(
                R.string.property_name_capture_size,
                R.drawable.ic_photo_size_select,
                Choice(
                    Size(1280, 720),
                    R.string.property_label_camera_capture_size_720,
                    0
                ),
                Choice(
                    Size(1920, 1080),
                    R.string.property_label_camera_capture_size_1080,
                    0
                )
            )
            setProperties(listOf(Pair(property, ui)))

            ui.choices.firstOrNull { it.item == property.value }?.let {
                binding.valueCapture.setText(it.label)
            }
        }

        binding.listFps.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(FrameRate.name)
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][FrameRate] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))

            ui.choices.firstOrNull { it.item == property.value }?.let {
                binding.valueFps.setText(it.label)
            }
        }

        binding.listStabilize.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(
                    Stabilize.name
                )
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][Stabilize] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))

            ui.choices.firstOrNull { it.item == property.value }?.let {
                binding.valueStabilize.setText(it.label)
            }
        }

        binding.listFacing.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(
                    CameraFacing.name
                )
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][CameraFacing] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))

            ui.choices.firstOrNull { it.item == property.value }?.let {
                if (it.label != 0) {
                    binding.valueFacing.setText(it.label)
                } else {
                    binding.valueFacing.text = it.item.toString()
                }
            }
        }
    }

    private fun onSelected(property: Property, choice: Choice<*>) {
        Log.d(TAG, "onSelected $property")
        when (property.key) {
            VideoSize -> binding.valueCapture.setText(choice.label)
            FrameRate -> binding.valueFps.setText(choice.label)
            Stabilize -> binding.valueStabilize.setText(choice.label)
            CameraFacing -> binding.valueFacing.setText(choice.label)
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
    }
}