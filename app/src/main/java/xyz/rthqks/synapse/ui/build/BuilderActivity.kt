package xyz.rthqks.synapse.ui.build

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_builder.*
import xyz.rthqks.synapse.R
import javax.inject.Inject

class BuilderActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: BuilderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_builder)

        viewModel = ViewModelProvider(this, viewModelFactory)[BuilderViewModel::class.java]
        Log.d(TAG, "viewModel $viewModel")
        viewModel.onSwipeEnable.observe(this, Observer {
            it.consume()?.let {
                view_pager.isUserInputEnabled = true
                view_pager.onInterceptTouchEvent(it)
            }
        })
        view_pager.adapter = NodeAdapter(this)
        view_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback(){
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_IDLE -> view_pager.isUserInputEnabled = false
                }
            }
        })
        view_pager.isUserInputEnabled = false
    }

    companion object {
        const val TAG = "BuilderActivity"
        const val GRAPH_ID = "graph_id"

        fun getIntent(activity: Activity, graphId: Int = -1): Intent =
            Intent(activity, BuilderActivity::class.java).also {
                it.putExtra(GRAPH_ID, graphId)
            }
    }
}

class NodeAdapter(
    activity: AppCompatActivity
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int {
        return 5
    }

    override fun createFragment(position: Int): Fragment {
        val args = Bundle()
        args.putInt("pos", position)
        val nodeFragment = NodeFragment()
        nodeFragment.arguments = args
        return nodeFragment
    }
}