package com.rthqks.synapse.build

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Connector
import com.rthqks.synapse.logic.PortType
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*

class PortsAdapter(
    private val isStartAligned: Boolean,
    private val onClick: (View, Connector) -> Unit
) : RecyclerView.Adapter<PortViewHolder>() {
    private val ports = mutableListOf<Connector>()

    fun setPorts(ports: List<Connector>) {
        val oldPorts = this.ports
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = oldPorts[oldItemPosition]
                val n = ports[newItemPosition]
                return o.port == n.port
            }

            override fun getOldListSize(): Int = oldPorts.size

            override fun getNewListSize(): Int = ports.size

            override fun areContentsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int
            ): Boolean {
                val o = oldPorts[oldItemPosition]
                val n = ports[newItemPosition]
                return o.link == n.link
            }

        })
        this.ports.clear()
        this.ports.addAll(ports)
        result.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            viewType,
            parent,
            false
        )
        return PortViewHolder(view, onClick)
    }

    override fun getItemViewType(position: Int): Int = if (isStartAligned) {
        R.layout.layout_port_fragment_node_start
    } else {
        R.layout.layout_port_fragment_node
    }

    override fun getItemCount() = ports.size

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(ports[position], isStartAligned)
    }
}

class PortViewHolder(
    itemView: View,
    onClick: (View, Connector) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val button = itemView.button
    private val label = itemView.label
    private var connector: Connector? = null

    init {
        button.setOnClickListener {
            connector?.let { it1 -> onClick(it, it1) }
        }
        button.setOnLongClickListener {
//            connector?.let { it1 -> onConnectorLongClick(it, it1) }
            true
        }
    }

    fun bind(connector: Connector, startAligned: Boolean) {
        this.connector = connector
        connector.link?.let {
            //                val otherPort =
//                    if (startAligned) viewModel.getConnector(it.fromNodeId, it.fromPortId).port
//                    else viewModel.getConnector(it.toNodeId, it.toPortId).port

//                label.text = connector.port.name
            button.setBackgroundResource(R.drawable.selectable_accent)
        } ?: run {
            button.setBackgroundResource(R.drawable.selectable_grey)
//                label.text = connector.port.name
        }

        label.text = connector.port.key

        when (connector.port.type) {
            PortType.Audio -> button.setImageResource(R.drawable.ic_volume_2)
            PortType.Video -> button.setImageResource(R.drawable.ic_image)
            PortType.Texture3D -> button.setImageResource(R.drawable.ic_layers)
        }
    }
}