package xyz.rthqks.proc.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_graph_list.*
import xyz.rthqks.proc.R

class GraphListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph_list)

        button_new_graph.setOnClickListener {
            startActivity(Intent(this, GraphEditActivity::class.java))
        }
    }
}
