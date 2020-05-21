package com.rthqks.synapse.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.Property
import kotlinx.android.synthetic.main.layout_property.view.*
import kotlin.math.max

class PropertiesAdapter(
    private val onSelected: (Property) -> Unit
) : RecyclerView.Adapter<PropertyViewHolder>() {
    private var list = emptyList<PropertyItem>()
    private val clickListener: (position: Int) -> Unit = { position ->
        val p = list[position]
        onSelected(p.property)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.layout_property, parent, false)

        return when (viewType) {
            2 -> ToggleViewHolder(view, clickListener)
            else -> SingleValueViewHolder(
                view,
                clickListener
            )
        }
    }

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int {
        val holder = list[position].ui
        return when (holder.type) {
            PropertyType.VALUE,
            PropertyType.EXPANDED -> 1

            PropertyType.TOGGLE,
            PropertyType.MENU -> 2

            PropertyType.FLOAT_RANGE,
            PropertyType.INT_RANGE -> 0
        }
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val property = list[position]
        holder.bind(property)
        Log.d(TAG, "onBind $position $property")
    }

    fun setProperties(properties: List<Pair<Property, PropertyUi<*>>>) {
        val new = mutableListOf<PropertyItem>()
        properties.forEach {
            val holder = it.second
            when (holder.type) {
                PropertyType.EXPANDED -> {
                    (holder as ChoiceUi<*>).choices.forEach { choice ->
                        new.add(
                            PropertyItem(
                                it.first,
                                holder,
                                choice.icon,
                                choice.item
                            )
                        )
                    }
                }

                PropertyType.TOGGLE,
                PropertyType.MENU -> {
                    new.add(
                        PropertyItem(
                            it.first,
                            holder,
                            holder.icon,
                            it.first.value
                        )
                    )
                }

                PropertyType.VALUE,
                PropertyType.FLOAT_RANGE,
                PropertyType.INT_RANGE -> {
                    new.add(
                        PropertyItem(
                            it.first,
                            holder,
                            holder.icon,
                            it.first.value
                        )
                    )
                }
            }
        }

        val result = DiffUtil.calculateDiff(object :
            DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return list[oldItemPosition] == new[newItemPosition]
            }

            override fun getOldListSize(): Int = list.size

            override fun getNewListSize(): Int = properties.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return list[oldItemPosition] == new[newItemPosition]
            }
        })
        list = new
        result.dispatchUpdatesTo(this)
    }

    companion object {
        const val TAG = "PropertiesAdapter"
    }
}

abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(propertyItem: PropertyItem)
}

private class SingleValueViewHolder(
    itemView: View, clickListener: (position: Int) -> Unit
) : PropertyViewHolder(itemView) {
    private val iconView = itemView.icon
    private var propertyItem: PropertyItem? = null

    init {
        itemView.setOnClickListener {
            propertyItem?.let { p ->
                p.property.value = p.value
            }
            clickListener(adapterPosition)
        }
    }

    override fun bind(propertyItem: PropertyItem) {
        this.propertyItem = propertyItem
        iconView.setImageResource(propertyItem.icon)
    }
}

private class ToggleViewHolder(
    itemView: View, clickListener: (position: Int) -> Unit
) : PropertyViewHolder(itemView) {
    private val iconView = itemView.icon
    private val textView = itemView.text
    private var propertyItem: PropertyItem? = null
    private var index = 0
    private var toggleType: ChoiceUi<*>? = null

    init {
        itemView.setOnClickListener {
            toggleType?.let {
                index = (index + 1) % it.choices.size
                update()
            }
            clickListener(adapterPosition)
        }
    }

    override fun bind(propertyItem: PropertyItem) {
        this.propertyItem = propertyItem
        val type = propertyItem.ui as ChoiceUi<*>
        toggleType = type
        index = max(0, type.choices.indexOfFirst {
            when (it.item) {
                is FloatArray -> it.item.contentEquals(propertyItem.property.value as FloatArray)
                else -> it.item == propertyItem.property.value
            }
        })
        update()
    }

    private fun update() {
        toggleType?.let {
            val choice = it.choices[index]
            propertyItem?.let { p ->
                p.property.value = choice.item
            }
            if (choice.label != 0) {
                textView.setText(choice.label)
            }
            iconView.setImageResource(choice.icon)
        }
    }
}

data class PropertyItem(
    val property: Property,
    val ui: PropertyUi<*>,
    @DrawableRes val icon: Int,
    var value: Any
)

fun Network.propertiesUi(): MutableList<Pair<Property, PropertyUi<*>>> {
    val list = mutableListOf<Pair<Property, PropertyUi<*>>>()
    getProperties().forEach {
        val node = getNode(it.nodeId)
        node.properties.values.forEach { p ->
            NodeUi[node.type][p.key]?.let {
                list += Pair(p, it)
            }
        }
    }
    return list
}

fun Node.propertiesUi(): MutableList<Pair<Property, PropertyUi<*>>> {
    val list = mutableListOf<Pair<Property, PropertyUi<*>>>()
    properties.values.forEach { property ->
        NodeUi[type][property.key]?.let {
            list += Pair(property, it)
        }
    }
    return list
}