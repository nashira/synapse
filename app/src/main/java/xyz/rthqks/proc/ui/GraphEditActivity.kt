package xyz.rthqks.proc.ui

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_edit.*
import kotlinx.android.synthetic.main.content_edit_graph.*
import kotlinx.android.synthetic.main.node_edit_item.view.*
import xyz.rthqks.proc.R
import xyz.rthqks.proc.data.PortConfig
import xyz.rthqks.proc.data.NodeConfig
import javax.inject.Inject
import kotlin.math.round


class GraphEditActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: GraphEditViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_edit)
        setSupportActionBar(toolbar)
        viewModel = ViewModelProviders.of(this, viewModelFactory)[GraphEditViewModel::class.java]

        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        button_add_node.setOnClickListener { view ->
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        val nodeAdapter = NodeAdapter()

        bottom_sheet.layoutManager = GridLayoutManager(this, 4)
        bottom_sheet.adapter = AddNodeAdapter {
            Log.d(TAG, "clicked ${getText(it.name)}")
            nodeAdapter.addNode(it)
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        val nodeLayoutManager = LinearLayoutManager(this)
        val dp8 = round(resources.displayMetrics.density * 8).toInt()
        val dp84 = round(resources.displayMetrics.density * 84).toInt()
        recycler_view.layoutManager = nodeLayoutManager
        recycler_view.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.top = if (parent.getChildLayoutPosition(view) > 0) 0 else dp8
                outRect.bottom = if (parent.getChildAdapterPosition(view) == nodeAdapter.itemCount - 1) dp84 else dp8
                outRect.left = dp8
                outRect.right = dp8
            }
        })

        recycler_view.adapter = nodeAdapter
    }

    companion object {
        private val TAG = GraphEditActivity::class.java.simpleName
    }
}

class NodeViewHolder(
    itemView: View,
    viewPool: RecyclerView.RecycledViewPool
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

        inputMenu.layoutManager =
            LinearLayoutManager(itemView.context)
        outputMenu.layoutManager =
            LinearLayoutManager(itemView.context)

        inputMenu.adapter = inputAdapter
        outputMenu.adapter = outputAdapter
    }

    fun bind(nodeConfig: NodeConfig) {
        name.setText(nodeConfig.name)
        name.setCompoundDrawablesWithIntrinsicBounds(nodeConfig.icon, 0, 0, 0)

        inputAdapter.setPorts(nodeConfig.inputs)
        outputAdapter.setPorts(nodeConfig.outputs)
    }
}

class PortsAdapter(
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
        return PortViewHolder(view, isStartAligned)
    }

    override fun getItemCount() = ports.size

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(ports[position])
    }

}

class PortViewHolder(
    itemView: View,
    private val isStartAligned: Boolean
) : RecyclerView.ViewHolder(itemView) {
    private val name = itemView.name

    init {
        itemView.setOnClickListener {
            Log.d("Ports", "clicked ${name.text} $itemView")
        }
    }

    fun bind(portConfig: PortConfig) {
        name.setText(portConfig.name)
        if (isStartAligned) {
            name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            name.setCompoundDrawablesWithIntrinsicBounds(portConfig.icon, 0, 0, 0)
        } else {
            name.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            name.setCompoundDrawablesWithIntrinsicBounds(0, 0, portConfig.icon, 0)
        }
    }

}

class NodeAdapter : RecyclerView.Adapter<NodeViewHolder>() {

    private val nodes = mutableListOf<NodeConfig>()
    private val viewPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.node_edit_item,
            parent,
            false
        )
        return NodeViewHolder(view, viewPool)
    }

    override fun getItemCount(): Int = nodes.size

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(nodes[position])
    }

    fun addNode(nodeConfig: NodeConfig) {
        nodes.add(nodeConfig)
        if (nodes.size > 1) {
            notifyItemChanged(nodes.size - 2)
        }
        notifyItemInserted(nodes.size - 1)
    }
}

class AddNodeAdapter(
    private val itemClick: (NodeConfig) -> Unit
) : RecyclerView.Adapter<AddNodeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddNodeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.bottomsheet_node_item, parent, false)
        return AddNodeViewHolder(view, itemClick)
    }

    override fun getItemCount(): Int = NodeConfig.SIZE

    override fun onBindViewHolder(holder: AddNodeViewHolder, position: Int) {
        holder.onBind(TYPES[position])
    }

    companion object {
        private val TYPES = listOf(
            NodeConfig.Camera(),
            NodeConfig.Microphone(),
            NodeConfig.Image(),
            NodeConfig.AudioFile(),
            NodeConfig.VideoFile(),
            NodeConfig.ColorFilter(),
            NodeConfig.ShaderFilter(),
            NodeConfig.Screen,
            NodeConfig.Speakers
        )
    }
}

class AddNodeViewHolder(
    itemView: View,
    itemClick: (NodeConfig) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val textView = itemView.findViewById<TextView>(R.id.text)
    private val iconView = itemView.findViewById<ImageView>(R.id.icon)
    private var nodeConfig: NodeConfig? = null

    init {
        itemView.setOnClickListener {
            nodeConfig?.let { nt -> itemClick.invoke(nt) }
        }
    }

    fun onBind(nodeConfig: NodeConfig) {
        this.nodeConfig = nodeConfig
        textView.setText(nodeConfig.name)
        iconView.setImageResource(nodeConfig.icon)
    }

    companion object {
        private val TAG = AddNodeViewHolder::class.java.simpleName
    }
}