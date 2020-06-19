package com.rthqks.synapse.build

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.ui.NodeUi
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_network.*
import kotlinx.android.synthetic.main.layout_node_list_item.view.*
import javax.inject.Inject

class NetworkFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[BuilderViewModel::class.java]

        val nodeAdapter = NodeAdapter { l, n ->
            viewModel.setNodeId(n.id)
            true
        }
        node_list.adapter = nodeAdapter
        viewModel.networkChannel.observe(viewLifecycleOwner, Observer {
            nodeAdapter.setNodes(it.getNodes())
        })
    }

    companion object {
        const val TAG = "NetworkFragment"
        const val OPEN_DOC_REQUEST = 431
        fun newInstance(): NetworkFragment = NetworkFragment()
    }
}

private class NodeAdapter(
    private val clickListener: (Boolean, Node) -> Boolean
) : RecyclerView.Adapter<NodeViewHolder>() {
    private val nodes = mutableListOf<Node>()

    fun setNodes(nodes: Collection<Node>) {
        this.nodes.clear()
        this.nodes.addAll(nodes)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.layout_node_list_item,
            parent,
            false
        )
        return NodeViewHolder(view, clickListener)
    }

    override fun getItemCount() = nodes.size

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(nodes[position])
    }
}

private class NodeViewHolder(
    itemView: View,
    clickListener: (Boolean, Node) -> Boolean
) : RecyclerView.ViewHolder(itemView) {
    private val icon = itemView.icon_view
    private val label = itemView.title_view
    private var node: Node? = null

    init {
        itemView.setOnClickListener {
            clickListener(false, node!!)
        }
    }

    fun bind(node: Node) {
        this.node = node
        val ui = NodeUi[node.type]
        label.setText(ui.title)
        icon.setImageResource(ui.icon)
    }
}