package xyz.rthqks.synapse.ui.build


import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.property_type_choice.view.*
import kotlinx.android.synthetic.main.property_type_choice.view.title
import kotlinx.android.synthetic.main.property_type_range.view.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.logic.ChoiceType
import xyz.rthqks.synapse.logic.FloatRangeType
import xyz.rthqks.synapse.logic.IntRangeType
import xyz.rthqks.synapse.logic.Property
import xyz.rthqks.synapse.ui.build.PropertyBinder.Companion.TAG

class PropertyBinder(
    private val recyclerView: RecyclerView
) {
    private val inflater = LayoutInflater.from(recyclerView.context)
    private val adapter = PropertyAdapter(inflater)

    init {
        recyclerView.adapter = adapter
    }

    fun hide() {
        adapter.setProperty(null)
    }

    fun show(property: Property<out Any?>) {
        adapter.setProperty(property)

    }

    companion object {
        const val TAG = "PropertyBinder"
    }
}

class PropertyAdapter(
    private val inflater: LayoutInflater
) :
    RecyclerView.Adapter<PropertyViewHolder>() {
    private var property: Property<*>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val layout = inflater.inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.property_type_choice -> ChoicePropertyViewHolder(layout)
            R.layout.property_type_range -> if (property?.type is FloatRangeType) {
                RangePropertyViewHolder(layout)
            } else {
                RangePropertyViewHolder(layout)
            }
            else -> error("unknown type $viewType")
        }
    }

    override fun getItemCount(): Int = if (property == null) 0 else 1

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        property?.let { holder.bind(it) }
    }

    override fun getItemViewType(position: Int): Int {

        val type = property!!.type

        return when (type) {
            is ChoiceType<*> -> {
                Log.d(TAG, "choice type ${type.choices.joinToString()}")
                R.layout.property_type_choice
            }
            is FloatRangeType<*> -> {
                Log.d(TAG, "range type ${type.range}")
                R.layout.property_type_range
            }
            is IntRangeType -> {
                Log.d(TAG, "range type ${type.range}")
                R.layout.property_type_range
            }
            else -> error("unknown property type $type")
        }
    }

    fun setProperty(property: Property<*>?) {
        val old = this.property != null
        val new = property != null
        this.property = property
        when {
            old && new -> notifyItemChanged(0)
            new -> notifyItemInserted(0)
            old -> notifyItemRemoved(0)
        }
    }
}

abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(property: Property<*>)
}

class RangePropertyViewHolder(
    itemView: View
) : SeekBar.OnSeekBarChangeListener, PropertyViewHolder(itemView) {

    init {
        itemView.seekbar.setOnSeekBarChangeListener(this)
        itemView.seekbar.max = 1000
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        Log.d(TAG, "onProgressChanged $progress")
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        Log.d(TAG, "onStartTrackingTouch")
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        Log.d(TAG, "onStopTrackingTouch")
    }

    override fun bind(property: Property<*>) {
        val type = property.type
//        val range = type.range
        itemView.title.setText(type.title)
    }
}

class ChoicePropertyViewHolder(
    itemView: View
): PropertyViewHolder(itemView) {
    private val arrayAdapter: ArrayAdapter<String> =
        ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item)

    private lateinit var property: Property<*>
    private lateinit var type: ChoiceType<*>

    private var suppressSave = false

    init {
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemView.spinner.adapter = arrayAdapter


        itemView.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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

                (property as Property<Any?>).value = type.choices[position].item
            }
        }
    }

    override fun bind(property: Property<*>) {
        this.property = property
        type = property.type as ChoiceType<*>
        itemView.title.setText(type.title)
        arrayAdapter.clear()
        arrayAdapter.addAll(type.choices.map { itemView.context.getString(it.label) })
        suppressSave = true
        itemView.spinner.setSelection(type.choices.indexOfFirst {
            it.item == property.value
        })

        arrayAdapter.notifyDataSetChanged()

    }

    companion object {
        const val TAG = "ChoicePropertyViewHolder"
    }
}