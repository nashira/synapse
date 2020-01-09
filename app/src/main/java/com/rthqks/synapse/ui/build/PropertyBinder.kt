package com.rthqks.synapse.ui.build


import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.ui.build.PropertyBinder.Companion.TAG
import kotlinx.android.synthetic.main.layout_property.view.icon
import kotlinx.android.synthetic.main.property_type_choice.view.*
import kotlinx.android.synthetic.main.property_type_choice.view.title
import kotlinx.android.synthetic.main.property_type_range.view.*
import kotlinx.android.synthetic.main.property_type_toggle.view.*
import kotlin.math.roundToInt

class PropertyBinder(
    private val properties: Properties,
    private val listView: RecyclerView,
    private val detailView: RecyclerView,
    private val onChange: (Property<*>) -> Unit
) {
    private val inflater = LayoutInflater.from(detailView.context)
    private val detailAdapter = PropertyAdapter(inflater, onChange)
    private val listAdapter: PropertiesAdapter

    init {
        detailView.adapter = detailAdapter
        listAdapter = PropertiesAdapter(properties) { key, selected, view ->
            for (i in 0 until listView.childCount) {
                listView.findViewHolderForAdapterPosition(i)?.let {
                    if (it.itemView != view) it.itemView.isSelected = false
                }
            }
            if (selected) {
                show(properties.find(key)!!)
            } else {
                hide()
            }
        }
        listView.adapter = listAdapter
    }

    fun hide() {
        detailAdapter.setProperty(null)
    }

    fun show(property: Property<out Any?>) {
        detailAdapter.setProperty(property)

    }

    companion object {
        const val TAG = "PropertyBinder"
    }
}

private class PropertiesAdapter(
    private val properties: Properties,
    private val onSelected: (Property.Key<*>, Boolean, View) -> Unit
) : RecyclerView.Adapter<PropertiesViewHolder>() {
    private val keys = properties.keys.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertiesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.layout_property, parent, false)
        return PropertiesViewHolder(view) { position ->
            val selected = !view.isSelected
            onSelected(keys[position], selected, view)
            view.isSelected = selected
        }
    }

    override fun getItemCount(): Int = keys.size

    override fun onBindViewHolder(holder: PropertiesViewHolder, position: Int) {
        val key = keys[position]
        val property = properties.find(key)!!
        holder.bind(key, property)
        Log.d(TAG, "onBind $position $key $property")
    }

    companion object {
        const val TAG = "PropertiesAdapter"
    }
}

private class PropertiesViewHolder(
    itemView: View, clickListener: (position: Int) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val iconView = itemView.icon

    init {
        itemView.setOnClickListener {
            clickListener(adapterPosition)
        }
    }

    fun bind(key: Property.Key<*>, property: Property<*>) {
        itemView.isSelected = false
        iconView.setImageResource(property.type.icon)
    }
}

private class PropertyAdapter(
    private val inflater: LayoutInflater,
    private val onChange: (Property<*>) -> Unit
) :
    RecyclerView.Adapter<PropertyViewHolder>() {
    private var property: Property<*>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val layout = inflater.inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.property_type_choice -> ChoicePropertyViewHolder(layout, onChange)
            R.layout.property_type_range -> RangePropertyViewHolder(layout, onChange)
            R.layout.property_type_toggle -> TogglePropertyViewHolder(layout, onChange)
            else -> error("unknown type $viewType")
        }
    }

    override fun getItemCount(): Int = if (property == null) 0 else 1

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        property?.let { holder.bind(it) }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val type = property?.type) {
            is ChoiceType<*> -> R.layout.property_type_choice
            is FloatRangeType,
            is IntRangeType -> R.layout.property_type_range
            is ToggleType -> R.layout.property_type_toggle
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

private abstract class PropertyViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(property: Property<*>)
}

private class RangePropertyViewHolder(
    itemView: View,
    private val onChange: (Property<*>) -> Unit
) : SeekBar.OnSeekBarChangeListener, PropertyViewHolder(itemView) {
    private var property: Property<*>? = null

    init {
        itemView.seekbar.setOnSeekBarChangeListener(this)
        itemView.seekbar.max = MAX
    }

    @Suppress("UNCHECKED_CAST")
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//        Log.d(TAG, "onProgressChanged $progress")
        property?.let {
            val normalized = itemView.seekbar.progress / MAX.toFloat()

            val type = it.type
            when (type) {
                is FloatRangeType -> {
                    val scale = (type.range.endInclusive - type.range.start)
                    (it as Property<Any?>).value = type.range.start + (normalized * scale)
                }
                is IntRangeType -> {
                    val scale = (type.range.last - type.range.first).toFloat()
                    (it as Property<Any?>).value =
                        type.range.first + (normalized * scale).roundToInt()
                }
                else -> error("unknown range type $type")
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        Log.d(TAG, "onStartTrackingTouch")
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        Log.d(TAG, "onStopTrackingTouch")
        property?.let(onChange)
    }

    override fun bind(property: Property<*>) {
        // set to null so changing seek position won't fire onChange
        this.property = null
        val type = property.type

        when (type) {
            is FloatRangeType -> {
                Log.d(TAG, "range type ${type.range}")
                val progress =
                    (property.value as Float - type.range.start) / (type.range.endInclusive - type.range.start)
                itemView.seekbar.progress = (progress * MAX).roundToInt()
            }
            is IntRangeType -> {
                Log.d(TAG, "range type ${type.range}")
                val progress =
                    (property.value as Int - type.range.first) / (type.range.last - type.range.first).toFloat()
                itemView.seekbar.progress = (progress * MAX).roundToInt()
            }
        }
        itemView.title.setText(type.title)
        // set once seek position is set
        this.property = property
    }

    companion object {
        const val MAX = 1000
    }
}

private class ChoicePropertyViewHolder(
    itemView: View,
    private val onChange: (Property<*>) -> Unit
) : PropertyViewHolder(itemView) {
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

                @Suppress("UNCHECKED_CAST")
                (property as Property<Any?>).value = type.choices[position].item
                onChange(property)
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
        Log.d(TAG, "choice type ${type.choices.joinToString()}")

        arrayAdapter.notifyDataSetChanged()
    }

    companion object {
        const val TAG = "ChoicePropertyViewHold"
    }
}

private class TogglePropertyViewHolder(
    itemView: View,
    private val onChange: (Property<*>) -> Unit
) : PropertyViewHolder(itemView) {
    private val iconView = itemView.icon
    private val titleView = itemView.title
    private val subtitleView = itemView.subtitle
    private val toggleButton = itemView.button_toggle
    private var property: Property<Boolean>? = null

    init {
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            property?.let {
                it.value = isChecked
                onChange(it)
                subtitle(it, it.type as ToggleType)
            }
        }
    }

    override fun bind(property: Property<*>) {
        @Suppress("UNCHECKED_CAST")
        this.property = property as Property<Boolean>
        val type = property.type as ToggleType
        iconView.setImageResource(type.icon)
        titleView.setText(type.title)

        toggleButton.isChecked = property.value

        subtitle(property, type)
    }

    private fun subtitle(
        property: Property<Boolean>,
        type: ToggleType
    ) {
        if (property.value) {
            subtitleView.setText(type.enabled)
        } else {
            subtitleView.setText(type.disabled)
        }
    }

}