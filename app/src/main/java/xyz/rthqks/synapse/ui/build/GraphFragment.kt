package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_graph.*
import xyz.rthqks.synapse.R
import javax.inject.Inject

class GraphFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        viewModel.setTitle(R.string.name_node_type_properties)
        viewModel.setMenu(R.menu.fragment_graph)
        edit_title.setText(viewModel.graph.name)

        val touchMediator = TouchMediator(context!!, viewModel::swipeEvent)
        root_view.setOnTouchListener { v, event ->
            touchMediator.onTouch(v, event)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    companion object {
        const val TAG = "GraphFragment"
        fun newInstance(): GraphFragment = GraphFragment()
    }
}