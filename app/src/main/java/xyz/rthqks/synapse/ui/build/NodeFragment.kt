package xyz.rthqks.synapse.ui.build

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_node.*
import xyz.rthqks.synapse.R
import javax.inject.Inject
import kotlin.math.abs

class NodeFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel
    private var pos = 0

    private val swipeTouchListener = object : View.OnTouchListener {
        lateinit var handler: Handler
        var startX = 0f
        var x = -1f
        var didLongClick = false
        var touchSlop: Int = 8
        var longPressTimeout: Long = 0
        val swipeEvent = SwipeEvent(0)

        override fun onTouch(view: View, event: MotionEvent): Boolean {
//        Log.d(TAG, "onTouch $event")
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeEvent.action = MotionEvent.ACTION_DOWN
                    viewModel.swipeEvent(swipeEvent)
                    x = event.rawX
                    startX = x
                    didLongClick = false
                    handler.postDelayed(
                        {
                            if (x != -1f && abs(x - startX) < touchSlop) {
                                didLongClick = view.performLongClick()
                            }
                        },
                        longPressTimeout
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    swipeEvent.action = MotionEvent.ACTION_MOVE
                    swipeEvent.x = event.rawX - x
                    viewModel.swipeEvent(swipeEvent)
                    x = event.rawX
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    x = -1f
                    Log.d(TAG, "total move ${abs(event.rawX - startX)}")
                    swipeEvent.action = MotionEvent.ACTION_UP
                    viewModel.swipeEvent(swipeEvent)
                    if (!didLongClick && abs(event.rawX - startX) < touchSlop) {
                        view.performClick()
                    }
                }
            }
            return !didLongClick
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        swipeTouchListener.handler = Handler(Looper.getMainLooper())
        swipeTouchListener.touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        swipeTouchListener.longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

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
        swipe_left.setOnClickListener {
            Log.d(TAG, "left click")
        }
        swipe_right.setOnClickListener {
            Log.d(TAG, "right click")
        }
        swipe_right.setOnLongClickListener {
            Log.d(TAG, "right long click")
            true
        }
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