package xyz.rthqks.synapse.ui.build

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_graph.*
import kotlinx.android.synthetic.main.property_toggle.*
import kotlinx.android.synthetic.main.property_toggle.view.*
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
        return inflater.inflate(R.layout.fragment_graph, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(activity!!, viewModelFactory)[BuilderViewModel::class.java]
        viewModel.setTitle(R.string.name_node_type_properties)
        viewModel.setMenu(R.menu.fragment_graph)

        setupEditTitle()
        edit_title.setText(viewModel.graph.name)

        button_save.setOnClickListener {
            handleNameSave()
        }

        val touchMediator = TouchMediator(context!!, viewModel::swipeEvent)
        swipe_to_nodes.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                viewModel.showFirstNode()
            }
            touchMediator.onTouch(v, event)
        }

        val property = ToggleProperty(property_toggle)
        property.iconView.setImageResource(R.drawable.ic_crop)
        property.titleView.setText(R.string.property_title_crop_to_fit)
        property.subtitleView.setText(R.string.property_subtitle_crop_to_fit_disabled)
        property.toggleButton.setOnClickListener {
            viewModel.setCropCenter(property.toggleButton.isChecked)
            if (property.toggleButton.isChecked) {
                property.subtitleView.setText(R.string.property_subtitle_crop_to_fit_enabled)
            } else {
                property.subtitleView.setText(R.string.property_subtitle_crop_to_fit_disabled)
            }
        }
    }

    private fun setupEditTitle() {
        edit_title.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleNameSave()
                    return true
                }
                return false
            }
        })
    }

    private fun handleNameSave() {
        val imm = context!!.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edit_title.windowToken, 0)
        edit_title.clearFocus()
        viewModel.setGraphName(edit_title.text.toString())
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            viewModel.setTitle(R.string.name_node_type_properties)
            viewModel.setMenu(R.menu.fragment_graph)
        }
        Log.d(TAG, "onResume")
    }

    companion object {
        const val TAG = "GraphFragment"
        fun newInstance(): GraphFragment = GraphFragment()
    }
}

class ToggleProperty(
    val view: View
) {
    val iconView = view.icon
    val titleView = view.title
    val subtitleView = view.subtitle
    val toggleButton = view.button_toggle
}