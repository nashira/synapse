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
import xyz.rthqks.synapse.logic.Node
import xyz.rthqks.synapse.ui.build.BuilderActivity.Companion.TAG
import xyz.rthqks.synapse.ui.exec.ExecGraphActivity
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
            consumable.consume()?.apply {
                when (action) {
                    MotionEvent.ACTION_DOWN -> view_pager.beginFakeDrag()
                    MotionEvent.ACTION_MOVE -> view_pager.fakeDragBy(x)
                    MotionEvent.ACTION_UP -> view_pager.endFakeDrag()
                }
            }
        })

        val nodeAdapter = NodeAdapter(this)
        view_pager.adapter = nodeAdapter
        view_pager.isUserInputEnabled = false

//        view_pager.setPageTransformer { page, position ->
////            Log.d(TAG, "page $page $position")
//            page.translationX = if (position < 0) -page.width * position else 0f
//        }

        view_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_IDLE -> view_pager.post {
                        Log.d(TAG, "scroll idle")
                        viewModel.updateCurrentItem(view_pager.currentItem)
                    }
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        Log.d(TAG, "scroll dragging")
                    }
                    ViewPager2.SCROLL_STATE_SETTLING -> {
                        Log.d(TAG, "scroll settling")
                    }
                }
            }
        })

        savedInstanceState ?: run {
            val graphId = intent.getIntExtra(GRAPH_ID, -1)
            viewModel.setGraphId(graphId)
        }

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_node -> {
                    viewModel.onAddNode()
                }
                R.id.delete_node -> {
                    onDeleteNode()
                }
                R.id.delete_graph -> {
                    viewModel.showFirstNode()
                }
                R.id.jump_to_node -> {
                    onJumpToNode()
                }
                R.id.cancel -> {
                    viewModel.cancelConnection()
                }
                R.id.execute -> {
                    startActivity(
                        ExecGraphActivity.getIntent(this, viewModel.graph.id)
                    )
                }
            }
            true
        }

        viewModel.titleChannel.observe(this, Observer {
            toolbar.setTitle(it)
        })

        viewModel.menuChannel.observe(this, Observer {
            toolbar.menu.clear()
            toolbar.inflateMenu(it)
        })

        viewModel.nodesChannel.observe(this, Observer {
            Log.d(TAG, it.toString())
            nodeAdapter.setState(it)
            if (view_pager.currentItem != it.currentItem) {
                view_pager.setCurrentItem(it.currentItem, it.animate)
            }
        })
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopExecution()
    }

    private fun onJumpToNode() {
        val dialog = NodeListDialog()
        dialog.listener = {
            Log.d(TAG, "onJump $it")
            viewModel.jumpToNode(it)
        }
        dialog.show(supportFragmentManager, null)
    }

    private fun onDeleteNode() {
        val dialog = ConfirmDialog()
        dialog.listener = {
            Log.d(TAG, "onDelete $it")
            if (it) {
                viewModel.deleteNode()
            }
        }
        dialog.show(supportFragmentManager, null)
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
    val nodes = mutableListOf<Node>()

    override fun getItemCount(): Int = nodes.size

    override fun getItemId(position: Int): Long {
        return nodes[position].id.toLong()
    }

    override fun createFragment(position: Int): Fragment {
        val node = nodes[position]
        return when (node.type) {
            Node.Type.Properties -> GraphFragment.newInstance()
            Node.Type.Creation,
            Node.Type.Connection -> ConnectionFragment.newInstance()
            else -> NodeFragment.newInstance(node.id)
        }
    }

    fun setState(adapterState: AdapterState<Node>) {
        val update = adapterState.items != nodes
        if (update) {
            Log.d(TAG, "updating adapter items")
            nodes.clear()
            nodes.addAll(adapterState.items)
            notifyDataSetChanged()
        }
    }
}