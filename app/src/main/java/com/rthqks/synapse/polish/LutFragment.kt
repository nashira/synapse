package com.rthqks.synapse.polish

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.flow.logic.NodeDef
import com.rthqks.synapse.databinding.FragmentLutBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

class LutFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel
    private lateinit var binding: FragmentLutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]
        binding.lutList.adapter = LutAdapter(EffectExecutor.LUTS, viewModel)

        binding.lutList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val spans = 3
            private val margin = resources.getDimension(R.dimen.connector_margin).roundToInt()
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val index = (view.layoutParams as GridLayoutManager.LayoutParams).spanIndex % spans
                val left = margin * (spans - index) / spans
                val right = margin * (index + 1) / spans
                outRect.set(left, 0, right, margin)
            }
        })

        binding.buttonLutClose.setOnClickListener {
            viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.lutStrength.max = 1000
        binding.lutStrength.progress = (viewModel.getLutStrength() * 1000).toInt()
        binding.lutStrength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.setLutStrength(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                viewModel.editProperty(EffectExecutor.ID_LUT,
                    NodeDef.Lut3d.LutStrength, binding.lutStrength.progress / 1000f)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "resume")
//        viewModel.startLutPreview()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "pause")
//        viewModel.stopLutPreview()
    }

    companion object {
        private const val TAG = "LutFragment"
    }
}
private class LutAdapter(
    private val luts: List<String>,
    private val viewModel: PolishViewModel
) : RecyclerView.Adapter<LutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_lut, parent, false)
        return LutViewHolder(view, viewModel)
    }

    override fun getItemCount(): Int {
        return luts.size
    }

    override fun onBindViewHolder(holder: LutViewHolder, position: Int) {
        holder.bind(luts[position])
    }
}

private class LutViewHolder(
    itemView: View,
    private val viewModel: PolishViewModel
) : RecyclerView.ViewHolder(itemView), TextureView.SurfaceTextureListener {
    private val textureView = itemView.findViewById<TextureView>(R.id.texture_view)
    private val title = itemView.findViewById<TextView>(R.id.title_view)
    private var lut: String? = null
    private var created = false

    init {
        Log.d("Lut", "onCreateViewHolder $this")
        textureView.surfaceTextureListener = this
        itemView.setOnClickListener {
            lut?.let { it1 -> viewModel.setLut(it1) }
        }
    }

    fun bind(lut: String) {
//        Log.d("Lut", "onBindViewHolder $lut ${this.lut} ${itemView.texture_view.surfaceTexture} ${itemView.texture_view.isAvailable}")
        if (created) {
            Log.w("Lut", "destroy not called")
        }
        this.lut = lut
        title.text = lut.replace("_", " ")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("Lut", "surface size: $lut")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
//        Log.d("Lut", "surface destroyed $lut $surface")
        created = false
        viewModel.unregisterLutPreview(surface)
        return false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
//        Log.d("Lut", "surface available $lut $surface")
        created = true

        val minWidth = min(width, 320)
        surface.setDefaultBufferSize(minWidth, minWidth)
//        if (adapterPosition % 3 == 0)
        lut?.let { viewModel.registerLutPreview(textureView, it) }
    }
}