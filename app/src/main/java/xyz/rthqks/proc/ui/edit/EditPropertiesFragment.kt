package xyz.rthqks.proc.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.android.support.DaggerFragment
import xyz.rthqks.proc.data.NodeConfig
import javax.inject.Inject

class EditPropertiesFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel
    private var nodeId: Int = -1
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeId = arguments!!.getInt(ARG_NODE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        textView = TextView(context)
        textView.text = "edit props"
        return textView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        graphViewModel = ViewModelProviders.of(activity!!, viewModelFactory)[EditGraphViewModel::class.java]
        val node = graphViewModel.getNode(nodeId)

        textView.text = node.properties.toString()
    }

    companion object {
        private const val ARG_NODE_ID = "node_id"
        fun newInstance(nodeConfig: NodeConfig): EditPropertiesFragment {
            val args = Bundle()
            args.putInt(ARG_NODE_ID, nodeConfig.id)
            val fragment = EditPropertiesFragment()
            fragment.arguments = args

            return fragment
        }
    }
}