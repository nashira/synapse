package com.rthqks.synapse.build

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.flow.logic.Node
import com.rthqks.flow.logic.Port
import com.rthqks.flow.logic.PortType
import com.rthqks.synapse.databinding.FragmentNetworkBinding
import com.rthqks.synapse.ui.ConfirmDialog
import com.rthqks.synapse.ui.NodeUi
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class NetworkFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private lateinit var binding: FragmentNetworkBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel =
            ViewModelProvider(requireActivity(), viewModelFactory)[BuilderViewModel::class.java]

        val nodeAdapter = NodeAdapter { _, n ->
            viewModel.setNodeId(n.id)
            true
        }
        binding.nodeList.adapter = nodeAdapter
        viewModel.networkChannel.observe(viewLifecycleOwner, Observer {
            nodeAdapter.setNodes(it.getNodes())
            binding.networkName.setText(it.name)
            binding.networkDescription.setText(it.description)
        })

        binding.networkName.setOnEditorActionListener { _, _, _ ->
            binding.networkName.clearFocus()
            hideKeyboard()
            false
        }

        binding.networkDescription.setOnEditorActionListener { _, _, _ ->
            binding.networkDescription.clearFocus()
            hideKeyboard()
            false
        }

        binding.networkName.setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "name focus $hasFocus")
            if (!hasFocus) {
                viewModel.setNetworkName(binding.networkName.text.toString().trim())
            }
        }

        binding.networkDescription.setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "desc focus $hasFocus")
            viewModel.setNetworkDescription(binding.networkDescription.text.toString().trim())
        }

        binding.deleteNetwork.setOnClickListener {
            onDeleteNetwork()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.networkName.windowToken, 0)
    }

    private fun onDeleteNetwork() {
        ConfirmDialog(
            R.string.menu_title_delete_network,
            R.string.button_cancel,
            R.string.confirm_delete
        ) {
            Log.d(TAG, "onDelete $it")
            if (it) {
                requireActivity().finish()
                viewModel.deleteNetwork()
            }
        }.show(parentFragmentManager, null)
    }

    companion object {
        const val TAG = "NetworkFragment"
        const val OPEN_DOC_REQUEST = 431
        fun newInstance(): NetworkFragment = NetworkFragment()
    }
}

private class NodeAdapter(
    private val clickListener: (Boolean, Node) -> Boolean
) : RecyclerView.Adapter<ItemViewHolder>() {
    private val nodes = mutableListOf<Node>()
    private val items = mutableListOf<Item>()

    fun setNodes(nodes: Collection<Node>) {
        this.nodes.clear()
        this.nodes += nodes
        items.clear()
        nodes.forEach { node ->
            items += Item(node)
            node.ports.values.forEach {
                items += Item(node, 1, it)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            viewType,
            parent,
            false
        )
        return NodeViewHolder(view, clickListener)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int = when(items[position].type) {
        0 -> R.layout.layout_node_list_item
        else -> R.layout.layout_port_list_item
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    data class Item(
        val node: Node,
        val type: Int = 0,
        val port: Port? = null
    )
}

private abstract class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: NodeAdapter.Item)
}

private class NodeViewHolder(
    itemView: View,
    clickListener: (Boolean, Node) -> Boolean
) : ItemViewHolder(itemView) {
    private val icon = itemView.findViewById<ImageView>(R.id.icon_view)
    private val label = itemView.findViewById<TextView>(R.id.title_view)
    private var node: Node? = null

    init {
        itemView.setOnClickListener {
            clickListener(false, node!!)
        }
    }

    override fun bind(item: NodeAdapter.Item) {
        node = item.node
        val ui = NodeUi[item.node.type]
        if (item.type == 0) {
            label.setText(ui.title)
            icon.setImageResource(ui.icon)
        } else {
            val port = item.port!!
            label.text = port.key
            when (port.type) {
                PortType.Audio -> icon.setImageResource(R.drawable.ic_volume_2)
                PortType.Video -> icon.setImageResource(R.drawable.ic_image)
                PortType.Texture3D -> icon.setImageResource(R.drawable.ic_layers)
                PortType.Matrix -> icon.setImageResource(R.drawable.ic_box)
            }
        }
    }
}