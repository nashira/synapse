package com.rthqks.synapse.polish

import android.os.Bundle
import com.rthqks.synapse.R
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.polish.activity_gallery.*

class GalleryActivity : DaggerAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        toolbar.setTitle(R.string.title_gallery)
    }
}