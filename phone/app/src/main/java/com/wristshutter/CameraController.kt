package com.wristshutter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Finds and presses a Samsung Camera button using the strategy chain from the spec:
 * view ID, then content description, then a tap at configured coordinates.
 */
class CameraController(private val service: AccessibilityService, private val prefs: Prefs) {
    private val handler = Handler(Looper.getMainLooper())

    /** [onLaunching] runs if the camera has to be opened first, which takes several seconds. */
    fun perform(cmd: Command, level: String?, onLaunching: () -> Unit, done: (CommandResult) -> Unit) {
        if (cmd == Command.ZOOM) return zoom(level, done)
        if (findCameraRoot() != null) return performNow(cmd, done)
        if (!prefs.launchCamera || !launchCamera(cmd)) return done(CommandResult.fail(Protocol.NO_CAM))
        onLaunching()
        waitForButton(cmd, SystemClock.elapsedRealtime() + LAUNCH_TIMEOUT_MS, done)
    }

    /** Polls until the camera window shows the target button, then presses it; NO_CAM on timeout. */
    private fun waitForButton(cmd: Command, deadline: Long, done: (CommandResult) -> Unit) {
        val root = findCameraRoot()
        if (root != null && findTargets(root, cmd).isNotEmpty()) {
            Log.i(TAG, "$cmd: camera ready after launch")
            // A short settle: the button can be on screen a moment before it accepts clicks.
            handler.postDelayed({ performNow(cmd, done) }, SETTLE_MS)
            return
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            Log.w(TAG, "$cmd: camera not ready within ${LAUNCH_TIMEOUT_MS} ms of launch")
            // If the camera did open, let the normal chain decide (e.g. NOT_FOUND in the wrong mode).
            return if (root != null) performNow(cmd, done) else done(CommandResult.fail(Protocol.NO_CAM))
        }
        handler.postDelayed({ waitForButton(cmd, deadline, done) }, POLL_MS)
    }

    private fun performNow(cmd: Command, done: (CommandResult) -> Unit) {
        val root = findCameraRoot() ?: return done(CommandResult.fail(Protocol.NO_CAM))

        // 1 and 2: view ID, then content description
        val targets = findTargets(root, cmd)
        for (target in targets) {
            if (click(target.node)) {
                Log.i(TAG, "$cmd: clicked by ${target.via}")
                return done(CommandResult(true, via = target.via))
            }
        }

        // 3. Coordinates
        val point = prefs.tapPoint(cmd)
            ?: return done(CommandResult.fail(if (targets.isNotEmpty()) Protocol.GESTURE_FAIL else Protocol.NOT_FOUND))
        tap(point.first, point.second) { ok ->
            Log.i(TAG, "$cmd: tap at ${point.first}%,${point.second}% ok=$ok")
            done(if (ok) CommandResult(true, via = "tap") else CommandResult.fail(Protocol.GESTURE_FAIL, via = "tap"))
        }
    }

    /**
     * Presses the lens button for [level]. Samsung's lens buttons share one ID, and their labels
     * read "Zoom 3, Button"; the number is matched, not the words, so this works in any language.
     */
    private fun zoom(level: String?, done: (CommandResult) -> Unit) {
        val root = findCameraRoot() ?: return done(CommandResult.fail(Protocol.NO_CAM))
        val wanted = level?.toFloatOrNull() ?: return done(CommandResult.fail(Protocol.NOT_FOUND))
        val button = allNodes(root).firstOrNull { node ->
            node.viewIdResourceName.orEmpty().contains(ZOOM_ID_PART) &&
                node.contentDescription?.let { NUMBER.find(it)?.value?.toFloatOrNull() } == wanted
        } ?: return done(CommandResult.fail(Protocol.NOT_FOUND)) // e.g. front camera: no lens buttons
        val via = "zoom '${button.contentDescription}'"
        Log.i(TAG, "ZOOM $level: clicking $via")
        done(if (click(button)) CommandResult(true, via = via) else CommandResult.fail(Protocol.GESTURE_FAIL, via))
    }

    private class Target(val node: AccessibilityNodeInfo, val via: String)

