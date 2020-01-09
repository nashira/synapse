package com.rthqks.synapse.ui.build

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.GetNode
import com.rthqks.synapse.logic.Node
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_graph.*
import kotlinx.android.synthetic.main.layout_port_fragment_graph.view.*
import javax.inject.Inject
import kotlin.math.max

class GraphFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private lateinit var propertyBinder: PropertyBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_graph, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        viewModel.setTitle(R.string.name_node_type_properties)
        viewModel.setMenu(R.menu.fragment_graph)

        setupEditTitle()
        edit_title.setText(viewModel.graph.name)

        button_save.setOnClickListener {
            handleNameSave()
        }

        val properties = viewModel.graph.properties

        propertyBinder = PropertyBinder(properties, tool_list, tool_main) {
            Log.d(TAG, "onChange ${it.key.name} ${it.value}")
            viewModel.onPropertyChange(-1, it, properties)
        }

        val touchMediator = TouchMediator(context!!, viewModel::swipeEvent)

        val connectorAdapter = NodeAdapter()
        val nodes = viewModel.graph.getNodes()
        connectorAdapter.setNodes(nodes)
        node_list.adapter = connectorAdapter

//        swipe_to_nodes.setOnTouchListener { v, event ->
//            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
//                viewModel.showFirstNode()
//            }
//            touchMediator.onTouch(v, event)
//        }

//        val property = ToggleProperty(property_type_toggle)
    }

    private fun setupEditTitle() {
        edit_title.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleNameSave()
                    return true
                }
                return false
            }
        })
    }

    private fun handleNameSave() {
        val imm = context!!.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edit_title.windowToken, 0)
        edit_title.clearFocus()
        viewModel.setGraphName(edit_title.text.toString())
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            viewModel.setTitle(R.string.name_node_type_properties)
            viewModel.setMenu(R.menu.fragment_graph)
        }
        Log.d(TAG, "onResume")
    }

    companion object {
        const val TAG = "GraphFragment"
        fun newInstance(): GraphFragment = GraphFragment()
    }
}

private class NodeAdapter : RecyclerView.Adapter<NodeViewHolder>() {
    private val nodes = mutableListOf<Node>()

    fun setNodes(nodes: List<Node>) {
        this.nodes.clear()
        this.nodes.addAll(nodes)
        if (nodes.isEmpty()) {
            this.nodes.add(GetNode(Node.Type.Creation))
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.layout_port_fragment_graph,
            parent,
            false
        )
        return NodeViewHolder(view)
    }

    override fun getItemCount() = max(1, nodes.size)

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(nodes[position])
    }
}

private class NodeViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {
    private val button = itemView.button
    private val label = itemView.label
    private var node: Node? = null

    init {
//            button.setOnTouchListener { v, event ->
//                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
//                    connector?.let { it1 -> onConnectorTouch(it1) }
//                }
//                touchMediator.onTouch(v, event, connector)
//            }
//            button.setOnClickListener {
//                connector?.let { it1 -> onConnectorClick(it1) }
//            }
//            button.setOnLongClickListener {
//                connector?.let { it1 -> onConnectorLongClick(it, it1) }
//                true
//            }
    }

    fun bind(node: Node) {
        this.node = node
//            connector.edge?.let {
//                val otherPort =
//                    if (startAligned) viewModel.getConnector(it.fromNodeId, it.fromPortId).port
//                    else viewModel.getConnector(it.toNodeId, it.toPortId).port
//                label.text = "${connector.port.name} (${otherPort.name})"
//            } ?: run {
//                label.text = connector.port.name
//            }
        label.setText(node.type.title)
        button.setImageResource(node.type.icon)
    }
}