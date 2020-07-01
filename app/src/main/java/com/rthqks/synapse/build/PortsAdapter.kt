package com.rthqks.synapse.build

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        R.layout.layout_port_fragment_node
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
    private val textView = itemView as TextView
    private var connector: Connector? = null

    init {
        textView.setOnClickListener {
            connector?.let { it1 -> onClick(it, it1) }
        }
    }

    fun bind(connector: Connector, startAligned: Boolean) {
        this.connector = connector
        textView.text = connector.port.key

        if (startAligned) {
            startIcons(connector.port.type)
        } else {
            endIcons(connector.port.type)
        }
    }

    private fun startIcons(type: PortType) {
        when (type) {
            PortType.Audio -> textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_volume_2,
                0,
                0,
                0
            )
            PortType.Video -> textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_image,
                0,
                0,
                0
            )
            PortType.Texture3D -> textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_layers,
                0,
                0,
                0
            )
            else -> textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_3d_rotation,
                0,
                0,
                0
            )
        }
    }

    private fun endIcons(type: PortType) {
        when (type) {
            PortType.Audio -> textView.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_volume_2,
                0
            )
            PortType.Video -> textView.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_image,
                0
            )
            PortType.Texture3D -> textView.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_layers,
                0
            )
            else -> textView.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                R.drawable.ic_3d_rotation,
                0
            )
        }
    }
}