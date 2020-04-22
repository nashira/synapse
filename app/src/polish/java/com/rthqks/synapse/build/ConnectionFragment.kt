package com.rthqks.synapse.build

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Connector
import com.rthqks.synapse.logic.Network
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.polish.fragment_connection.*
import kotlinx.android.synthetic.polish.layout_connection.view.*
import javax.inject.Inject

class ConnectionFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private val network: Network get() = viewModel.network
    private lateinit var connectionAdapter: ConnectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_connection, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.d(TAG, "onActivityCreated")
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[BuilderViewModel::class.java]
        connectionAdapter = ConnectionAdapter(viewModel, requireContext(), 3)
        val layoutManager = GridLayoutManager(context, 3)
        layoutManager.spanSizeLookup = connectionAdapter.spanSizeLookup
        recycler_view.addItemDecoration(connectionAdapter.itemDecoration)
        recycler_view.layoutManager = layoutManager
        recycler_view.adapter = connectionAdapter

        viewModel.connectionChannel.observe(viewLifecycleOwner, Observer {
//            Log.d(TAG, "changed ${it.node.def} ${it.port.name}")

//            if (it.node.def == NodeDef.Creation) {
//                val connectors = network.getCreationConnectors()
////                viewModel.addConnectionPreview(it, connectors)
//                connectionAdapter.setData(emptyList(), connectors)
//            } else {
//                val openConnectors = network.getOpenConnectors(it)
//                val potentialConnectors = network.getPotentialConnectors(it)
//                viewModel.viewModelScope.launch {
//                    viewModel.addConnectionPreview(it, potentialConnectors)
//                    viewModel.waitForExecutor()
//                    connectionAdapter.setData(openConnectors, potentialConnectors)
//                }
//            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        viewModel.setTitle(R.string.title_connect)
        viewModel.setMenu(R.menu.connection)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    companion object {
        const val TAG = "ConnectionFragment"

        fun newInstance(): ConnectionFragment {
            return ConnectionFragment()
        }
    }
}

class ConnectionAdapter(
    val viewModel: BuilderViewModel,
    context: Context,
    spans: Int
) : RecyclerView.Adapter<ConnectionAdapter.ViewHolder>() {
    private val margin = context.resources.getDimensionPixelSize(R.dimen.connector_margin)

    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (getItemViewType(position) == R.layout.layout_connection) 1 else spans
        }
    }

    val itemDecoration = object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val pos = parent.getChildAdapterPosition(view)
            val item = items[pos]
            if (item is HeaderItem) {
                outRect.set(margin, margin, margin, margin * 2)
            } else {
                val index = (view.layoutParams as GridLayoutManager.LayoutParams).spanIndex % spans
                val left = margin * (spans - index) / spans
                val right = margin * (index + 1) / spans
                outRect.set(left, 0, right, margin)
            }
        }
    }
    private val items = mutableListOf<Item>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.layout_connection -> ItemViewHolder(view)
            else -> HeaderViewHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HeaderItem -> R.layout.layout_connection_header
            else -> R.layout.layout_connection
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun setData(existing: List<Connector>, potential: List<Connector>) {
        items.clear()
        if (existing.isNotEmpty()) {
            items += HeaderItem("Connect an Existing Node")
            items += existing.map { ConnectorItem(it) }
        }
        if (potential.isNotEmpty()) {
            items += HeaderItem("Add a New Node")
            items += potential.map { ConnectorItem(it) }
        }
        notifyDataSetChanged()
    }

    interface Item

    class HeaderItem(
        val text: String
    ) : Item

    class ConnectorItem(
        val connector: Connector
    ) : Item

    abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: Item)
    }

    class HeaderViewHolder(itemView: View) : ViewHolder(itemView) {
        private val title = itemView as TextView

        override fun bind(item: Item) {
            item as HeaderItem
            title.text = item.text
        }
    }

    inner class ItemViewHolder(itemView: View) : ViewHolder(itemView) {
        private val surfaceView = itemView.surface_view
        private val portName = itemView.port_name_view
        private val nodeName = itemView.node_name_view
        private var item: ConnectorItem? = null

        init {
            surfaceView.setOnClickListener {
                item?.let {
                    val node = it.connector.node.let {
                        if (it.id >= Network.COPY_ID_SKIP)
                            it.copy(id = -1)
                        else
                            it
                    }

                    viewModel.completeConnection(
                        Connector(node, it.connector.port)
                    )
                }
            }
        }

        override fun bind(item: Item) {
            this.item = item as ConnectorItem
//            portName.text = item.connector.port.name
//            nodeName.setText(item.connector.node.def.title)
            val id = item.connector.node.id
            Log.d(TAG, "bind $id")
            if (id >= 0) {
                surfaceView.doOnLayout {
                    viewModel.setSurfaceView(id, null, surfaceView)
                }
            }
        }
    }

    companion object {
        const val TAG = "ConnectionFragment"
    }
}