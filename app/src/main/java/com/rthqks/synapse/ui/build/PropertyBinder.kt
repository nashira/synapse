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
import com.rthqks.synapse.logic.ChoiceType
import com.rthqks.synapse.logic.FloatRangeType
import com.rthqks.synapse.logic.IntRangeType
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.ui.build.PropertyBinder.Companion.TAG
import kotlinx.android.synthetic.main.property_type_choice.view.*
import kotlinx.android.synthetic.main.property_type_choice.view.title
import kotlinx.android.synthetic.main.property_type_range.view.*
import kotlin.math.roundToInt

class PropertyBinder(
    private val recyclerView: RecyclerView,
    private val onChange: (Property<*>) -> Unit
) {
    private val inflater = LayoutInflater.from(recyclerView.context)
    private val adapter = PropertyAdapter(inflater, onChange)

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
            else -> error("unknown type $viewType")
        }
    }

    override fun getItemCount(): Int = if (property == null) 0 else 1

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        property?.let { holder.bind(it) }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val type = property?.type) {
            is ChoiceType<*> -> {
                R.layout.property_type_choice
            }
            is FloatRangeType,
            is IntRangeType -> {
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

abstract class PropertyViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(property: Property<*>)
}

class RangePropertyViewHolder(
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
                    (it as Property<Any?>).value = type.range.first + (normalized * scale).roundToInt()
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

class ChoicePropertyViewHolder(
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