package xyz.rthqks.proc.ui.edit

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_edit.*
import kotlinx.android.synthetic.main.content_edit_graph.*
import xyz.rthqks.proc.R
import xyz.rthqks.proc.data.NodeType
import xyz.rthqks.proc.logic.GraphConfigEditor
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


        viewModel.graphChannel.observe(this, Observer {
            Log.d(TAG, it.toString())
            Log.d(TAG, it.nodes.toString())
            Log.d(TAG, it.edges.toString())
            setupUI(it)
        })
    }

    private fun setupUI(graphEditor: GraphConfigEditor) {
        val nodeAdapter = NodeAdapter(graphEditor)

        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        button_add_node.setOnClickListener { view ->
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        bottom_sheet.layoutManager = GridLayoutManager(this, 4)
        bottom_sheet.adapter = AddNodeAdapter {
            Log.d(TAG, "clicked ${getText(it.name)}")
            viewModel.addNodeType(it)
            nodeAdapter.onNodeAdded()
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
                outRect.bottom =
                    if (parent.getChildAdapterPosition(view) == nodeAdapter.itemCount - 1) dp84 else dp8
                outRect.left = dp8
                outRect.right = dp8
            }
        })

        recycler_view.adapter = nodeAdapter
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                nodeAdapter.moveNode(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })

        touchHelper.attachToRecyclerView(recycler_view)
    }

    companion object {
        private val TAG = GraphEditActivity::class.java.simpleName
    }
}

class AddNodeAdapter(
    private val itemClick: (NodeType) -> Unit
) : RecyclerView.Adapter<AddNodeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddNodeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.bottomsheet_node_item, parent, false)
        return AddNodeViewHolder(view, itemClick)
    }

    override fun getItemCount(): Int = NodeType.SIZE

    override fun onBindViewHolder(holder: AddNodeViewHolder, position: Int) {
        holder.onBind(TYPES[position])
    }

    companion object {
        private val TYPES = listOf(
            NodeType.Camera,
            NodeType.Microphone,
            NodeType.Image,
            NodeType.AudioFile,
            NodeType.VideoFile,
            NodeType.ColorFilter,
            NodeType.ShaderFilter,
            NodeType.Screen,
            NodeType.Speakers
        )
    }
}

class AddNodeViewHolder(
    itemView: View,
    itemClick: (NodeType) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val textView = itemView.findViewById<TextView>(R.id.text)
    private val iconView = itemView.findViewById<ImageView>(R.id.icon)
    private var node: NodeType? = null

    init {
        itemView.setOnClickListener {
            node?.let { nt -> itemClick.invoke(nt) }
        }
    }

    fun onBind(node: NodeType) {
        this.node = node
        textView.setText(node.name)
        iconView.setImageResource(node.icon)
    }

    companion object {
        private val TAG = AddNodeViewHolder::class.java.simpleName
    }
}