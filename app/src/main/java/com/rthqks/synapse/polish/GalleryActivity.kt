package com.rthqks.synapse.polish

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.rthqks.synapse.R
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.ops.Analytics
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_gallery.*
import kotlinx.android.synthetic.main.layout_gallery_video.view.*
import javax.inject.Inject

class GalleryActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject
    lateinit var analytics: Analytics
    lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        viewModel = ViewModelProvider(this, viewModelFactory)[GalleryViewModel::class.java]

        appbar.outlineProvider = null
        toolbar.setTitle(R.string.title_gallery)
        val gridPadding = resources.getDimensionPixelSize(R.dimen.gallery_grid_spacing)

        val adapter = VideoAdapter {
            startActivity(it)
            if (it.action == Intent.ACTION_VIEW) {
                analytics.logEvent(Analytics.Event.ViewVideo())
            } else {
                analytics.logEvent(Analytics.Event.ShareVideo())
            }
        }

        video_list.addItemDecoration(object : RecyclerView.ItemDecoration(){
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos % 2 == 0) {
                    outRect.set(gridPadding*2, gridPadding, gridPadding, gridPadding)
                } else {
                    outRect.set(gridPadding, gridPadding, gridPadding*2, gridPadding)
                }
            }
        })
        viewModel.loadVideos()
        video_list.adapter = adapter
        viewModel.localVideos.observe(this, Observer { videos ->
            Log.d("Gallery", videos.joinToString())
            adapter.setVideos(videos)
        })
    }
}

private class VideoAdapter(
    private val onIntent: (Intent) -> Unit
) : RecyclerView.Adapter<VideoViewHolder>() {
    private val videos = mutableListOf<VideoStorage.Video>()
    fun setVideos(videos: List<VideoStorage.Video>) {
        this.videos.clear()
        this.videos.addAll(videos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_gallery_video, parent, false)
        return VideoViewHolder(view, onIntent)
    }

    override fun getItemCount(): Int = videos.size

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        Log.d("VideoAdapter", "bind ${videos[position]}")
        holder.bind(videos[position])
    }

    companion object {
        const val TAG = "GalleryActivity"
    }
}

private class VideoViewHolder(
    itemView: View,
    onIntent: (Intent) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private var video: VideoStorage.Video? = null

    init {
        itemView.image_view.setOnClickListener {
            video?.let {
                val intent: Intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(it.uri, "video/*")
                }

                if (intent.resolveActivity(itemView.context.packageManager) != null) {
                    onIntent(intent)
                }
            }
        }
        itemView.button_share.setOnClickListener {
            video?.let {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, it.uri)
                    type = VideoStorage.MIME_MP4
                }

                val shareIntent = Intent.createChooser(sendIntent, null)
                onIntent(shareIntent)
            }
        }
    }

    fun bind(video: VideoStorage.Video) {
        this.video = video
        Glide
            .with(itemView.context)
            .asBitmap()
            .load(video.uri)
            .centerCrop()
            .into(itemView.image_view)

        val timeLabel = DateUtils.getRelativeDateTimeString(
            itemView.context,
            video.date,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
        )
        itemView.label_view.text = timeLabel
    }
}