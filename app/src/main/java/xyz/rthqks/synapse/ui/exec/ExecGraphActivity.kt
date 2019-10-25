package xyz.rthqks.synapse.ui.exec

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_exec_graph.*
import xyz.rthqks.synapse.R
import javax.inject.Inject

class ExecGraphActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: ExecGraphViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exec_graph)
        viewModel = ViewModelProviders.of(this, viewModelFactory)[ExecGraphViewModel::class.java]
        viewModel.setSurfaceView(surface_view)

        val graphId = intent.getIntExtra(GRAPH_ID, -1)
        Log.d(TAG, "graphid: $graphId")

        savedInstanceState ?: run {
            viewModel.loadGraph(graphId)
        }

        viewModel.graphLoaded.observe(this, Observer {
            toolbar.title = it.name
        })

        /*
        viewModel.surfaceViewRequest.observe(this, Observer {
            // create surface views needed by graph, pass view objects back
        })
         */
    }

    override fun onStart() {
        super.onStart()
        viewModel.startExecution()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopExecution()
    }

    companion object {
        private val TAG = ExecGraphActivity::class.java.simpleName
        private const val GRAPH_ID = "graph_id"
        fun getIntent(activity: Activity, graphId: Int): Intent {
            val intent = Intent(activity, ExecGraphActivity::class.java)
            intent.putExtra(GRAPH_ID, graphId)
            return intent
        }
    }
}