package xyz.rthqks.synapse.ui.build

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import kotlinx.android.synthetic.main.layout_port_fragment_node.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.logic.Connector
import javax.inject.Inject

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private lateinit var touchMediator: TouchMediator
    private lateinit var inputsAdapter: PortsAdapter
    private lateinit var outputsAdapter: PortsAdapter

    private var nodeId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeId = arguments?.getInt(ARG_NODE_ID) ?: -1
        Log.d(TAG, "onCreate $nodeId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        Log.d(TAG, "onCreateView $nodeId")
        val view = inflater.inflate(R.layout.fragment_node, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated $nodeId")
        inputs_list.layoutManager = LinearLayoutManager(context)
        outputs_list.layoutManager = LinearLayoutManager(context)
        inputsAdapter = PortsAdapter(true)
        outputsAdapter = PortsAdapter(false)
        inputs_list.adapter = inputsAdapter
        outputs_list.adapter = outputsAdapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "onActivityCreated $nodeId")
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        touchMediator = TouchMediator(context!!, viewModel::swipeEvent)

        val graph = viewModel.graph
        val node = viewModel.getNode(nodeId)

        val connectors = graph.getConnectors(nodeId).groupBy { it.port.output }

        inputsAdapter.setPorts(connectors[false] ?: emptyList())
        outputsAdapter.setPorts(connectors[true] ?: emptyList())

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "onAttach $nodeId")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "onDetach $nodeId")
    }

    fun onPortTouch(connector: Connector) {
        Log.d(TAG, "touch $connector")
        viewModel.preparePortSwipe(connector)
    }

    fun onPortClick(connector: Connector) {
        Log.d(TAG, "click $connector")
    }

    fun onPortLongClick(connector: Connector) {
        Log.d(TAG, "long click $connector")
    }

    inner class PortsAdapter(
        private val isStartAligned: Boolean
    ) : RecyclerView.Adapter<PortViewHolder>() {
        private val ports = mutableListOf<Connector>()

        fun setPorts(ports: List<Connector>) {
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
        private var portConfig: Connector? = null

        init {
            button.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    portConfig?.let { it1 -> onPortTouch(it1) }
                }
                touchMediator.onTouch(v, event, portConfig)
            }
            button.setOnClickListener {
                portConfig?.let { it1 -> onPortClick(it1) }
            }
            button.setOnLongClickListener {
                portConfig?.let { it1 -> onPortLongClick(it1) }
                true
            }
        }

        fun bind(portConfig: Connector, startAligned: Boolean) {
            this.portConfig = portConfig
            name.text = portConfig.port.name
//            button.setImageResource(dataType.icon)
            if (!startAligned) {
                listOf(name, button).forEach {
                    (it.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = Gravity.END
                        it.layoutParams = this
                    }
                }
                name.gravity = Gravity.END
            }

//            Log.d(TAG, "port ${portConfig.key} ${viewModel.getPortState(portConfig)}")
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