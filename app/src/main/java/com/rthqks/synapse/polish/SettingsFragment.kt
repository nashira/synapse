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
import com.rthqks.synapse.logic.NodeDef
import com.rthqks.synapse.logic.NodeDef.Camera.CameraFacing
import com.rthqks.synapse.logic.NodeDef.Camera.FrameRate
import com.rthqks.synapse.logic.NodeDef.Camera.Stabilize
import com.rthqks.synapse.logic.NodeDef.Camera.VideoSize
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.ui.*
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
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        button_close.setOnClickListener {
            viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        }

        list_capture.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(VideoSize.name)
                    ?: return@apply
            val ui = expandedUi(
                R.string.property_name_capture_size,
                R.drawable.ic_photo_size_select,
                Choice(
                    Size(1280, 720),
                    R.string.property_label_camera_capture_size_720,
                    R.drawable.square
                ),
                Choice(
                    Size(1920, 1080),
                    R.string.property_label_camera_capture_size_1080,
                    R.drawable.square
                )
            )
            setProperties(listOf(Pair(property, ui)))
        }

        list_fps.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(FrameRate.name)
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][FrameRate] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))
        }

        list_stabilize.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(
                    Stabilize.name
                )
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][Stabilize] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))
        }

        list_facing.adapter = PropertiesAdapter(::onSelected).apply {
            val property =
                viewModel.baseNetwork?.getNode(EffectExecutor.ID_CAMERA)?.properties?.get(
                    CameraFacing.name
                )
                    ?: return@apply
            val ui =
                (NodeUi[NodeDef.Camera.key][CameraFacing] as? ChoiceUi<*>)?.asType(PropertyType.EXPANDED)
                    ?: return@apply
            setProperties(listOf(Pair(property, ui)))
        }
    }

    private fun onSelected(property: Property, choice: Choice<*>) {
        Log.d(TAG, "onSelected $property")
        when (property.key) {
            VideoSize -> value_capture.setText(choice.label)
            FrameRate -> value_fps.setText(choice.label)
            Stabilize -> value_stabilize.setText(choice.label)
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
    }
}