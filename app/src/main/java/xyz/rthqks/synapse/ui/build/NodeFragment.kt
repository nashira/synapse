package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import xyz.rthqks.synapse.R
import javax.inject.Inject

class NodeFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private var pos = 0
    private val swipeTouchListener: (View, MotionEvent) -> Boolean = { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            viewModel.enableSwipe(event)
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pos = arguments?.getInt("pos") ?: 0
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
        swipe_left.setOnTouchListener(swipeTouchListener)
        swipe_right.setOnTouchListener(swipeTouchListener)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        Log.d(TAG, "viewModel $viewModel")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause $pos")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume $pos")
    }

    companion object {
        const val TAG = "NodeFragment"
    }
}