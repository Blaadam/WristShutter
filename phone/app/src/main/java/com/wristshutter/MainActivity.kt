package com.wristshutter

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Single plain settings screen, built with programmatic Views (no Compose, no AppCompat). */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    // Separate from [handler] so a pending test survives onPause while you switch to the camera.
    private val testHandler = Handler(Looper.getMainLooper())

    private lateinit var a11yStatus: TextView
    private lateinit var sdkStatus: TextView
    private lateinit var watchStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var recentLog: TextView

    private val idFields = mutableMapOf<Command, EditText>()
    private val descFields = mutableMapOf<Command, EditText>()
    private val xFields = mutableMapOf<Command, EditText>()
    private val yFields = mutableMapOf<Command, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(col)
        })

        col.header("Status")
        a11yStatus = col.statusRow("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        sdkStatus = col.statusRow(null, null)
        watchStatus = col.statusRow(null, null)
        batteryStatus = col.statusRow("Ignore battery optimisation") {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        }

        col.header("Test (runs after 3 s, switch to the camera)")
        for (cmd in Prefs.CAMERA_COMMANDS) {
            col.button("Test ${cmd.wire}") { runTest(cmd) }
        }

        col.header("Recent commands")
        recentLog = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }
        col.addView(recentLog)

        col.header("Behaviour")
        col.addView(Switch(this).apply {
            text = "Open Samsung Camera if it's closed (shoot opens Photo, video opens Video)"
            isChecked = prefs.launchCamera
            setOnCheckedChangeListener { _, on -> prefs.launchCamera = on }
        })

        col.header("Debug")
        col.addView(Switch(this).apply {
            text = "Dump camera UI to logcat (adb logcat -s WristShutter)"
            isChecked = prefs.dumpUi
            setOnCheckedChangeListener { _, on -> prefs.dumpUi = on }
        })
        if (BuildConfig.DEBUG) {
            col.addView(Switch(this).apply {
                text = "TETHERED mode (Connect IQ simulator over adb port ${Protocol.ADB_PORT})"
                isChecked = prefs.tethered
                setOnCheckedChangeListener { _, on ->
                    prefs.tethered = on
                    ShutterAccessibilityService.instance?.startLink()
                }
            })
        }

        for (cmd in Prefs.CAMERA_COMMANDS) {
            col.header("Button: ${cmd.wire}")
            idFields[cmd] = col.field("View IDs, one per line (e.g. shutter_button)", multiLine = true)
                .apply { setText(prefs.viewIds(cmd).joinToString("\n")) }
            descFields[cmd] = col.field("Content descriptions, one per line", multiLine = true)
                .apply { setText(prefs.descriptions(cmd).joinToString("\n")) }
            val point = prefs.tapPoint(cmd)
            xFields[cmd] = col.field("Fallback tap X, % of width (blank = off)", decimal = true)
                .apply { setText(point?.first?.toString().orEmpty()) }
            yFields[cmd] = col.field("Fallback tap Y, % of height (blank = off)", decimal = true)
                .apply { setText(point?.second?.toString().orEmpty()) }
        }
        col.button("Save button settings") { save() }
        col.button("Reset button settings to defaults") {
            prefs.resetButtons()
            toast("Reset to defaults")
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun refreshStatus() {
        val serviceOn = isServiceEnabled()
        a11yStatus.text = "Accessibility service enabled: ${yesNo(serviceOn)}"
        sdkStatus.text = "Connect IQ SDK ready: ${yesNo(WatchLink.Status.sdkReady)}" +
            if (prefs.tethered) " (TETHERED)" else ""
        ShutterAccessibilityService.instance?.refreshDeviceStatus()
        val watches = WatchLink.Status.connectedDevices
        watchStatus.text = "Watch connected: " + if (watches.isEmpty()) "no" else watches.joinToString()
        val pm = getSystemService(PowerManager::class.java)
        batteryStatus.text = "Battery optimisation ignored: ${yesNo(pm.isIgnoringBatteryOptimizations(packageName))}"
        recentLog.text = formatLog()
        // The SDK connects asynchronously, so keep polling while the screen is visible.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(::refreshStatus, 2000)
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, ShutterAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun runTest(cmd: Command) {
        save()
        toast("Running ${cmd.wire} in 3 s")
        testHandler.postDelayed({
            val service = ShutterAccessibilityService.instance
            if (service == null) {
                toast("${cmd.wire}: ${Protocol.NO_A11Y}")
                return@postDelayed
            }
            service.handleCommand(cmd, null, "test") { result ->
                toast("${cmd.wire}: " + if (result.ok) "OK" else result.err)
            }
        }, 3000)
    }

    private fun save() {
        for (cmd in Prefs.CAMERA_COMMANDS) {
            prefs.setViewIds(cmd, idFields.getValue(cmd).text.toString())
            prefs.setDescriptions(cmd, descFields.getValue(cmd).text.toString())
            prefs.setTapPoint(
                cmd,
                xFields.getValue(cmd).text.toString().toFloatOrNull(),
                yFields.getValue(cmd).text.toString().toFloatOrNull(),
            )
        }
        toast("Saved")
    }

    private fun formatLog(): String {
        val entries = CommandLog.recent()
        if (entries.isEmpty()) return "No commands yet"
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return entries.joinToString("\n") { e ->
            val outcome = if (e.result.ok) "OK" else e.result.err
            val via = if (e.result.via.isNotEmpty()) "  ${e.result.via}" else ""
            val what = e.cmd.wire + (e.level?.let { " ${it}x" } ?: "")
            "${time.format(e.at)}  ${e.source.padEnd(9)} ${what.padEnd(5)} $outcome$via"
        }
    }

    // Small view helpers

    private fun LinearLayout.header(title: String) = addView(TextView(context).apply {
        text = title
        textSize = 18f
        setPadding(0, dp(20), 0, dp(6))
    })

    private fun LinearLayout.statusRow(buttonLabel: String?, onClick: (() -> Unit)?): TextView {
        val label = TextView(context).apply { textSize = 15f }
        addView(label)
        if (buttonLabel != null && onClick != null) button(buttonLabel, onClick)
        return label
    }

    private fun LinearLayout.button(label: String, onClick: () -> Unit) = addView(Button(context).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    private fun LinearLayout.field(hint: String, multiLine: Boolean = false, decimal: Boolean = false): EditText {
        addView(TextView(context).apply { text = hint; textSize = 13f; setPadding(0, dp(8), 0, 0) })
        val edit = EditText(context).apply {
            inputType = when {
                decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                else -> InputType.TYPE_CLASS_TEXT
            }
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        addView(edit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return edit
    }

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
