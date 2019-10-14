package xyz.rthqks.synapse.ui.exec

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dagger.android.support.DaggerAppCompatActivity

class ExecGraphActivity: DaggerAppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graphId = intent.getIntExtra(GRAPH_ID, -1)
        Log.d(TAG, "graphid: $graphId")
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