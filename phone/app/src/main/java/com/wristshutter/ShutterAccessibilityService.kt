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
    private var lastCmd: Command? = null
    private var lastLevel: String? = null
    private var lastCmdAt = 0L

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
            val level = msg["level"] as? String
            Log.i(TAG, "From ${device.friendlyName}: $cmd id=$id" + (level?.let { " level=$it" } ?: ""))
            if (cmd == null) return@WatchLink
            val source = "watch #$id"
            if (isRepeat(cmd, level)) {
                Log.i(TAG, "$cmd id=$id ignored: repeat within ${REPEAT_WINDOW_MS} ms")
                val busy = CommandResult.fail(Protocol.BUSY)
                CommandLog.add(source, cmd, level, busy)
                link?.reply(device, id, busy)
                return@WatchLink
            }
            handleCommand(cmd, level, source, onLaunching = { link?.replyWait(device, id) }) { result ->
                Log.i(TAG, "$cmd id=$id -> $result")
                link?.reply(device, id, result)
            }
        }.also { it.start() }
    }

    /**
     * The same camera command again within [REPEAT_WINDOW_MS]. Two VIDEO presses 0.1 s apart once
     * started and stopped a recording instantly; the watch now blocks this too, but older watch
     * builds or a restarted watch app might not.
     */
    private fun isRepeat(cmd: Command, level: String?): Boolean {
        if (cmd == Command.PING) return false
        val now = SystemClock.elapsedRealtime()
        val repeat = cmd == lastCmd && level == lastLevel && now - lastCmdAt < REPEAT_WINDOW_MS
        if (!repeat) {
            lastCmd = cmd
            lastLevel = level
            lastCmdAt = now
        }
        return repeat
    }

    fun refreshDeviceStatus() {
        link?.refreshDeviceStatus()
    }

    /** The one code path used by both watch commands and the in-app test buttons. */
    fun handleCommand(
        cmd: Command,
        level: String?,
        source: String,
        onLaunching: () -> Unit = {},
        done: (CommandResult) -> Unit,
    ) {
        if (cmd == Command.PING) return done(CommandResult.OK)
        camera.perform(cmd, level, onLaunching) { result ->
            CommandLog.add(source, cmd, level, result)
            done(result)
        }
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
        private const val REPEAT_WINDOW_MS = 1000L

        /** Non-null while the service is connected. Used by the settings screen. */
        @Volatile var instance: ShutterAccessibilityService? = null
            private set
    }
}
