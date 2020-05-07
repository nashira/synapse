package com.rthqks.synapse.polish

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.Lut3d.LutStrength
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.ops.Analytics
import com.rthqks.synapse.ui.ExpandedHolder
import com.rthqks.synapse.ui.NodeUi
import com.rthqks.synapse.ui.PropertyHolder
import com.rthqks.synapse.ui.ToggleHolder
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.layout_lut.view.*
import kotlinx.android.synthetic.polish.activity_polish.*
import kotlinx.android.synthetic.polish.layout_effect.view.*
import kotlinx.android.synthetic.polish.layout_property.view.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class PolishActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var analytics: Analytics
    lateinit var viewModel: PolishViewModel
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var preferences: SharedPreferences
    private val deviceSupported = CompletableDeferred<Boolean>()
    private val permissionsGranted = CompletableDeferred<Boolean>()
    private var uiAngle = 0
    private val interpolator = AccelerateDecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polish)
        viewModel = ViewModelProvider(this, viewModelFactory)[PolishViewModel::class.java]

        val behavior = BottomSheetBehavior.from(layout_colors)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions $states")

        val neededPermissions = states.filter { !it.granted }.map { it.name }

        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 123)
        } else {
            permissionsGranted.complete(true)
        }

        viewModel.deviceSupported.observe(this, Observer {
            deviceSupported.complete(it)
        })

        var rotation = 0
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                val update = when (angle) {
                    in 45..135 -> 90
                    in 135..225 -> 180
                    in 225..315 -> 270
                    else -> 0
                }

                if (rotation != update) {
                    rotation = update
                    viewModel.setDeviceOrientation(rotation)

                    uiAngle = when (angle) {
                        in 45..135 -> -90
                        in 135..225 -> 180
                        in 225..315 -> 90
                        else -> 0
                    }
                    setUiOrientation(uiAngle)
                }
            }
        }

        viewModel.viewModelScope.launch {
            val supported = deviceSupported.await()
            val permissions = permissionsGranted.await()

            analytics.logEvent(Analytics.Event.PolishInit(supported, permissions))

            when {
                !supported -> {
                    Toast.makeText(this@PolishActivity, "device not supported", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
                !permissions -> {
                    Toast.makeText(this@PolishActivity, "Permissions Required", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
                else -> {
                    if (finishedIntro) {
                        onReady()
                    } else {
                        showIntro()
                    }
                }
            }
        }
    }

    private fun showIntro() {
        preferences.edit {
            putBoolean(FINISHED_INTRO, true)
        }
        onReady()
    }

    private fun onReady() {
        viewModel.initializeEffect()
        val behavior = BottomSheetBehavior.from(layout_colors)
        var recording = false
        viewModel.setSurfaceView(surface_view)

        lut_list.adapter = LutAdapter(EffectExecutor.LUTS, viewModel)

        lut_list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val spans = 3
            private val margin = resources.getDimension(R.dimen.connector_margin).roundToInt()
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val index = (view.layoutParams as GridLayoutManager.LayoutParams).spanIndex % spans
                val left = margin * (spans - index) / spans
                val right = margin * (index + 1) / spans
                outRect.set(left, 0, right, margin)
            }
        })

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private var started = false

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                Log.d(TAG, "state changed $newState")
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        Log.d(TAG, "stop lut previews")
                        started = false
                        viewModel.stopLutPreview()
                    }
                    else -> {
                        if (!started) {
                            Log.d(TAG, "start lut previews")
                            started = true
                            viewModel.startLutPreview()
                        }
                    }
                }
            }
        })

        button_color.setOnClickListener {
            Log.d(TAG, "show luts")
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        button_lut_close.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        lut_strength.max = 1000
        lut_strength.progress = (viewModel.properties[LutStrength] * 1000).toInt()
        lut_strength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.setLutStrength(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                viewModel.editProperty(LutStrength, lut_strength.progress / 1000f)
            }
        })

        button_camera.setOnClickListener(throttleClick {
            Log.d(TAG, "flip camera")
            viewModel.flipCamera()
        })
        var updateRecordingTime: Job? = null
        button_record.setOnClickListener(throttleClick {
            recording = !recording
            if (recording) {
                focusMode()
                viewModel.startRecording()
                video_duration.visibility = View.VISIBLE
                updateRecordingTime = viewModel.updateRecordingTime(video_duration)
            } else {
                exploreMode()
                highlightGalleryButton()
                video_duration.visibility = View.GONE
                updateRecordingTime?.cancel()
                viewModel.stopRecording()
            }
            Log.d(TAG, "recording $recording")
        })

        button_settings.setOnClickListener {
            Log.d(TAG, "show settings")
            analytics.logEvent(Analytics.Event.OpenSettings())
            SettingsDialog().show(supportFragmentManager, "settings")
        }

        button_gallery.setOnClickListener {
            analytics.logEvent(Analytics.Event.OpenGallery())
            Intent(this, GalleryActivity::class.java).also {
                startActivity(it)
            }
        }

        val effectAdapter = EffectAdapter {
            viewModel.setEffectProperty(it.property)
        }
        effect_list.adapter = effectAdapter

        var effects = emptyList<Network>()
        viewModel.effects.observe(this, Observer {
            effects = it
            effectAdapter.setEffects(it)
        })

        effect_list.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val effect = effects[position]
                viewModel.setEffect(effect)
                Log.d(TAG, "pos $position $effect")
            }
        })
    }

    private fun setUiOrientation(rotation: Int) {
        listOf(
            button_camera,
            button_settings,
            button_color,
            button_gallery
        ).forEach {
            it.animate()
                .rotation(rotation.toFloat())
                .setInterpolator(interpolator)
                .setDuration(200)
        }
    }

    private fun highlightGalleryButton() {
        button_gallery.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setInterpolator {
                kotlin.math.sin(it * 6 * Math.PI).toFloat()
            }
            .setDuration(600)
            .withEndAction {
                button_gallery.scaleX = 1f
                button_gallery.scaleY = 1f
            }
            .start()
    }

    private fun focusMode() {
        listOf(
            button_camera,
            button_settings,
            button_gallery
        ).forEach {
            it.animate()
                .setInterpolator(interpolator)
                .alpha(0f)
                .setDuration(200)
                .withEndAction { it.visibility = View.GONE }
                .start()
        }
    }

    private fun exploreMode() {
        listOf(
            button_camera,
            button_settings,
            button_gallery
        ).forEach {
            it.visibility = View.VISIBLE
            it.animate()
                .setInterpolator(interpolator)
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun checkPermissions(): List<Permission> {
        return PERMISSIONS.map {
            Permission(
                it,
                checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED,
                shouldShowRequestPermissionRationale(it)
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val perms = checkPermissions()
        val neededPermissions = perms.filter { !it.granted }
        permissionsGranted.complete(neededPermissions.isEmpty())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
        viewModel.startExecution()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener.disable()
        viewModel.stopExecution()
    }

    private fun hideSystemUI() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    companion object {
        const val TAG = "PolishActivity"
        const val PREF_NAME = "intro"
        const val FINISHED_INTRO = "finished_intro"
        val PERMISSIONS = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) else listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

private data class Permission(val name: String, val granted: Boolean, val showRationale: Boolean)

private class EffectAdapter(
    private val onClick: (PropertyItem) -> Unit
) : RecyclerView.Adapter<EffectViewHolder>() {
    private var effects: List<Network> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect, parent, false)
        return EffectViewHolder(view, onClick)
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        holder.bind(effects[position])
    }

    fun setEffects(list: List<Network>) {
        effects = list
        notifyDataSetChanged()
    }
}

private class EffectViewHolder(
    itemView: View,
    onClick: (PropertyItem) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val nameView = itemView.name
    private val settingsList = itemView.settings_list
    private val adapter = PropertiesAdapter(onClick)

    init {
        settingsList.adapter = adapter
    }

    fun bind(effect: Network) {
        nameView.text = effect.name
        adapter.setProperties(effect.getProperties())
    }
}

private fun Network.getProperties(): MutableList<Pair<Property<*>, PropertyHolder<*>>> {
    val list = mutableListOf<Pair<Property<*>, PropertyHolder<*>>>()
    properties.forEach { entry ->
        val node = getNode(entry.key)!!
        entry.value.forEach {  p ->
            NodeUi[node.type][p.key]?.let {
                list += Pair(p, it)
            }
        }
    }
    return list
}

private class LutAdapter(
    private val luts: List<String>,
    private val viewModel: PolishViewModel
) : RecyclerView.Adapter<LutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_lut, parent, false)
        return LutViewHolder(view, viewModel)
    }

    override fun getItemCount(): Int {
        return luts.size
    }

    override fun onBindViewHolder(holder: LutViewHolder, position: Int) {
        holder.bind(luts[position])
    }
}

