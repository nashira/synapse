package xyz.rthqks.proc.ui.edit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_edit.*
import xyz.rthqks.proc.R
import xyz.rthqks.proc.data.NodeType
import javax.inject.Inject


class GraphEditActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_edit)
        setSupportActionBar(toolbar)
        graphViewModel = ViewModelProviders.of(this, viewModelFactory)[EditGraphViewModel::class.java]

        savedInstanceState ?: run {
            supportFragmentManager.commit {
                add(R.id.content, EditGraphFragment())
            }
        }
        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        button_add_node.setOnClickListener { view ->
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        bottom_sheet.layoutManager = GridLayoutManager(this, 4)
        bottom_sheet.adapter = AddNodeAdapter {
            Log.d(TAG, "clicked ${getText(it.name)}")
            graphViewModel.addNodeType(it)
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
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