package xyz.rthqks.proc.ui.edit

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_edit_graph.*
import xyz.rthqks.proc.R
import javax.inject.Inject
import kotlin.math.round

class EditGraphFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        graphViewModel =
            ViewModelProviders.of(activity!!, viewModelFactory)[EditGraphViewModel::class.java]

//        graphViewModel.graphChannel.observe(this, Observer {
//            Log.d(TAG, it.toString())
//            Log.d(TAG, it.nodes.toString())
//            Log.d(TAG, it.edges.toString())
//            setupUi(it)
//        })

        setupUi()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_graph, container, false)
        return view
    }

    private fun setupUi() {
        val nodeAdapter = NodeAdapter(graphViewModel)
        val nodeLayoutManager = LinearLayoutManager(context)
        val dp8 = round(resources.displayMetrics.density * 8).toInt()
        val dp84 = round(resources.displayMetrics.density * 84).toInt()
        recycler_view.layoutManager = nodeLayoutManager
        recycler_view.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.top = if (parent.getChildLayoutPosition(view) > 0) 0 else dp8
                outRect.bottom =
                    if (parent.getChildAdapterPosition(view) == nodeAdapter.itemCount - 1) dp84 else dp8
                outRect.left = dp8
                outRect.right = dp8
            }
        })

        recycler_view.adapter = nodeAdapter
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                nodeAdapter.moveNode(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })

        touchHelper.attachToRecyclerView(recycler_view)

        graphViewModel.onNodeAdded.observe(this, Observer {
            nodeAdapter.onNodeAdded()
        })

        graphViewModel.onPortSelected.observe(this, Observer {
            nodeAdapter.notifyDataSetChanged()
        })

        nodeAdapter.onEditNodeProperties = {
            Log.d(TAG, "onEditNodeProperties $it")
            fragmentManager?.commit {
                val fragment = EditPropertiesFragment.newInstance(it)
                replace(R.id.content, fragment)
                addToBackStack(null)
            }
        }
    }

    companion object {
        private val TAG = EditGraphFragment::class.java.simpleName
    }
}
