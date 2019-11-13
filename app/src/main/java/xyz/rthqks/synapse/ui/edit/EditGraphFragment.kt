package xyz.rthqks.synapse.ui.edit

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_edit_graph.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.ui.exec.ExecGraphActivity
import javax.inject.Inject
import kotlin.math.round


class EditGraphFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel
    private lateinit var nodeAdapter: NodeAdapter


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        graphViewModel =
            ViewModelProviders.of(activity!!, viewModelFactory)[EditGraphViewModel::class.java]

        setupUi()

        graphViewModel.graphChannel.observe(this, Observer {
            Log.d(TAG, it.toString())
            nodeAdapter.setGraphEditor(it)
            edit_title.setText(it.graphData.name)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_graph, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setTitle(R.string.title_activity_graph_edit)
        toolbar.inflateMenu(R.menu.fragment_edit_graph)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.view -> {
                    activity?.let {
                        val intent = ExecGraphActivity.getIntent(it, graphViewModel.graph.id)
                        it.startActivity(intent)
                    }
                }
                R.id.delete -> {
                    val snackbar =
                        Snackbar.make(container, R.string.confirm_delete, Snackbar.LENGTH_LONG)
                    snackbar.setAction(R.string.confirm) {
                        graphViewModel.deleteGraph()
                        activity?.finish()
                    }
                    snackbar.show()
                }
            }
            true
        }

        edit_title.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    val imm =
                        context!!.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                    graphViewModel.setGraphName(edit_title.text.toString())
                    return true
                }
                return false
            }
        })
    }

    private fun setupUi() {
        button_add_node.setOnClickListener {
            graphViewModel.onAddNodeClicked.value = Unit
        }

        nodeAdapter = NodeAdapter(graphViewModel)
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
            nodeAdapter.onNodeAdded(it)
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
