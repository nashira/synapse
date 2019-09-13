package xyz.rthqks.proc.ui.edit

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.node_edit_item.view.*
import xyz.rthqks.proc.R
import xyz.rthqks.proc.data.NodeConfig
import xyz.rthqks.proc.data.PortConfig
import xyz.rthqks.proc.logic.GraphConfigEditor


class NodeAdapter(
    private var graphConfig: GraphConfigEditor
) : RecyclerView.Adapter<NodeViewHolder>() {

    private val viewPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.node_edit_item,
            parent,
            false
        )
        return NodeViewHolder(view, viewPool, graphConfig, this)
    }

    override fun getItemCount(): Int = graphConfig.nodes.size

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        graphConfig.nodes.let {
            holder.bind(it[position])
        }
    }

    fun moveNode(fromPos: Int, toPos: Int) {
        graphConfig.nodes.removeAt(fromPos).let {
            graphConfig.nodes.add(toPos, it)
            notifyItemMoved(fromPos, toPos)
        }

        if (fromPos == 0 || toPos == 0) {
            // redraw dividers :/
            notifyItemChanged(fromPos)
            notifyItemChanged(toPos)
        }
    }

    fun onNodeAdded() {
        graphConfig.nodes.size.let {
            notifyItemInserted(it - 1)
            if (it > 1) {
                notifyItemChanged(it - 2)
            }
        }
    }
}

class NodeViewHolder(
    itemView: View,
    viewPool: RecyclerView.RecycledViewPool,
    private val graphEditor: GraphConfigEditor,
    private val nodeAdapter: NodeAdapter
) :
    RecyclerView.ViewHolder(itemView) {
    private val name = itemView.name
    private val inputMenu = itemView.inputs_menu
    private val outputMenu = itemView.outputs_menu
    private val inputAdapter = PortsAdapter(true)
    private val outputAdapter = PortsAdapter(false)

    init {
        inputMenu.setRecycledViewPool(viewPool)
        outputMenu.setRecycledViewPool(viewPool)

        inputMenu.layoutManager = LinearLayoutManager(itemView.context)
        outputMenu.layoutManager = LinearLayoutManager(itemView.context)

        inputMenu.adapter = inputAdapter
        outputMenu.adapter = outputAdapter
    }

    fun bind(node: NodeConfig) {
        val nodeType = node.type
        name.setText(nodeType.name)
        name.setCompoundDrawablesWithIntrinsicBounds(nodeType.icon, 0, 0, 0)

        inputAdapter.setPorts(node.inputs)
        outputAdapter.setPorts(node.outputs)
        itemView.header_input.visibility = if (node.inputs.isEmpty()) View.GONE else View.VISIBLE
        itemView.header_output.visibility = if (node.outputs.isEmpty()) View.GONE else View.VISIBLE
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
                    graphEditor.setSelectedPort(portConfig)
                    nodeAdapter.notifyDataSetChanged()
                    when (it.direction) {
                        PortConfig.DIRECTION_INPUT -> {
                            val openPorts = graphEditor.getOpenOutputsForType(it.dataType)
                            Log.d(TAG, openPorts.toString())
                        }
                        PortConfig.DIRECTION_OUTPUT -> {
                            val openPorts = graphEditor.getOpenInputsForType(it.dataType)
                            Log.d(TAG, openPorts.toString())
                        }
                    }
                    null
                }
            }
        }

        fun bind(portConfig: PortConfig, startAligned: Boolean) {
            this.portConfig = portConfig
            val dataType = portConfig.dataType
            name.setText(dataType.name)
            if (startAligned) {
                name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                name.setCompoundDrawablesWithIntrinsicBounds(dataType.icon, 0, 0, 0)
            } else {
                name.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                name.setCompoundDrawablesWithIntrinsicBounds(0, 0, dataType.icon, 0)
            }
            Log.d(TAG, "port ${portConfig.id} ${graphEditor.getPortState(portConfig)}")
            when(graphEditor.getPortState(portConfig)) {
                PortState.Unconnected -> itemView.setBackgroundColor(Color.BLACK)
                PortState.Connected -> itemView.setBackgroundColor(Color.BLUE)
                PortState.SelectedUnconnected -> itemView.setBackgroundColor(Color.CYAN)
                PortState.SelectedConnected -> itemView.setBackgroundColor(Color.MAGENTA)
                PortState.EligibleToConnect -> itemView.setBackgroundColor(Color.GREEN)
                PortState.EligibleToDisconnect -> itemView.setBackgroundColor(Color.RED)
            }
        }
    }
}

private val TAG = NodeAdapter::class.java.simpleName