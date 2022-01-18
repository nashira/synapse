package com.rthqks.synapse.build

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isEmpty
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.flow.logic.Connector
import com.rthqks.flow.logic.Node
import com.rthqks.synapse.databinding.ActivityBuilderBinding
import com.rthqks.synapse.ui.ConfirmDialog
import com.rthqks.synapse.ui.PropertiesAdapter
import com.rthqks.synapse.ui.propertiesUi
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject


class BuilderActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private val inputsAdapter = PortsAdapter(true, this::onPortClick)
    private val outputsAdapter = PortsAdapter(false, this::onPortClick)
    private lateinit var binding: ActivityBuilderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        viewModel = ViewModelProvider(this, viewModelFactory)[BuilderViewModel::class.java]
        Log.d(TAG, "viewModel $viewModel")

        if (savedInstanceState == null) {
            val networkId = intent.getStringExtra(NETWORK_ID) ?: error("missing network id")
            viewModel.setNetworkId(networkId)
        }

        val propertiesAdapter = PropertiesAdapter { property, choice ->
            Log.d(TAG, "property changed: ${property.key.name}: ${property.value}")
        }
        binding.propertiesList.adapter = propertiesAdapter

        binding.inputsList.adapter = inputsAdapter
        binding.outputsList.adapter = outputsAdapter

        var firstNode = true
        viewModel.networkChannel.observe(this) { network ->
            if (firstNode) {
                firstNode = false
                network.getFirstNode()?.let {
                    viewModel.setNodeId(it.id)
                }
            }
            viewModel.setSurfaceView(binding.surfaceView)
        }

        viewModel.nodeChannel.observe(this) { node ->
            handleNode(node, propertiesAdapter)
        }

//        button_node_list.setOnClickListener {
//            onJumpToNode()
//        }

        binding.buttonEffects.setOnClickListener {
            val state = if (behavior.state == BottomSheetBehavior.STATE_HIDDEN)
                BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN

            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                supportFragmentManager.commitNow {
                    val fragment = NetworkFragment.newInstance()
                    replace(R.id.bottom_sheet, fragment, NetworkFragment.TAG)
                }
            }

            behavior.state = state
        }

        binding.buttonAddNode.setOnClickListener {
            val state = if (behavior.state == BottomSheetBehavior.STATE_HIDDEN)
                BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN


            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                supportFragmentManager.commitNow {
                    val fragment = AddNodeFragment()
                    replace(R.id.bottom_sheet, fragment, AddNodeFragment.TAG)
                }
            }
            behavior.state = state
        }
    }

    private fun handleNode(
        node: Node,
        propertiesAdapter: PropertiesAdapter
    ) {
        binding.nodeNameView.text = node.type
        propertiesAdapter.setProperties(node.propertiesUi())
        val connectors = viewModel.getConnectors(node.id).groupBy { it.port.output }
        inputsAdapter.setPorts(connectors[false] ?: emptyList())
        outputsAdapter.setPorts(connectors[true] ?: emptyList())
        connectors[true]?.firstOrNull()?.let {
            viewModel.setOutputPort(it.port.nodeId, it.port.key)
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(
                        event.rawX.toInt(),
                        event.rawY.toInt()
                    )
                ) {
                    v.clearFocus()
                    val imm: InputMethodManager =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("RestrictedApi")
    private fun onPortClick(view: View, connector: Connector) {
        Log.d(TAG, "long click $connector")
        val menu = PopupMenu(this, view)
        menu.inflate(R.menu.layout_connector)

        if (connector.link == null) {
            menu.menu.findItem(R.id.delete_connection)?.isVisible = false
        }

        if (!connector.port.output) {
            menu.menu.findItem(R.id.add_connection)?.isVisible = false
        }

        if (menu.menu.isEmpty()) {
            return
        }

        try {
            // TODO: remove when support library adds this
            val field = PopupMenu::class.java.getDeclaredField("mPopup").also {
                it.isAccessible = true
            }.get(menu) as MenuPopupHelper
            field.setForceShowIcon(true)
        } catch (e: Throwable) {
            Log.w(TAG, "error forcing icons visible")
        }

        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.show_preview -> {
                    Log.d(TAG, "show preview")
                    val port = connector.port
                    viewModel.setOutputPort(port.nodeId, port.key)
                }
                R.id.expose_port -> {
                    Log.d(TAG, "expose")
                }
                R.id.add_connection -> {
                    Log.d(TAG, "add")
                }
                R.id.delete_connection -> {
                    Log.d(TAG, "delete")
                }
            }
            true
        }

        menu.show()
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

//    override fun onBackPressed() {
//        if (!viewModel.onBackPressed()) {
//            super.onBackPressed()
//        }
//    }

    companion object {
        const val TAG = "BuilderActivity"
        const val NETWORK_ID = "network_id"

        fun getIntent(context: Context, networkId: String? = null): Intent =
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