    /** Candidate nodes for [cmd], in strategy order: view IDs first, then content descriptions. */
    private fun findTargets(root: AccessibilityNodeInfo, cmd: Command): List<Target> {
        val out = ArrayList<Target>()
        for (id in prefs.viewIds(cmd)) {
            val fullId = if (':' in id) id else "${Protocol.CAMERA_PACKAGE}:id/$id"
            root.findAccessibilityNodeInfosByViewId(fullId).forEach { out += Target(it, "id ${fullId.substringAfter(":id/")}") }
        }
        out += modeTargets(root, cmd)
        val descs = prefs.descriptions(cmd)
        if (descs.isNotEmpty()) {
            val nodes = allNodes(root).filter { !it.contentDescription.isNullOrBlank() }
            for (label in descs) {
                nodes.filter { it.contentDescription!!.contains(label, ignoreCase = true) }
                    .forEach { out += Target(it, "desc '${it.contentDescription}'") }
            }
        }
        return out
    }

    /**
     * The shutter and record button are one view whose meaning depends on the mode. Samsung Camera
     * names a layer after the current mode (shooting_mode_photo_main / shooting_mode_video_main,
     * verified on One UI 9 with Camera 17.0), so the mode can be read without any translated text.
     * Shoot presses the center button only in Photo mode, video only in Video mode.
     */
    private fun modeTargets(root: AccessibilityNodeInfo, cmd: Command): List<Target> {
        val modeLayer = when (cmd) {
            Command.SHOOT -> PHOTO_MODE_LAYER
            Command.VIDEO -> VIDEO_MODE_LAYER
            else -> return emptyList()
        }
        if (root.findAccessibilityNodeInfosByViewId(cameraId(modeLayer)).isEmpty()) return emptyList()
        return root.findAccessibilityNodeInfosByViewId(cameraId(CENTER_BUTTON))
            .map { Target(it, "mode ${modeLayer.substringAfter("shooting_mode_").substringBefore("_main")} + $CENTER_BUTTON") }
    }

    private fun cameraId(id: String) = "${Protocol.CAMERA_PACKAGE}:id/$id"

    /**
     * Opens Samsung Camera straight into the mode the command needs. Allowed from the background
     * because a system-bound accessibility service is exempt from background activity launch limits.
     * Skipped with the screen off: shooting blind is not a goal (spec §11).
     */
    private fun launchCamera(cmd: Command): Boolean {
        if (cmd != Command.SHOOT && cmd != Command.VIDEO) return false
        if (!service.getSystemService(PowerManager::class.java).isInteractive) {
            Log.i(TAG, "$cmd: camera closed and screen off, not launching")
            return false
        }
        val locked = service.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val action = when {
            cmd == Command.VIDEO -> MediaStore.INTENT_ACTION_VIDEO_CAMERA
            locked -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
            else -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }
        val intent = Intent(action)
            .setPackage(Protocol.CAMERA_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            service.startActivity(intent)
            Log.i(TAG, "$cmd: launched camera ($action, locked=$locked)")
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "$cmd: camera launch failed", e)
            false
        }
    }

    /** Root of the Samsung Camera window if it is the active app, else null. */
    fun findCameraRoot(): AccessibilityNodeInfo? {
        service.rootInActiveWindow?.let { if (it.packageName == Protocol.CAMERA_PACKAGE) return it }
        // The active window can be a system overlay; fall back to the focused application window.
        return service.windows
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
            ?.root
            ?.takeIf { it.packageName == Protocol.CAMERA_PACKAGE }
    }

    /** Clicks the node or its nearest clickable ancestor. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.isEnabled) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tap(xPct: Float, yPct: Float, done: (Boolean) -> Unit) {
        val bounds = service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val path = Path().apply { moveTo(bounds.width() * xPct / 100f, bounds.height() * yPct / 100f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) = done(true)
            override fun onCancelled(g: GestureDescription) = done(false)
        }, null)
        if (!dispatched) done(false)
    }

    companion object {
        // A cold start of Samsung Camera took 5 s on One UI 9, and querying its window blocks while
        // it starts. The watch extends its own timeout to 10 s when told the camera is launching.
        private const val LAUNCH_TIMEOUT_MS = 8000L
        private const val POLL_MS = 150L
        private const val SETTLE_MS = 250L
        // View ID of the lens buttons: com.sec.android.app.camera:id/zoom_lens_text_button_main
        private const val ZOOM_ID_PART = "zoom_lens"
        private const val CENTER_BUTTON = "center_button_container"
        private const val PHOTO_MODE_LAYER = "shooting_mode_photo_main"
        private const val VIDEO_MODE_LAYER = "shooting_mode_video_main"
        private val NUMBER = Regex("""\d+(\.\d+)?""")

        fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val out = ArrayList<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                out += n
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
            return out
        }
    }
}
