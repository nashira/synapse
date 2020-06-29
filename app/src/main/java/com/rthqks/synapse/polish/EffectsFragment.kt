package com.rthqks.synapse.polish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.ui.Choice
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_effects.*
import kotlinx.android.synthetic.main.fragment_settings.button_close
import kotlinx.android.synthetic.main.layout_effect_list_item.view.*
import javax.inject.Inject

class EffectsFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PolishViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_effects, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[PolishViewModel::class.java]

        button_close.setOnClickListener {
            viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        }

        val effectAdapter = EffectAdapter(::onEffectSelected)
        effect_list.adapter = effectAdapter

        viewModel.effects.observe(viewLifecycleOwner, Observer {
            effectAdapter.setEffects(it)
        })
    }

    private fun onEffectSelected(network: Network) {
        viewModel.bottomSheetState.value = BottomSheetBehavior.STATE_HIDDEN
        viewModel.setEffect(network)
    }
}

private class EffectAdapter(
    private val onEffectSelected: (Network) -> Unit
) : RecyclerView.Adapter<EffectViewHolder>() {
    private var effects: List<Network> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect_list_item, parent, false)
        return EffectViewHolder(view, onEffectSelected)
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        holder.bind(effects[position])
    }

    fun setEffects(list: List<Network>) {
        effects = list
        notifyDataSetChanged()
    }
}

private class EffectViewHolder(
    itemView: View,
    onEffect: (Network) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val nameView = itemView.effect_title
    private val descView = itemView.effect_description
    private var network: Network? = null

    init {
        itemView.setOnClickListener {
            network?.let { onEffect(it) }
        }
    }

    fun bind(network: Network) {
        this.network = network
        nameView.text = network.name
        descView.text = network.description
    }
}


