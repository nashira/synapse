package com.rthqks.synapse.ui.edit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import com.rthqks.synapse.R
import com.rthqks.synapse.data.NodeData
import com.rthqks.synapse.data.PropertyData
import javax.inject.Inject

class EditPropertiesFragment : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var graphViewModel: EditGraphViewModel
    private var nodeId: Int = -1
    private var fileSelectData: PropertyData? = null

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
            ViewModelProvider(activity!!, viewModelFactory)[EditGraphViewModel::class.java]
//        toolbar.setTitle(node.type.name)
//        recycler_view.layoutManager = LinearLayoutManager(context)
//        recycler_view.adapter = PropertyAdapter(node, graphViewModel)

        graphViewModel.onSelectFile.observe(viewLifecycleOwner, Observer {
            it.consume()?.let {
                fileSelectData = it
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                    flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    startActivityForResult(this, OPEN_DOC_REQUEST)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OPEN_DOC_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    data.data!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                fileSelectData?.let { p -> graphViewModel.onFileSelected(data.data, p) }
                fileSelectData = null
            }
//        Log.d(TAG, "data $data $fileDescriptor")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val TAG = "EditPropertiesFragment"
        const val ARG_NODE_ID = "node_id"
        const val OPEN_DOC_REQUEST = 17
        fun newInstance(nodeData: NodeData): EditPropertiesFragment {
            val args = Bundle()
            args.putInt(ARG_NODE_ID, nodeData.id)
            val fragment = EditPropertiesFragment()
            fragment.arguments = args

            return fragment
        }
    }
}
//
//class PropertyAdapter(
//    private val nodeData: NodeData,
//    private val viewModel: EditGraphViewModel
//) : RecyclerView.Adapter<PropertyViewHolder>() {
//    val properties = nodeData.properties.values.toList()
//    val propertyTypes: List<PropertyType<*>> =
//        properties.map { PropertyType.map[Key[it.type]] as PropertyType<*> }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(viewType, parent, false)
//        return when (viewType) {
//            R.layout.property_item_uri -> UriPropertyViewHolder(view, viewModel)
//            R.layout.property_type_choice -> DiscretePropertyViewHolder(view, viewModel)
//            R.layout.property_item_text -> TextPropertyViewHolder(view, viewModel)
//            else -> error("unknown property type: $viewType")
//        }
//    }
//
//    override fun getItemCount(): Int = nodeData.properties.size
//
//    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
//        val config = properties[position]
//        val property = propertyTypes[position]
//        Log.d(TAG, "binding $property")
//        holder.bind(property, config)
//    }
//
//    override fun getItemViewType(position: Int): Int {
//        val type = propertyTypes[position]
//        return when {
//            type == PropertyType.Uri -> R.layout.property_item_uri
//            type is PropertyType.Text -> R.layout.property_item_text
//            type is PropertyType.Discrete -> R.layout.property_type_choice
//            else -> 0
//        }
//    }
//
//    companion object {
//        const val TAG = "PropertyAdapter"
//    }
//}
//
//
//abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//    abstract fun bind(property: PropertyType<*>, data: PropertyData)
//}
//
//class UriPropertyViewHolder(
//    itemView: View,
//    private val viewModel: EditGraphViewModel
//) : PropertyViewHolder(itemView) {
//    private var property: PropertyType.Text? = null
//    private var data: PropertyData? = null
//    private var suppressSave = false
//
//    init {
//        itemView.button_select_file.setOnClickListener {
//            data?.let { it1 -> viewModel.selectFileFor(it1) }
//        }
//    }
//
//    override fun bind(property: PropertyType<*>, data: PropertyData) {
//        this.property = property as PropertyType.Text
//        this.data = data
//        itemView.uri_name.setText(property.name)
//        suppressSave = true
//    }
//
//    companion object {
//        const val TAG = "TextPropertyViewHolder"
//    }
//}
//
//class TextPropertyViewHolder(
//    itemView: View,
//    private val viewModel: EditGraphViewModel
//) : PropertyViewHolder(itemView) {
//    private var property: PropertyType.Text? = null
//    private var data: PropertyData? = null
//    private var suppressSave = false
//    private var editText = itemView.text_value
//
//    init {
//
//        editText.setOnKeyListener(object : View.OnKeyListener {
//            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
//                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
//                    val imm =
//                        itemView.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
//
//                    imm.hideSoftInputFromWindow(v.windowToken, 0)
//                    v.clearFocus()
//
//                    data?.let {
//                        it.value = PropertyType.toString(property!!.key, editText.text.toString())
//                        viewModel.saveProperty(it)
//                    }
//
//                    return true
//                }
//                return false
//            }
//        })
//    }
//
//    override fun bind(property: PropertyType<*>, data: PropertyData) {
//        this.property = property as PropertyType.Text
//        this.data = data
//        itemView.name.setText(property.name)
//        editText.setText(data.value)
//        suppressSave = true
//    }
//
//    companion object {
//        const val TAG = "TextPropertyViewHolder"
//    }
//}
//
//class DiscretePropertyViewHolder(
//    itemView: View,
//    private val viewModel: EditGraphViewModel
//) : PropertyViewHolder(itemView) {
//    private val arrayAdapter: ArrayAdapter<String> =
//        ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item)
//
//    private var property: PropertyType.Discrete<*>? = null
//    private var data: PropertyData? = null
//    private var suppressSave = false
//
//    init {
//        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        itemView.value.adapter = arrayAdapter
//        itemView.value.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//
//            }
//
//            override fun onItemSelected(
//                parent: AdapterView<*>?,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                if (suppressSave) {
//                    suppressSave = false
//                    return
//                }
//
//                property?.values?.get(position)?.let { value ->
//                    data?.let {
//
//                        it.value = PropertyType.toString(property!!.key, value)
//                        viewModel.saveProperty(it)
//                    }
//                }
//            }
//        }
//    }
//
//    override fun bind(property: PropertyType<*>, data: PropertyData) {
//        this.property = property as PropertyType.Discrete
//        this.data = data
//        itemView.name.setText(property.name)
//        arrayAdapter.clear()
//        arrayAdapter.addAll(property.labels.map { itemView.context.getString(it) })
//        suppressSave = true
//        itemView.value.setSelection(property.indexOf(data.value))
//    }
//
//    companion object {
//        const val TAG = "DiscretePropertyViewHolder"
//    }
//}
//
//private fun PropertyType.Discrete<*>.indexOf(value: String): Int {
//    val v = fromString(value)
//    return values.indexOf(v)
//}