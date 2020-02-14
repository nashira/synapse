package com.rthqks.synapse.ui.browse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.ui.build.BuilderActivity
import com.rthqks.synapse.ui.exec.NetworkActivity
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.synapse.activity_network_list.*
import kotlinx.android.synthetic.synapse.network_list_item.view.*
import javax.inject.Inject

class NetworkListActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: NetworkListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_list)
        toolbar.setTitle(R.string.title_network_list)
        viewModel = ViewModelProvider(this, viewModelFactory)[NetworkListViewModel::class.java]

        button_new_network.setOnClickListener {
            startActivity(BuilderActivity.getIntent(this))
        }

        button_new_network.setOnLongClickListener {
            Intent(this, BuilderActivity::class.java).also {
                startActivity(it)
            }
            true
        }

        val networkAdapter = NetworkAdapter()
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = networkAdapter

        viewModel.networkList.observe(this, Observer {
            Log.d(TAG, "networks: $it")
            networkAdapter.setNetworks(it)
        })

        networkAdapter.onItemClick { item, longClick ->
            if (longClick) {
                startActivity(
                    NetworkActivity.getIntent(this, item.id)
                )
            } else {
                startActivity(
                    BuilderActivity.getIntent(this, item.id)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNetworks()
    }

    companion object {
        const val TAG = "NetworkListActivity"
    }
}

class NetworkAdapter : RecyclerView.Adapter<NetworkViewHolder>() {
    private val networks = mutableListOf<Network>()
    private var itemClickListener: ((Network, Boolean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.network_list_item, parent, false)
        return NetworkViewHolder(view) { item, longClick ->
            itemClickListener?.invoke(item, longClick)
        }
    }

    override fun getItemCount(): Int = networks.size

    override fun onBindViewHolder(holder: NetworkViewHolder, position: Int) {
        holder.bind(networks[position])
    }

    fun setNetworks(list: List<Network>) {
        networks.clear()
        networks.addAll(list)
        notifyDataSetChanged()
    }

    fun onItemClick(function: (Network, Boolean) -> Unit) {
        itemClickListener = function
    }
}

class NetworkViewHolder(
    itemView: View,
    itemClick: (Network, Boolean) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val name = itemView.name
    private var network: Network? = null

    init {
        itemView.setOnClickListener {
            Log.d("network", "clicked $network")
            network?.let {
                itemClick(it, false)
            }
        }
        itemView.setOnLongClickListener {
            Log.d("network", "clicked $network")
            network?.let {
                itemClick(it, true)
            }
            true
        }
    }

    fun bind(network: Network) {
        this.network = network
        name.text = network.name
    }
}