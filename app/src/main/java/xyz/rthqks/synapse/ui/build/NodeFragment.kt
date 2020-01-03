package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isEmpty
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.logic.Connector
import xyz.rthqks.synapse.logic.Node
import javax.inject.Inject

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private lateinit var touchMediator: TouchMediator
    private lateinit var inputsAdapter: PortsAdapter
    private lateinit var outputsAdapter: PortsAdapter
    private lateinit var propertiesAdapter: PropertiesAdapter
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
        inputs_list.layoutManager = LinearLayoutManager(context)
        outputs_list.layoutManager = LinearLayoutManager(context)
        inputsAdapter = PortsAdapter(true)
        outputsAdapter = PortsAdapter(false)
        inputs_list.adapter = inputsAdapter
        outputs_list.adapter = outputsAdapter

        tool_list.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
//        Log.d(TAG, "onActivityCreated $nodeId")
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        touchMediator = TouchMediator(context!!, viewModel::swipeEvent)

        viewModel.graph.getNode(nodeId)?.let {
            propertiesAdapter = PropertiesAdapter(viewModel, it)
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
        private val viewModel: BuilderViewModel,
        private val node: Node
    ) : RecyclerView.Adapter<PropertyViewHolder>() {
        private val properties = node.properties.keys.toList()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
            return PropertyViewHolder(ImageView(parent.context))
        }

        override fun getItemCount(): Int = properties.size

        override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
            val key = properties[position]
            val property = node.properties[key]
            Log.d(TAG, "onBind $position $key ${property}")
        }
    }

    class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

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
                button.text = "${connector.port.name} (${otherPort.name})"
            } ?: run {
                button.text = connector.port.name
            }
//            button.setImageResource()
            if (!startAligned) {
                button.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_arrow_forward_ios,
                    0
                )
                listOf(button).forEach {
                    (it.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = Gravity.END
                        it.layoutParams = this
                    }
                }
            } else {
                button.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_arrow_forward_ios,
                    0,
                    0,
                    0
                )
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