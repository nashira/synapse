package com.rthqks.synapse.polish

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.flow.logic.Network
import com.rthqks.synapse.databinding.FragmentEffectsBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class EffectsFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel
    private lateinit var binding: FragmentEffectsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEffectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        binding.buttonClose.setOnClickListener {
            viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        }

        val effectAdapter = EffectAdapter(::onEffectSelected)
        binding.effectList.adapter = effectAdapter

        viewModel.effects.observe(viewLifecycleOwner, Observer {
            val selected = it.indexOfFirst { it.id == viewModel.currentEffect?.id }
            effectAdapter.setEffects(it, selected)
        })
    }

    private fun onEffectSelected(network: Network) {
        Log.d(TAG, "onEffectSelected: ${network.name}")
        viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        viewModel.setEffect(network.id)
    }

    companion object {
        private val TAG = "EffectsFragment"
    }
}

private class EffectAdapter(
    private val onEffectSelected: (Network) -> Unit
) : RecyclerView.Adapter<EffectViewHolder>() {
    private var effects: List<Network> = emptyList()
    private var selectedPos: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect_list_item, parent, false)
        return EffectViewHolder(view) {
            notifyItemChanged(selectedPos)
            selectedPos = it
            notifyItemChanged(selectedPos)
            onEffectSelected(effects[it])
        }
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        val network = effects[position]
        holder.bind(network, position == selectedPos)
    }

    fun setEffects(list: List<Network>, selected: Int) {
        selectedPos = selected
        effects = list
        notifyDataSetChanged()
    }
}

private class EffectViewHolder(
    itemView: View,
    onEffect: (Int) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val nameView = itemView.findViewById<TextView>(R.id.effect_title)
    private val descView = itemView.findViewById<TextView>(R.id.effect_description)
    private var network: Network? = null

    init {
        itemView.setOnClickListener {
            onEffect(adapterPosition)
        }
    }

    fun bind(network: Network, selected: Boolean) {
        this.network = network
        nameView.text = network.name
        descView.text = network.description
        itemView.isSelected = selected
    }
}


