package xyz.rthqks.synapse.ui.edit

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_edit_properties.*
import kotlinx.android.synthetic.main.property_item_discrete.view.name
import kotlinx.android.synthetic.main.property_item_discrete.view.value
import kotlinx.android.synthetic.main.property_item_text.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.data.NodeConfig
import xyz.rthqks.synapse.data.PropertyConfig
import xyz.rthqks.synapse.data.PropertyType
import javax.inject.Inject

class EditPropertiesFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel
    private var nodeId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeId = arguments!!.getInt(ARG_NODE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_properties, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        graphViewModel =
            ViewModelProviders.of(activity!!, viewModelFactory)[EditGraphViewModel::class.java]
        val node = graphViewModel.getNode(nodeId)
        toolbar.setTitle(node.type.name)
        recycler_view.layoutManager = LinearLayoutManager(context)
        recycler_view.adapter = PropertyAdapter(node, graphViewModel)
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

class PropertyAdapter(
    private val nodeConfig: NodeConfig,
    private val viewModel: EditGraphViewModel
) : RecyclerView.Adapter<PropertyViewHolder>() {
    val properties = nodeConfig.properties.values.toList()
    val propertyTypes = properties.map { PropertyType[Key[it.type] as Key<Any>] }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.property_item_discrete -> DiscretePropertyViewHolder(view, viewModel)
            R.layout.property_item_text -> TextPropertyViewHolder(view, viewModel)
            else -> error("unknown property type: $viewType")
        }
    }

    override fun getItemCount(): Int = nodeConfig.properties.size

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val config = properties[position]
        val property = propertyTypes[position]
        Log.d(TAG, "binding $property")
        holder.bind(property, config)
    }

    override fun getItemViewType(position: Int): Int {
        return when (propertyTypes[position]) {
            is PropertyType.Discrete -> R.layout.property_item_discrete
            is PropertyType.Text -> R.layout.property_item_text
            else -> R.layout.frame_layout
        }
    }

    companion object {
        private val TAG = PropertyAdapter::class.java.simpleName
    }
}


abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(property: PropertyType<*>, config: PropertyConfig)
}

class TextPropertyViewHolder(
    itemView: View,
    private val viewModel: EditGraphViewModel
) : PropertyViewHolder(itemView) {
    private var property: PropertyType.Text? = null
    private var config: PropertyConfig? = null
    private var suppressSave = false
    private var editText = itemView.text_value

    init {

        editText.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    val imm =
                        itemView.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()

                    config?.let {
                        it.value = PropertyType.toString(property!!.key, editText.text.toString())
                        viewModel.saveProperty(it)
                    }

                    return true
                }
                return false
            }
        })
    }

    override fun bind(property: PropertyType<*>, config: PropertyConfig) {
        this.property = property as PropertyType.Text
        this.config = config
        itemView.name.setText(property.name)
        editText.setText(config.value)
        suppressSave = true
    }

    companion object {
        const val TAG = "TextPropertyViewHolder"
    }
}

class DiscretePropertyViewHolder(
    itemView: View,
    private val viewModel: EditGraphViewModel
) : PropertyViewHolder(itemView) {
    private val arrayAdapter: ArrayAdapter<String> =
        ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item)

    private var property: PropertyType.Discrete<*>? = null
    private var config: PropertyConfig? = null
    private var suppressSave = false

    init {
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemView.value.adapter = arrayAdapter
        itemView.value.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressSave) {
                    suppressSave = false
                    return
                }

                property?.values?.get(position)?.let { value ->
                    config?.let {

                        it.value = PropertyType.toString(property!!.key, value)
                        viewModel.saveProperty(it)
                    }
                }
            }
        }
    }

    override fun bind(property: PropertyType<*>, config: PropertyConfig) {
        this.property = property as PropertyType.Discrete
        this.config = config
        itemView.name.setText(property.name)
        arrayAdapter.clear()
        arrayAdapter.addAll(property.labels.map { itemView.context.getString(it) })
        suppressSave = true
        itemView.value.setSelection(property.indexOf(config.value))
    }

    companion object {
        private val TAG = DiscretePropertyViewHolder::class.java.simpleName
    }
}

private fun PropertyType.Discrete<*>.indexOf(value: String): Int {
    val v = fromString(value)
    return values.indexOf(v)
}