package com.rthqks.synapse.ui.build

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.GetNode
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.synapse.fragment_network.*
import kotlinx.android.synthetic.synapse.layout_node_item.view.*
import javax.inject.Inject
import kotlin.math.max

class NetworkFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private lateinit var propertyBinder: PropertyBinder
    private lateinit var uriProvider: UriProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        uriProvider = UriProvider {
            startActivityForResult(it, OPEN_DOC_REQUEST)
        }
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        viewModel.setTitle(R.string.name_node_type_properties)
        viewModel.setMenu(R.menu.fragment_network)

        val properties = viewModel.network.properties

        propertyBinder = PropertyBinder(properties, tool_list, tool_main, uriProvider) {
            Log.d(TAG, "onChange ${it.key.name} ${it.value}")
            viewModel.onPropertyChange(-1, it, properties)
        }

        val touchMediator = TouchMediator(context!!, viewModel::swipeEvent)

        val connectorAdapter = NodeAdapter({ view: View, event: MotionEvent, node: Node ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (node.type == NodeType.Creation) {
                    viewModel.showFirstNode()
                } else {
                    viewModel.swipeToNode(node)
                }
            }
            touchMediator.onTouch(view, event)
        },{ longClick: Boolean, node: Node ->
//            if (node.type == NodeType.Creation) {
//                viewModel.showFirstNode()
//            } else {
//                viewModel.swipeToNode(node)
//            }
            true
        })

        val sorted = viewModel.getSortedNodeList()
        connectorAdapter.setNodes(sorted)
        node_list.adapter = connectorAdapter

        node_list.setData(sorted, viewModel.network.getLinks())

//        graph.setData(viewModel.network)
//
//        Log.d(TAG, sorted.joinToString())
//        viewModel.viewModelScope.launch {
//            while (isActive) {
//                graph.iterate()
//                delay(30)
//            }
//        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OPEN_DOC_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uriProvider.onActivityResult(activity!!, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            viewModel.setTitle(R.string.name_node_type_properties)
            viewModel.setMenu(R.menu.fragment_network)
        }
        Log.d(TAG, "onResume")
    }

    companion object {
        const val TAG = "NetworkFragment"
        const val OPEN_DOC_REQUEST = 431
        fun newInstance(): NetworkFragment = NetworkFragment()
    }
}

private class NodeAdapter(
    private val touchListener: (View, MotionEvent, Node) -> Boolean,
    private val clickListener: (Boolean, Node) -> Boolean
) : RecyclerView.Adapter<NodeViewHolder>() {
    private val nodes = mutableListOf<Node>()

    fun setNodes(nodes: List<Node>) {
        this.nodes.clear()
        this.nodes.addAll(nodes)
        if (nodes.isEmpty()) {
            this.nodes.add(GetNode(NodeType.Creation))
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.layout_node_item,
            parent,
            false
        )
        return NodeViewHolder(view, touchListener, clickListener)
    }

    override fun getItemCount() = max(1, nodes.size)

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(nodes[position])
    }
}

private class NodeViewHolder(
    itemView: View,
    touchListener: (View, MotionEvent, Node) -> Boolean,
    clickListener: (Boolean, Node) -> Boolean
) : RecyclerView.ViewHolder(itemView) {
    private val button = itemView.button
    private val label = itemView.label
    private var node: Node? = null

    init {
//        itemView.setOnTouchListener { v, event ->
//            touchListener(v, event, node!!)
//        }
        button.setOnTouchListener { v, event ->
            touchListener(v, event, node!!)
        }
        button.setOnClickListener {
            clickListener(false, node!!)
        }
        button.setOnLongClickListener {
            clickListener(true, node!!)
        }
    }

    fun bind(node: Node) {
        this.node = node
        label.setText(node.type.title)
        button.setImageResource(node.type.icon)
    }
}