package com.curtain.blocker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.curtain.blocker.data.Settings
import com.curtain.blocker.databinding.ActivityMainBinding
import com.curtain.blocker.service.CurtainAccessibilityService
import com.curtain.blocker.service.CurtainCommandReceiver
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val permissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* status is refreshed in onResume either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.openAccessibilityButton.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.masterSwitch.setOnCheckedChangeListener(masterListener)

        binding.previewButton.setOnClickListener {
            sendBroadcast(
                Intent(this, CurtainCommandReceiver::class.java)
                    .setAction(CurtainCommandReceiver.ACTION_PREVIEW)
                    .setPackage(packageName)
            )
        }

        binding.openInstagramButton.setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE)
            if (launch != null) {
                startActivity(launch)
            } else {
                android.widget.Toast
                    .makeText(this, "Instagram is not installed", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }

        buildOptions()
        setupSeekBars()
        requestNotificationsIfNeeded()
    }

    /**
     * Held as a field so onResume can sync the switch without the write-back
     * firing — otherwise merely opening the app would cancel an active snooze.
     */
    private val masterListener =
        android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            settings.enabled = checked
            if (checked) settings.snoozeUntil = 0L
            CurtainAccessibilityService.instance?.onSettingsChanged()
        }

    override fun onResume() {
        super.onResume()
        binding.masterSwitch.setOnCheckedChangeListener(null)
        binding.masterSwitch.isChecked = settings.enabled
        binding.masterSwitch.setOnCheckedChangeListener(masterListener)
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = isServiceEnabled(this)
        val snoozed = settings.snoozeUntil > System.currentTimeMillis()
        binding.statusText.text = when {
            !on -> "Not running. Turn on \"Curtain — short-form blocker\" under Accessibility → Installed apps."
            !settings.enabled -> "Service is running, blocking is switched off."
            snoozed -> "Paused. Resumes in about " +
                ((settings.snoozeUntil - System.currentTimeMillis()) / 60_000L + 1) + " min."
            else -> "Running. Instagram and YouTube are being watched."
        }
        binding.openAccessibilityButton.visibility = if (on) View.GONE else View.VISIBLE
    }

    // -- option rows --------------------------------------------------------

    private fun buildOptions() {
        val c = binding.optionsContainer
        header(c, "Instagram")
        toggle(c, "Black out the home feed", settings.blockIgFeed) { settings.blockIgFeed = it }
        toggle(c, "Black out the Reels tab", settings.blockIgReelsTab) { settings.blockIgReelsTab = it }
        toggle(c, "Black out Explore", settings.blockIgExplore) { settings.blockIgExplore = it }
        toggle(c, "Black out Stories", settings.blockIgStories) { settings.blockIgStories = it }
        toggle(c, "Allow reels opened from a DM", settings.allowReelsFromDm) {
            settings.allowReelsFromDm = it
        }
        toggle(c, "Allow reels opened from a profile", settings.allowReelsFromProfile) {
            settings.allowReelsFromProfile = it
        }
        toggle(c, "Lock scrolling inside an allowed reel", settings.blockScrollInAllowedReel) {
            settings.blockScrollInAllowedReel = it
        }

        header(c, "YouTube")
        toggle(c, "Black out the Shorts player", settings.blockYtShorts) { settings.blockYtShorts = it }
        toggle(c, "Cover Shorts shelves in feeds", settings.blockYtShortsShelf) {
            settings.blockYtShortsShelf = it
        }
        toggle(c, "Black out the whole home feed", settings.blockYtHome) { settings.blockYtHome = it }
    }

    private fun header(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(20)
            layoutParams = lp
        })
    }

    private fun toggle(
        parent: LinearLayout,
        label: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        parent.addView(MaterialSwitch(this).apply {
            text = label
            textSize = 15f
            isChecked = initial
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
            setOnCheckedChangeListener { _, checked ->
                onChange(checked)
                CurtainAccessibilityService.instance?.onSettingsChanged()
            }
        })
    }

    // -- geometry sliders ---------------------------------------------------

    private fun setupSeekBars() {
        bindSeek(binding.topBarSeek, binding.topBarLabel, "Top bar height", settings.topBarDp) {
            settings.topBarDp = it
        }
        bindSeek(
            binding.topRightSeek, binding.topRightLabel,
            "Top-right cut-out width", settings.topRightDp
        ) { settings.topRightDp = it }
        bindSeek(
            binding.bottomBarSeek, binding.bottomBarLabel,
            "Bottom strip height", settings.bottomBarDp
        ) { settings.bottomBarDp = it }
    }

    private fun bindSeek(
        seek: SeekBar,
        label: TextView,
        title: String,
        initial: Int,
        onChange: (Int) -> Unit
    ) {
        fun render(v: Int) {
            label.text = "$title — $v dp"
        }
        seek.progress = initial
        render(initial)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                render(value)
                if (fromUser) onChange(value)
            }

            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) {
                CurtainAccessibilityService.instance?.onSettingsChanged()
            }
        })
    }

    // -- misc ---------------------------------------------------------------

    private fun requestNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        fun isServiceEnabled(context: Context): Boolean {
            val expected =
                "${context.packageName}/${CurtainAccessibilityService::class.java.name}"
            val enabled = AndroidSettings.Secure.getString(
                context.contentResolver,
                AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
