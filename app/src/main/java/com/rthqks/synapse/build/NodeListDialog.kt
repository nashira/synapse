package com.rthqks.synapse.build

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.flow.logic.Node
import com.rthqks.synapse.ui.NodeUi
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class NodeListDialog : DialogFragment() {
    var listener: ((Node) -> Unit)? = null
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private val adapter = NodeAdapter()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        AndroidSupportInjection.inject(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[BuilderViewModel::class.java]
        viewModel.networkChannel.value?.let {
            adapter.setNodes(it.getNodes())
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d("NodeList", "onCreateDialog")
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_node_list, null) as RecyclerView
        view.layoutManager = LinearLayoutManager(context)
        view.adapter = adapter
        return AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.menu_title_jump_to_node)
            setView(view)
        }.create()
    }

    inner class NodeAdapter: RecyclerView.Adapter<NodeHolder>() {
        private val nodes = mutableListOf<Node>()

        fun setNodes(nodes: Collection<Node>) {
            this.nodes.clear()
            this.nodes.addAll(nodes)
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_node_list_item, parent, false)
            return NodeHolder(view)
        }

        override fun getItemCount(): Int {
            return nodes.size
        }

        override fun onBindViewHolder(holder: NodeHolder, position: Int) {
            val node = nodes[position]
            holder.bind(node)
        }
    }

    inner class NodeHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon = itemView.findViewById<ImageView>(R.id.icon_view)
        private val label = itemView.findViewById<TextView>(R.id.title_view)
        private var node: Node? = null
        init {
            itemView.setOnClickListener {
                node?.let { n -> listener?.invoke(n) }
                dismiss()
            }
        }

        fun bind(node: Node) {
            this.node = node
            val def = NodeUi[node.type]
            icon.setImageResource(def.icon)
            label.setText(def.title)
        }
    }
}