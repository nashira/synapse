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
import androidx.viewpager2.widget.ViewPager2
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_builder.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.NodeData
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

        view_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    view_pager.post {
                        viewModel.onViewPagerIdle(view_pager)
                    }
                }
            }
        })

        savedInstanceState ?: run {
            val graphId = intent.getIntExtra(GRAPH_ID, -1)
            viewModel.setGraphId(graphId)
        }

        viewModel.nodesChannel.observe(this, Observer {
            Log.d(TAG, it.toString())
            nodeAdapter.setState(it)
            if (view_pager.currentItem != it.currentItem) {
                view_pager.setCurrentItem(it.currentItem, false)
            }
        })

        viewModel.graphChannel.observe(this, Observer {
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
    val nodes = mutableListOf<NodeData>()

    override fun getItemCount(): Int = nodes.size

    override fun getItemId(position: Int): Long {
        return nodes[position].id.toLong()
    }

    override fun createFragment(position: Int): Fragment {
        return NodeFragment.newInstance(nodes[position].id)
    }

    fun settle(currentItem: Int) {
//        when (currentItem) {
//            0 -> {
//                nodes.add(0, nodes.removeAt(2))
//            }
//            2 -> {
//                nodes.add(2, nodes.removeAt(0))
//            }
//        }
    }

    fun setState(adapterState: AdapterState<NodeData>) {
        val update = adapterState.items != nodes
        this.nodes.clear()
        this.nodes.addAll(adapterState.items)
        if (update) {
            notifyDataSetChanged()
        }
    }
}