private class LutViewHolder(
    itemView: View,
    private val viewModel: PolishViewModel
) : RecyclerView.ViewHolder(itemView), TextureView.SurfaceTextureListener {
    private var lut: String? = null
    private var created = false

    init {
        Log.d("Lut", "onCreateViewHolder $this")
        itemView.texture_view.surfaceTextureListener = this
        itemView.setOnClickListener {
            lut?.let { it1 -> viewModel.setLut(it1) }
        }
    }

    fun bind(lut: String) {
//        Log.d("Lut", "onBindViewHolder $lut ${this.lut} ${itemView.texture_view.surfaceTexture} ${itemView.texture_view.isAvailable}")
        if (created) {
            Log.w("Lut", "destroy not called")
        }
        this.lut = lut
        itemView.title_view.text = lut.replace("_", " ")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Log.d("Lut", "surface size: $lut")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
//        Log.d("Lut", "surface destroyed $lut $surface")
        created = false
        surface?.let { viewModel.unregisterLutPreview(it) }
        return false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
//        Log.d("Lut", "surface available $lut $surface")
        created = true

        surface?.let {
            val s = min(width, 320)
            it.setDefaultBufferSize(s, s)
        }
//        if (adapterPosition % 3 == 0)
        lut?.let { viewModel.registerLutPreview(itemView.texture_view, it) }
    }
}


