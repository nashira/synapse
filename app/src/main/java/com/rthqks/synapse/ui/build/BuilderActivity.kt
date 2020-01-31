package com.rthqks.synapse.ui.build

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
import com.google.firebase.analytics.FirebaseAnalytics
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import com.rthqks.synapse.ui.exec.NetworkActivity
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_builder.*
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
        view_pager.offscreenPageLimit = 1

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
            val networkId = intent.getIntExtra(NETWORK_ID, -1)
            viewModel.setNetworkId(networkId)
            FirebaseAnalytics.getInstance(this)
                .logEvent("EditNetwork", Bundle().also { it.putInt("network_id", networkId) })
        }

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_node -> viewModel.onAddNode()
                R.id.delete_node -> onDeleteNode()
                R.id.delete_network -> onDeleteNetwork()
                R.id.jump_to_node -> onJumpToNode()
                R.id.jump_to_network -> viewModel.jumpToNode(BuilderViewModel.PROPERTIES_NODE)
                R.id.cancel -> viewModel.cancelConnection()
                R.id.execute -> startActivity(
                    NetworkActivity.getIntent(this, viewModel.network.id)
                )
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

    override fun onStart() {
        super.onStart()
        viewModel.updateStartState()
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
        ConfirmDialog(
            R.string.menu_title_delete_node,
            R.string.button_cancel,
            R.string.confirm_delete
        ) {
            Log.d(TAG, "onDelete $it")
            if (it) {
                viewModel.deleteNode()
            }
        }.show(supportFragmentManager, null)
    }

    private fun onDeleteNetwork() {
        ConfirmDialog(
            R.string.menu_title_delete_network,
            R.string.button_cancel,
            R.string.confirm_delete
        ) {
            Log.d(TAG, "onDelete $it")
            if (it) {
                viewModel.deleteNetwork()
                finish()
            }
        }.show(supportFragmentManager, null)
    }

    override fun onBackPressed() {
        if (!viewModel.onBackPressed()) {
            super.onBackPressed()
        }
    }

    companion object {
        const val TAG = "BuilderActivity"
        const val NETWORK_ID = "network_id"

        fun getIntent(activity: Activity, networkId: Int = -1): Intent =
            Intent(activity, BuilderActivity::class.java).also {
                it.putExtra(NETWORK_ID, networkId)
            }
    }

    private class NodeAdapter(
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
                NodeType.Properties -> NetworkFragment.newInstance()
                NodeType.Creation,
                NodeType.Connection -> ConnectionFragment.newInstance()
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
}