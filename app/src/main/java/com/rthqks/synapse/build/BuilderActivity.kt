package com.rthqks.synapse.build

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Connector
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.ui.PropertiesAdapter
import com.rthqks.synapse.ui.propertiesUi
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_builder.*
import javax.inject.Inject

class BuilderActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private val inputsAdapter = PortsAdapter(true, this::onPortClick)
    private val outputsAdapter = PortsAdapter(false, this::onPortClick)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_builder)

        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        viewModel = ViewModelProvider(this, viewModelFactory)[BuilderViewModel::class.java]
        Log.d(TAG, "viewModel $viewModel")

        if (savedInstanceState == null) {
            val networkId = intent.getIntExtra(NETWORK_ID, -1)
            viewModel.setNetworkId(networkId)
        }

        val propertiesAdapter = PropertiesAdapter { property, choice ->
            Log.d(TAG, "property changed: ${property.key.name}: ${property.value}")
        }
        properties_list.adapter = propertiesAdapter

        inputs_list.adapter = inputsAdapter
        outputs_list.adapter = outputsAdapter

        var firstNode = true
        viewModel.networkChannel.observe(this, Observer { network ->
            if (firstNode) {
                firstNode = false
                network.getFirstNode()?.let {
                    viewModel.setNodeId(it.id)
                }
            }
            viewModel.setSurfaceView(surface_view)
        })

        viewModel.nodeChannel.observe(this, Observer { node ->
            node_name_view.text = node.type
            propertiesAdapter.setProperties(node.propertiesUi())
            val connectors = viewModel.getConnectors(node.id).groupBy { it.port.output }
            inputsAdapter.setPorts(connectors[false] ?: emptyList())
            outputsAdapter.setPorts(connectors[true] ?: emptyList())
            connectors[true]?.firstOrNull()?.let {
                viewModel.setOutputPort(it.port.nodeId, it.port.key)
            }
        })

//        button_node_list.setOnClickListener {
//            onJumpToNode()
//        }

        button_effects.setOnClickListener {
            val state = if (behavior.state == BottomSheetBehavior.STATE_HIDDEN)
                BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN
            behavior.state = state

            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                supportFragmentManager.commit {
                    val fragment = NetworkFragment.newInstance()
                    replace(R.id.bottom_container, fragment, NetworkFragment.TAG)
                }
            }
        }

        button_add_node.setOnClickListener {
            val state = if (behavior.state == BottomSheetBehavior.STATE_HIDDEN)
                BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN
            behavior.state = state

            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                supportFragmentManager.commit {
                    val fragment = AddNodeFragment()
                    replace(R.id.bottom_container, fragment, AddNodeFragment.TAG)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopExecution()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startExecution()
    }

    private fun onPortClick(connector: Connector) {
        val port = connector.port
        viewModel.setOutputPort(port.nodeId, port.key)
    }

    private fun onJumpToNode() {
        val dialog = NodeListDialog()
        dialog.listener = {
            Log.d(TAG, "onJump $it")
            viewModel.setNodeId(it.id)
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
//                viewModel.deleteNetwork()
                finish()
            }
        }.show(supportFragmentManager, null)
    }

//    override fun onBackPressed() {
//        if (!viewModel.onBackPressed()) {
//            super.onBackPressed()
//        }
//    }

    companion object {
        const val TAG = "BuilderActivity"
        const val NETWORK_ID = "network_id"

        fun getIntent(context: Context, networkId: Int = -1): Intent =
            Intent(context, BuilderActivity::class.java).also {
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
//                NodeDef.Properties -> NetworkFragment.newInstance()
//                NodeDef.Creation,
//                NodeDef.Connection -> ConnectionFragment.newInstance()
//                else -> NodeFragment.newInstance(node.id)
                else -> error("gone")
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