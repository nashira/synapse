package com.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isEmpty
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Connector
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.Port
import com.rthqks.synapse.logic.Property
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*
import kotlinx.android.synthetic.main.layout_property.view.*
import javax.inject.Inject

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private lateinit var touchMediator: TouchMediator
    private lateinit var inputsAdapter: PortsAdapter
    private lateinit var outputsAdapter: PortsAdapter
    private lateinit var propertiesAdapter: PropertiesAdapter
    private lateinit var propertyBinder: PropertyBinder
    private var nodeId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeId = arguments?.getInt(ARG_NODE_ID) ?: -1
//        Log.d(TAG, "onCreate $nodeId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
//        Log.d(TAG, "onCreateView $nodeId")
        return inflater.inflate(R.layout.fragment_node, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        Log.d(TAG, "onViewCreated $nodeId")
        inputsAdapter = PortsAdapter(true)
        outputsAdapter = PortsAdapter(false)
        inputs_list.adapter = inputsAdapter
        outputs_list.adapter = outputsAdapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
//        Log.d(TAG, "onActivityCreated $nodeId")
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        touchMediator = TouchMediator(context!!, viewModel::swipeEvent)
        propertyBinder = PropertyBinder(tool_main) {
            Log.d(TAG, "onChange ${it.key.name} ${it.value}")
            viewModel.onPropertyChange(nodeId, it)
        }

        viewModel.graph.getNode(nodeId)?.let {
            propertiesAdapter = PropertiesAdapter(it) { key, selected, view ->
                for (i in 0 until tool_list.childCount) {
                    tool_list.findViewHolderForAdapterPosition(i)?.let {
                        if (it.itemView != view) it.itemView.isSelected = false
                    }
                }
                if (selected) {
                    propertyBinder.show(it.properties.find(key)!!)
                } else {
                    propertyBinder.hide()
                }
            }
            tool_list.adapter = propertiesAdapter
        }

        viewModel.graphChannel.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "graph change $nodeId ${viewModel.graph.getNode(nodeId)}")
            viewModel.graph.getNode(nodeId)?.let {
                reloadConnectors()
                viewModel.setSurfaceView(nodeId, surface_view)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause $nodeId")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume $nodeId")

        val node = viewModel.getNode(nodeId)
        viewModel.setTitle(node.type.title)
        viewModel.setMenu(R.menu.fragment_node)
        reloadConnectors()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart $nodeId")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop $nodeId")
    }

    private fun reloadConnectors() {
        Log.d(TAG, "reloadConnectors $nodeId")
        val graph = viewModel.graph
        val connectors = graph.getConnectors(nodeId).groupBy { it.port.output }
        inputsAdapter.setPorts(connectors[false] ?: emptyList())
        outputsAdapter.setPorts(connectors[true] ?: emptyList())
    }

    fun onConnectorTouch(connector: Connector) {
        Log.d(TAG, "touch $connector")
        viewModel.preparePortSwipe(connector)
    }

    fun onConnectorClick(connector: Connector) {
        Log.d(TAG, "click $connector")
    }

    fun onConnectorLongClick(view: View, connector: Connector) {
        Log.d(TAG, "long click $connector")
        val menu = PopupMenu(context!!, view)
        menu.inflate(R.menu.layout_connector)

        if (connector.edge == null) {
            menu.menu.findItem(R.id.delete_connection)?.isVisible = false
        }

        if (!connector.port.output) {
            menu.menu.findItem(R.id.add_connection)?.isVisible = false
        }

        if (menu.menu.isEmpty()) {
            return
        }

        try {
            // TODO: remove when support library adds this
            val field = PopupMenu::class.java.getDeclaredField("mPopup").also {
                it.isAccessible = true
            }.get(menu) as MenuPopupHelper
            field.setForceShowIcon(true)
        } catch (e: Throwable) {
            Log.w(TAG, "error forcing icons visible")
        }

        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.delete_connection -> {
                    connector.edge?.let { it1 ->
                        viewModel.deleteEdge(it1)
                        reloadConnectors()
                    }
                }
                R.id.add_connection -> {
                    viewModel.startConnection(connector)
                }
            }
            true
        }

        menu.show()
    }

    class PropertiesAdapter(
        private val node: Node,
        private val onSelected: (Property.Key<*>, Boolean, View) -> Unit
    ) : RecyclerView.Adapter<PropertyViewHolder>() {
        private val keys = node.properties.keys.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.layout_property, parent, false)
            return PropertyViewHolder(view) { position ->
                val selected = !view.isSelected
                onSelected(keys[position], selected, view)
                view.isSelected = selected
            }
        }

        override fun getItemCount(): Int = keys.size

        override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
            val key = keys[position]
            val property = node.properties.find(key)!!
            holder.bind(key, property)
            Log.d(TAG, "onBind $position $key $property")
        }
    }

    class PropertyViewHolder(
        itemView: View, clickListener: (position: Int) -> Unit
    ) :
        RecyclerView.ViewHolder(itemView) {
        private val iconView = itemView.icon

        init {
            itemView.setOnClickListener {
                clickListener(adapterPosition)
            }
        }

        fun bind(key: Property.Key<*>, property: Property<*>) {
            itemView.isSelected = false
            iconView.setImageResource(property.type.icon)
        }
    }

    inner class PortsAdapter(
        private val isStartAligned: Boolean
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
                    return o.edge == n.edge
                }

            })
            this.ports.clear()
            this.ports.addAll(ports)
            result.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.layout_port_fragment_node,
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
        private val button = itemView.button
        private val label = itemView.label
        private val arrowForward = itemView.arrow_forward
        private val arrowBackward = itemView.arrow_backward
        private var connector: Connector? = null

        init {
            button.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    connector?.let { it1 -> onConnectorTouch(it1) }
                }
                touchMediator.onTouch(v, event, connector)
            }
            button.setOnClickListener {
                connector?.let { it1 -> onConnectorClick(it1) }
            }
            button.setOnLongClickListener {
                connector?.let { it1 -> onConnectorLongClick(it, it1) }
                true
            }
        }

        fun bind(connector: Connector, startAligned: Boolean) {
            this.connector = connector
            connector.edge?.let {
                val otherPort =
                    if (startAligned) viewModel.getConnector(it.fromNodeId, it.fromPortId).port
                    else viewModel.getConnector(it.toNodeId, it.toPortId).port
                label.text = "${connector.port.name} (${otherPort.name})"
            } ?: run {
                label.text = connector.port.name
            }

            when (connector.port.type) {
                Port.Type.Audio -> button.setImageResource(R.drawable.ic_speaker)
                Port.Type.Video -> button.setImageResource(R.drawable.ic_display)
            }

            if (!startAligned) {
                arrowBackward.visibility = View.GONE
                arrowForward.visibility = View.VISIBLE
            } else {
                arrowBackward.visibility = View.VISIBLE
                arrowForward.visibility = View.GONE
            }
        }
    }

    companion object {
        const val TAG = "NodeFragment"
        const val ARG_NODE_ID = "node_id"
        fun newInstance(id: Int): NodeFragment {
            val args = Bundle()
            args.putInt(ARG_NODE_ID, id)
            val fragment = NodeFragment()
            fragment.arguments = args
            return fragment
        }
    }
}