private class PropertiesAdapter(
    private val onSelected: (PropertyItem) -> Unit
) : RecyclerView.Adapter<PropertyViewHolder>() {
    private var list = emptyList<PropertyItem>()
    private val clickListener: (position: Int) -> Unit = { position ->
        val p = list[position]
        onSelected(p)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.layout_property, parent, false)

        return when (viewType) {
            2 -> ToggleViewHolder(view, clickListener)
            else -> SingleValueViewHolder(view, clickListener)
        }
    }

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int {
        return when (list[position].holder) {
            is ExpandedHolder<*> -> 1
            is ToggleHolder<*> -> 2
            else -> 0
        }
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val property = list[position]
        holder.bind(property)
        Log.d(TAG, "onBind $position $property")
    }

    fun setProperties(properties: List<Pair<Property<*>, PropertyHolder<*>>>) {
        val new = mutableListOf<PropertyItem>()
        properties.forEach {
            when (it.second) {
                is ExpandedHolder<*> -> {
                    (it.second as ExpandedHolder).choices.forEach { choice ->
                        new.add(
                            PropertyItem(
                                it.first as Property<Any?>,
                                it.second,
                                choice.icon,
                                choice.item
                            )
                        )
                    }
                }
                is ToggleHolder<*> -> {
                    new.add(PropertyItem(it.first as Property<Any?>, it.second, it.second.icon))
                }
                else -> {
                    new.add(
                        PropertyItem(
                            it.first as Property<Any?>,
                            it.second,
                            it.second.icon,
                            it.first.value
                        )
                    )
                }
            }
        }

        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
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

private abstract class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
    private var toggleType: ToggleHolder<*>? = null

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
        val type = propertyItem.holder as ToggleHolder<*>
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
            textView.setText(choice.label)
            iconView.setImageResource(choice.icon)
        }
    }
}

private data class PropertyItem(
    val property: Property<Any?>,
    val holder: PropertyHolder<*>,
    @DrawableRes val icon: Int,
    var value: Any? = null
)