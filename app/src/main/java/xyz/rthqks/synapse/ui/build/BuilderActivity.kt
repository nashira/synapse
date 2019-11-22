package xyz.rthqks.synapse.ui.build

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_builder.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.logic.GraphConfigEditor
import javax.inject.Inject

class BuilderActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_builder)

        viewModel = ViewModelProvider(this, viewModelFactory)[BuilderViewModel::class.java]
        Log.d(TAG, "viewModel $viewModel")
        viewModel.onSwipeEvent.observe(this, Observer { consumable ->
            consumable.consume()?.let {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> view_pager.beginFakeDrag()
                    MotionEvent.ACTION_MOVE -> view_pager.fakeDragBy(it.x)
                    MotionEvent.ACTION_UP -> view_pager.endFakeDrag()
                    else -> true
                }
            }
        })

        val nodeAdapter = NodeAdapter(this)
        view_pager.adapter = nodeAdapter
        view_pager.isUserInputEnabled = false

        savedInstanceState ?: run {
            val graphId = intent.getIntExtra(GRAPH_ID, -1)
            viewModel.setGraphId(graphId)
        }

        viewModel.graphChannel.observe(this, Observer {
            nodeAdapter.graphConfigEditor = it
            nodeAdapter.notifyDataSetChanged()
        })
    }

    companion object {
        const val TAG = "BuilderActivity"
        const val GRAPH_ID = "graph_id"

        fun getIntent(activity: Activity, graphId: Int = -1): Intent =
            Intent(activity, BuilderActivity::class.java).also {
                it.putExtra(GRAPH_ID, graphId)
            }
    }
}

class NodeAdapter(
    activity: AppCompatActivity
) : FragmentStateAdapter(activity) {
    var graphConfigEditor: GraphConfigEditor? = null

    override fun getItemCount(): Int {
        return graphConfigEditor?.nodes?.size ?: 0
    }

    override fun createFragment(position: Int): Fragment {
        val nodes = graphConfigEditor?.nodes!!.values.toList()
        return NodeFragment.newInstance(nodes[position].id)
    }
}