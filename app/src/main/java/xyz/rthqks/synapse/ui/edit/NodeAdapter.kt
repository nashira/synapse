package xyz.rthqks.synapse.ui.edit

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.node_edit_item.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.NodeData
import xyz.rthqks.synapse.data.PortConfig
import xyz.rthqks.synapse.logic.GraphConfigEditor


class NodeAdapter(
    private val graphViewModel: EditGraphViewModel
) : RecyclerView.Adapter<NodeViewHolder>() {

    private var nodes = mutableListOf<NodeData>()
    private val viewPool = RecyclerView.RecycledViewPool()
    var onEditNodeProperties: ((NodeData) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.node_edit_item,
            parent,
            false
        )
        return NodeViewHolder(view, viewPool, graphViewModel) {
            onEditNodeProperties?.invoke(it)
        }
    }

    override fun getItemCount(): Int = nodes.size ?: 0

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        nodes.let {
            holder.bind(it[position])
        }
    }

    fun setGraphEditor(graphConfigEditor: GraphConfigEditor) {
        nodes.clear()
        nodes.addAll(graphConfigEditor.nodes.values)
        notifyDataSetChanged()
    }

    fun moveNode(fromPos: Int, toPos: Int) {
        nodes.removeAt(fromPos).let {
            nodes.add(toPos, it)
            notifyItemMoved(fromPos, toPos)
        }

        if (fromPos == 0 || toPos == 0) {
            // redraw dividers :/
            notifyItemChanged(fromPos)
            notifyItemChanged(toPos)
        }
    }

    fun onNodeAdded(node: NodeData) {
        nodes.add(node)
        nodes.size.let {
            notifyItemInserted(it - 1)
            if (it > 1) {
                notifyItemChanged(it - 2)
            }
        }
    }

    fun removeNode(position: Int) {
        val node = nodes.removeAt(position)
        graphViewModel.removeNode(node)
        notifyDataSetChanged()
    }
}

class NodeViewHolder(
    itemView: View,
    viewPool: RecyclerView.RecycledViewPool,
    private val graphViewModel: EditGraphViewModel,
    private val onEditNodeProperties: ((NodeData) -> Unit)
) :
    RecyclerView.ViewHolder(itemView) {
    private val name = itemView.name
    private val propertiesButton = itemView.button_properties
    private val inputMenu = itemView.inputs_menu
    private val outputMenu = itemView.outputs_menu
    private val inputAdapter = PortsAdapter(true)
    private val outputAdapter = PortsAdapter(false)
    private var nodeData: NodeData? = null

    init {
        inputMenu.setRecycledViewPool(viewPool)
        outputMenu.setRecycledViewPool(viewPool)

        inputMenu.layoutManager = LinearLayoutManager(itemView.context)
        outputMenu.layoutManager = LinearLayoutManager(itemView.context)

        inputMenu.adapter = inputAdapter
        outputMenu.adapter = outputAdapter

        propertiesButton.setOnClickListener {
            nodeData?.let { n -> onEditNodeProperties(n) }
        }
    }

    fun bind(node: NodeData) {
        nodeData = node
        val nodeType = node.type
        name.setText(nodeType.name)
        name.setCompoundDrawablesWithIntrinsicBounds(nodeType.icon, 0, 0, 0)

        inputAdapter.setPorts(node.inputs)
        outputAdapter.setPorts(node.outputs)
        itemView.header_input.visibility = if (node.inputs.isEmpty()) View.INVISIBLE else View.VISIBLE
        itemView.header_output.visibility = if (node.outputs.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    inner class PortsAdapter(
        private val isStartAligned: Boolean
    ) : RecyclerView.Adapter<PortViewHolder>() {
        private val ports = mutableListOf<PortConfig>()

        fun setPorts(ports: List<PortConfig>) {
            this.ports.clear()
            this.ports.addAll(ports)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.port_config_item,
                parent,
                false
            )
            return PortViewHolder(view)
        }

        override fun getItemCount() = ports.size

        override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
            holder.bind(ports[position], isStartAligned)
        }
    }

    inner class PortViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.name
        private var portConfig: PortConfig? = null

        init {
            itemView.setOnClickListener { _ ->
                portConfig?.let { it ->
                    graphViewModel.setSelectedPort(it)
                    null
                }
            }
        }

        fun bind(portConfig: PortConfig, startAligned: Boolean) {
            this.portConfig = portConfig
            val dataType = portConfig.type
            name.setText(dataType.name)
            if (startAligned) {
                name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                name.setCompoundDrawablesWithIntrinsicBounds(dataType.icon, 0, 0, 0)
            } else {
                name.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                name.setCompoundDrawablesWithIntrinsicBounds(0, 0, dataType.icon, 0)
            }
            Log.d(TAG, "port ${portConfig.key} ${graphViewModel.getPortState(portConfig)}")
            when(graphViewModel.getPortState(portConfig)) {
                PortState.Unconnected -> itemView.setBackgroundColor(Color.TRANSPARENT)
                PortState.Connected -> itemView.setBackgroundColor(Color.rgb(200, 200, 255))
                PortState.SelectedUnconnected -> itemView.setBackgroundColor(Color.rgb(200, 255, 255))
                PortState.SelectedConnected -> itemView.setBackgroundColor(Color.rgb(255, 200, 255))
                PortState.EligibleToConnect -> itemView.setBackgroundColor(Color.rgb(200, 255, 200))
                PortState.EligibleToDisconnect -> itemView.setBackgroundColor(Color.rgb(255, 200, 200))
            }
        }
    }
}

private val TAG = NodeAdapter::class.java.simpleName