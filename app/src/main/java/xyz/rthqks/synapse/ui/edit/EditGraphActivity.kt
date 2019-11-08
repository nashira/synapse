package xyz.rthqks.synapse.ui.edit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_edit.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.NodeType
import javax.inject.Inject


class GraphEditActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_edit)

        graphViewModel = ViewModelProviders.of(this, viewModelFactory)[EditGraphViewModel::class.java]

        savedInstanceState ?: run {
            val graphId = intent.getIntExtra(GRAPH_ID, -1)
            graphViewModel.setGraphId(graphId)

            supportFragmentManager.commit {
                add(R.id.content, EditGraphFragment())
            }
        }

        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottom_sheet.layoutManager = GridLayoutManager(this, 4)
        bottom_sheet.adapter = AddNodeAdapter {
            Log.d(TAG, "clicked ${getText(it.name)}")
            graphViewModel.addNodeType(it)
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        graphViewModel.onAddNodeClicked.observe(this, Observer {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        })
    }

    companion object {
        private val TAG = GraphEditActivity::class.java.simpleName
        private const val GRAPH_ID = "graph_id"

        fun getIntent(activity: Activity, graphId: Int = -1): Intent {
            return Intent(activity, GraphEditActivity::class.java).also {
                it.putExtra(GRAPH_ID, graphId)
            }
        }
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

    override fun getItemCount(): Int = TYPES.size

    override fun onBindViewHolder(holder: AddNodeViewHolder, position: Int) {
        holder.onBind(TYPES[position])
    }

    companion object {
        private val TYPES = listOf(
            NodeType.Camera,
            NodeType.FrameDifference,
            NodeType.GrayscaleFilter,
            NodeType.BlurFilter,
            NodeType.SparkleFilter,
            NodeType.Microphone,
            NodeType.AudioWaveform,
//            NodeType.Image,
//            NodeType.AudioFile,
//            NodeType.VideoFile,
            NodeType.LutFilter,
//            NodeType.ShaderFilter,
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
            node?.let { nt -> itemClick(nt) }
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