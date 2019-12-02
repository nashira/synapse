package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_connection.*
import xyz.rthqks.synapse.R
import javax.inject.Inject

class ConnectionFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: BuilderViewModel
    private val portId: String get() = viewModel.connectionPortId
    private val nodeId: Int get() = viewModel.connectionNodeId

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_connection, container, false)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]

        viewModel.connectionChannel.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "changed $nodeId $portId")
        })

        val touchMediator = TouchMediator(context!!, viewModel::swipeEvent)
        text_view.setOnTouchListener { v, event ->
            touchMediator.onTouch(v, event)
        }
    }

    override fun onResume() {
        super.onResume()
        text_view.text = "$nodeId\n$portId"
        Log.d(TAG, "loaded $nodeId $portId")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }


    companion object {
        const val TAG = "ConnectionFragment"

        fun newInstance(): ConnectionFragment {
            return ConnectionFragment()
        }
    }
}