package xyz.rthqks.synapse.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_graph_list.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.ui.edit.GraphEditActivity

class GraphListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_list)

        button_new_graph.setOnClickListener {
            startActivity(Intent(this, GraphEditActivity::class.java))
        }
    }
}
