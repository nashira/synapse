package com.rthqks.synapse.ui.splash

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import com.rthqks.synapse.R
import com.rthqks.synapse.ui.browse.NetworkListActivity
import dagger.android.support.DaggerAppCompatActivity

class SplashActivity : DaggerAppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions $states")

        val neededPermissions = states.filter { !it.granted }.map { it.name }

        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 123)
            return
        }

        if (finishedIntro) {
            quit()
            return
        } else {
            showIntro()
        }
    }

    private fun showIntro() {
        preferences.edit {
            putBoolean(FINISHED_INTRO, true)
        }
        quit()
    }

    private fun quit() {
        startActivity(Intent(this, NetworkListActivity::class.java))
        finish()
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
        quit()
//        val perms = checkPermissions()
//        val neededPermissions = perms.filter { it.second }.map { it.first }
//        if (neededPermissions.isNotEmpty()) {
//            finish()
//        }
    }

    companion object {
        const val TAG = "com.rthqks.synapse.polish.SplashActivity"
        const val PREF_NAME = "intro"
        const val FINISHED_INTRO = "finished_intro"
        val PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

data class Permission(val name: String, val granted: Boolean, val showRationale: Boolean)