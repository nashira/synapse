package com.rthqks.synapse.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.flow.logic.Network
import com.rthqks.flow.logic.Node
import com.rthqks.flow.logic.Property
import kotlinx.android.synthetic.main.layout_property.view.*
import kotlin.math.max

class PropertiesAdapter(
    private val onSelected: (Property, Choice<*>) -> Unit
) : RecyclerView.Adapter<PropertyViewHolder>() {
    private var list = emptyList<PropertyItem>()
    private val clickListener: (Property, Choice<*>) -> Unit = { property, choice ->
        onSelected(property, choice)
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
                                choice
                            )
                        )
                    }
                }

                PropertyType.TOGGLE,
                PropertyType.MENU -> {
                    new.add(
                        PropertyItem(
                            it.first,
                            holder
                        )
                    )
                }

                PropertyType.VALUE,
                PropertyType.FLOAT_RANGE,
                PropertyType.INT_RANGE -> {
                    new.add(
                        PropertyItem(
                            it.first,
                            holder
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
    itemView: View, clickListener: (Property, Choice<*>) -> Unit
) : PropertyViewHolder(itemView) {
    private val iconView = itemView.icon
    private val nameView = itemView.text
    private val titleView = itemView.title
    private var propertyItem: PropertyItem? = null

    init {
        itemView.setOnClickListener {
            val property = propertyItem?.property ?: return@setOnClickListener
            val choice = propertyItem?.choice ?: return@setOnClickListener
            property.value = choice.item
            clickListener(property, choice)
        }
    }

    override fun bind(propertyItem: PropertyItem) {
        this.propertyItem = propertyItem
        val choice = propertyItem.choice ?: return

        if (choice.icon == 0) {
            iconView.visibility = View.GONE
            nameView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.VISIBLE
            nameView.visibility = View.GONE
            iconView.setImageResource(choice.icon)
        }
        if (choice.label != 0) {
            nameView.setText(choice.label)
        }
        titleView.visibility = View.GONE
    }
}

private class ToggleViewHolder(
    itemView: View, clickListener: (Property, Choice<*>) -> Unit
) : PropertyViewHolder(itemView) {
    private val iconView = itemView.icon
    private val textView = itemView.text
    private val titleView = itemView.title
    private var propertyItem: PropertyItem? = null
    private var index = 0
    private var toggleType: ChoiceUi<*>? = null

    init {
        itemView.setOnClickListener {
            toggleType?.let {
                index = (index + 1) % it.choices.size
                update()

                val choice = it.choices[index]
                propertyItem?.let { p ->
                    p.property.value = choice.item
                    clickListener(p.property, choice)
                }
            }
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
            if (choice.icon == 0) {
                iconView.visibility = View.GONE
                textView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.VISIBLE
                textView.visibility = View.GONE
                iconView.setImageResource(choice.icon)
            }

            if (choice.label != 0) {
                textView.setText(choice.label)
            }

            propertyItem?.ui?.title?.let { t -> titleView.setText(t) }
        }
    }
}

data class PropertyItem(
    val property: Property,
    val ui: PropertyUi<*>,
    val choice: Choice<*>? = null
)

fun Network.propertiesUi(): MutableList<Pair<Property, PropertyUi<*>>> {
    val list = mutableListOf<Pair<Property, PropertyUi<*>>>()
    getProperties().filter { it.exposed }.forEach { p ->
        val node = getNode(p.nodeId)
        NodeUi[node.type][p.key]?.let {
            list += Pair(p, it)
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