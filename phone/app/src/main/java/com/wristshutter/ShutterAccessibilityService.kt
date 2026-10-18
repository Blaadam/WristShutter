package com.wristshutter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ShutterAccessibilityService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private lateinit var camera: CameraController
    private var link: WatchLink? = null
    private var lastDumpAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        camera = CameraController(this, prefs)
        instance = this
        startLink()
    }

    /** Restarts the Connect IQ connection, e.g. after switching WIRELESS/TETHERED in debug builds. */
    fun startLink() {
        link?.stop()
        link = WatchLink(this, prefs.tethered) { device, msg ->
            val id = (msg["id"] as? Number)?.toInt() ?: return@WatchLink
            val cmd = Command.fromWire(msg["cmd"] as? String)
            Log.i(TAG, "From ${device.friendlyName}: $cmd id=$id")
            if (cmd == null) return@WatchLink
            handleCommand(cmd) { result ->
                Log.i(TAG, "$cmd id=$id -> $result")
                link?.reply(device, id, result)
            }
        }.also { it.start() }
    }

    fun refreshDeviceStatus() {
        link?.refreshDeviceStatus()
    }

    /** The one code path used by both watch commands and the in-app test buttons. */
    fun handleCommand(cmd: Command, done: (CommandResult) -> Unit) {
        if (cmd == Command.PING) return done(CommandResult.OK)
        camera.perform(cmd, done)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!prefs.dumpUi || event.packageName != Protocol.CAMERA_PACKAGE) return
        val now = SystemClock.elapsedRealtime()
        // Content changes fire constantly in the viewfinder; dump on window changes and at most every 3 s otherwise.
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!windowChanged && now - lastDumpAt < 3000) return
        lastDumpAt = now
        camera.findCameraRoot()?.let(NodeDumper::dump)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        link?.stop()
        link = null
        if (instance === this) instance = null
    }

    companion object {
        /** Non-null while the service is connected. Used by the settings screen. */
        @Volatile var instance: ShutterAccessibilityService? = null
            private set
    }
}
