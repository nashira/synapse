package xyz.rthqks.synapse.ui.edit

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_edit_properties.*
import kotlinx.android.synthetic.main.property_item_discrete.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.*
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
        recycler_view.adapter = PropertyAdapter(node)
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
    private val nodeConfig: NodeConfig
) : RecyclerView.Adapter<PropertyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.property_item_discrete -> DiscretePropertyViewHolder(view)
            else -> error("unknown property type: $viewType")
        }
    }

    override fun getItemCount(): Int = nodeConfig.properties.size

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val config = nodeConfig.properties[position]
        val property = nodeConfig.getPropertyType(position)
        Log.d(TAG, "binding $property")
        holder.bind(property, config)
    }

    override fun getItemViewType(position: Int): Int {
        return when (nodeConfig.getPropertyType(position)) {
            is DiscreteProperty -> R.layout.property_item_discrete
            else -> R.layout.frame_layout
        }
    }

    companion object {
        private val TAG = PropertyAdapter::class.java.simpleName
    }
}


abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(property: Property, config: PropertyConfig)
}

class DiscretePropertyViewHolder(itemView: View) : PropertyViewHolder(itemView) {
    private val arrayAdapter: ArrayAdapter<String> =
        ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item)

    private var property: DiscreteProperty? = null
    private var config: PropertyConfig? = null

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
                property?.values?.get(position)?.let {
                    config?.value = it.toString()
                }
            }

        }
    }

    override fun bind(property: Property, config: PropertyConfig) {
        this.property = property as DiscreteProperty
        this.config = config
        itemView.name.setText(property.name)
        arrayAdapter.clear()
        arrayAdapter.addAll(property.labels.map { itemView.context.getString(it) })
        itemView.value.setSelection(property.indexOf(config.value))
    }
}

private fun DiscreteProperty.indexOf(value: String): Int {
    val parsed = when (type) {
        ValueType.Int -> value.toInt()
        ValueType.Long -> value.toLong()
        ValueType.Float -> value.toFloat()
        ValueType.Double -> value.toDouble()
        ValueType.String -> value
    }

    return values.indexOf(parsed)
}

private fun NodeConfig.getPropertyType(position: Int): Property {
    val config = properties[position]
    return type.properties.getValue(config.key)
}