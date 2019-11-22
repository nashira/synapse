package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.PortConfig
import javax.inject.Inject

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private lateinit var swipeTouchListener: TouchMediator
    private lateinit var inputsAdapter: PortsAdapter
    private lateinit var outputsAdapter: PortsAdapter

    private var nodeId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeId = arguments?.getInt(ARG_NODE_ID) ?: -1

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_node, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        inputsAdapter = PortsAdapter(true)
        outputsAdapter = PortsAdapter(false)
        inputs_list.isEnabled = false
        inputs_list.layoutManager = LinearLayoutManager(context)
        inputs_list.adapter = inputsAdapter
        outputs_list.layoutManager = LinearLayoutManager(context)
        outputs_list.adapter = outputsAdapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        swipeTouchListener = TouchMediator(context!!, viewModel::swipeEvent)
//        swipe_left.setOnTouchListener(swipeTouchListener)
//        swipe_right.setOnTouchListener(swipeTouchListener)

        val node = viewModel.getNode(nodeId)
        inputsAdapter.setPorts(node.inputs)
        outputsAdapter.setPorts(node.outputs)

        toolbar.setTitle(node.type.name)

        Log.d(TAG, "viewModel $viewModel $this")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause $nodeId")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume $nodeId")
    }

    inner class PortsAdapter(
        private val isStartAligned: Boolean
    ) : RecyclerView.Adapter<PortViewHolder>() {
        private val ports = mutableListOf<PortConfig>()

        fun setPorts(ports: List<PortConfig>) {
            this.ports.clear()
            this.ports.addAll(ports)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.layout_port_fragment_node,
                parent,
                false
            )
            return PortViewHolder(view)
        }

        override fun getItemCount() = ports.size

        override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
            holder.bind(ports[position], isStartAligned)
        }
    }

    inner class PortViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.name
        private val button = itemView.button
        private var portConfig: PortConfig? = null

        init {
//            itemView.setOnClickListener { _ ->
////                portConfig?.let { it ->
////                    graphViewModel.setSelectedPort(it)
////                    null
////                }
//            }
            button.setOnTouchListener(swipeTouchListener)
        }

        fun bind(portConfig: PortConfig, startAligned: Boolean) {
            this.portConfig = portConfig
            val dataType = portConfig.type
            name.setText(dataType.name)
            button.setImageResource(dataType.icon)
            if (!startAligned) {
                listOf(name, button).forEach {
                    (it.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = Gravity.END
                        it.layoutParams = this
                    }
                }
                name.gravity = Gravity.END
            }

            Log.d(TAG, "port ${portConfig.key} ${viewModel.getPortState(portConfig)}")
//            when(viewModel.getPortState(portConfig)) {
//                PortState.Unconnected -> itemView.setBackgroundColor(Color.TRANSPARENT)
//                PortState.Connected -> itemView.setBackgroundColor(Color.rgb(200, 200, 255))
//                PortState.SelectedUnconnected -> itemView.setBackgroundColor(Color.rgb(200, 255, 255))
//                PortState.SelectedConnected -> itemView.setBackgroundColor(Color.rgb(255, 200, 255))
//                PortState.EligibleToConnect -> itemView.setBackgroundColor(Color.rgb(200, 255, 200))
//                PortState.EligibleToDisconnect -> itemView.setBackgroundColor(Color.rgb(255, 200, 200))
//            }
        }
    }

    companion object {
        const val TAG = "NodeFragment"
        const val ARG_NODE_ID = "node_id"
        fun newInstance(id: Int): NodeFragment {
            val args = Bundle()
            args.putInt(ARG_NODE_ID, id)
            val fragment = NodeFragment()
            fragment.arguments = args
            return fragment
        }
    }
}