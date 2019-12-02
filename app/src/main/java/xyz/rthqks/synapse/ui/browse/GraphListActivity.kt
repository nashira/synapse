package xyz.rthqks.synapse.ui.browse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_list.*
import kotlinx.android.synthetic.main.graph_list_item.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.ui.build.BuilderActivity
import xyz.rthqks.synapse.ui.edit.GraphEditActivity
import javax.inject.Inject

class GraphListActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: GraphListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_list)
        toolbar.setTitle(R.string.title_graph_list)
        viewModel = ViewModelProviders.of(this, viewModelFactory)[GraphListViewModel::class.java]

        button_new_graph.setOnClickListener {
            startActivity(GraphEditActivity.getIntent(this))
        }

        button_new_graph.setOnLongClickListener {
            Intent(this, BuilderActivity::class.java).also {
                startActivity(it)
            }
            true
        }

        val graphAdapter = GraphAdapter()
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = graphAdapter

        viewModel.graphList.observe(this, Observer {
            Log.d(TAG, "graphs: $it")
            graphAdapter.setGraphs(it)
        })

        graphAdapter.onItemClick { item, longClick ->
            if (longClick) {
                startActivity(
                    BuilderActivity.getIntent(this, item.id)
                )
            } else {
                startActivity(
                    GraphEditActivity.getIntent(this, item.id)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadGraphs()
    }

    companion object {
        private val TAG = GraphListActivity::class.java.simpleName
    }
}

class GraphAdapter : RecyclerView.Adapter<GraphViewHolder>() {
    private val graphs = mutableListOf<Graph>()
    private var itemClickListener: ((Graph, Boolean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GraphViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.graph_list_item, parent, false)
        return GraphViewHolder(view) { item, longClick ->
            itemClickListener?.invoke(item, longClick)
        }
    }

    override fun getItemCount(): Int = graphs.size

    override fun onBindViewHolder(holder: GraphViewHolder, position: Int) {
        holder.bind(graphs[position])
    }

    fun setGraphs(list: List<Graph>) {
        graphs.clear()
        graphs.addAll(list)
        notifyDataSetChanged()
    }

    fun onItemClick(function: (Graph, Boolean) -> Unit) {
        itemClickListener = function
    }
}

class GraphViewHolder(
    itemView: View,
    itemClick: (Graph, Boolean) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val name = itemView.name
    private var graph: Graph? = null

    init {
        itemView.setOnClickListener {
            Log.d("graph", "clicked $graph")
            graph?.let {
                itemClick(it, false)
            }
        }
        itemView.setOnLongClickListener {
            Log.d("graph", "clicked $graph")
            graph?.let {
                itemClick(it, true)
            }
            true
        }
    }

    fun bind(graph: Graph) {
        this.graph = graph
        name.text = graph.name
    }
}