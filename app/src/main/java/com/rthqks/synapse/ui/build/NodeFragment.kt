package com.rthqks.synapse.ui.build

import android.app.Activity
import android.content.Intent
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
import com.rthqks.synapse.logic.Port
import com.rthqks.synapse.ui.build.NetworkFragment.Companion.OPEN_DOC_REQUEST
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*
import javax.inject.Inject

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private lateinit var touchMediator: TouchMediator
    private lateinit var inputsAdapter: PortsAdapter
    private lateinit var outputsAdapter: PortsAdapter
    private lateinit var propertyBinder: PropertyBinder
    private lateinit var uriProvider: UriProvider
    private var nodeId = -1
    private var selectedPortId: String? = null


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
        uriProvider = UriProvider {
            startActivityForResult(it, OPEN_DOC_REQUEST)
        }
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        touchMediator = TouchMediator(context!!, viewModel::swipeEvent)

        viewModel.network.getNode(nodeId)?.let { node ->
            propertyBinder = PropertyBinder(node.properties, tool_list, tool_main, uriProvider) {
                Log.d(TAG, "onChange ${it.key.name} ${it.value}")
                viewModel.onPropertyChange(nodeId, it, node.properties)
            }
        }

        viewModel.networkChannel.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "network change $nodeId ${viewModel.network.getNode(nodeId)}")
            viewModel.network.getNode(nodeId)?.let {
                reloadConnectors()
            }
        })

        viewModel.setSurfaceView(nodeId, selectedPortId, surface_view)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OPEN_DOC_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uriProvider.onActivityResult(activity!!, it) }
        }
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
        val network = viewModel.network
        val connectors = network.getConnectors(nodeId).groupBy { it.port.output }
        inputsAdapter.setPorts(connectors[false] ?: emptyList())
        outputsAdapter.setPorts(connectors[true] ?: emptyList())
    }

    fun onConnectorTouch(connector: Connector) {
        Log.d(TAG, "touch $connector")
        viewModel.preparePortSwipe(connector)
    }

    fun onConnectorClick(connector: Connector) {
        Log.d(TAG, "click $connector")
        // TODO: find a way to enable previewing different outputs
//        selectedPortId = connector.port.id
//        viewModel.setSurfaceView(nodeId, selectedPortId, surface_view)
    }

    fun onConnectorLongClick(view: View, connector: Connector) {
        Log.d(TAG, "long click $connector")
        val menu = PopupMenu(context!!, view)
        menu.inflate(R.menu.layout_connector)

        if (connector.link == null) {
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
                    connector.link?.let { it1 ->
                        viewModel.deleteLink(it1)
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

    private inner class PortsAdapter(
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
                    return o.link == n.link
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

    private inner class PortViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val button = itemView.button
        private val label = itemView.label
        private val arrowForward = itemView.arrow_forward
        private val arrowBackward = itemView.title
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
            connector.link?.let {
                val otherPort =
                    if (startAligned) viewModel.getConnector(it.fromNodeId, it.fromPortId).port
                    else viewModel.getConnector(it.toNodeId, it.toPortId).port
                label.text = "${connector.port.name} (${otherPort.name})"
                button.setBackgroundResource(R.drawable.selectable_accent)
            } ?: run {
                button.setBackgroundResource(R.drawable.selectable_grey)
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