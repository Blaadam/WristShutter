package com.wristshutter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Finds and presses a Samsung Camera button using the strategy chain from the spec:
 * view ID, then content description, then a tap at configured coordinates.
 */
class CameraController(private val service: AccessibilityService, private val prefs: Prefs) {

    fun perform(cmd: Command, done: (CommandResult) -> Unit) {
        val root = findCameraRoot() ?: return done(CommandResult.fail(Protocol.NO_CAM))
        var foundSomething = false

        // 1. View resource ID
        for (id in prefs.viewIds(cmd)) {
            val fullId = if (':' in id) id else "${Protocol.CAMERA_PACKAGE}:id/$id"
            for (node in root.findAccessibilityNodeInfosByViewId(fullId)) {
                foundSomething = true
                if (click(node)) {
                    Log.i(TAG, "$cmd: clicked by id $fullId")
                    return done(CommandResult.OK)
                }
            }
        }

        // 2. Content description
        val descs = prefs.descriptions(cmd)
        if (descs.isNotEmpty()) {
            val nodes = allNodes(root).filter { !it.contentDescription.isNullOrBlank() }
            for (label in descs) {
                for (node in nodes.filter { it.contentDescription!!.contains(label, ignoreCase = true) }) {
                    foundSomething = true
                    if (click(node)) {
                        Log.i(TAG, "$cmd: clicked by description '${node.contentDescription}'")
                        return done(CommandResult.OK)
                    }
                }
            }
        }

        // 3. Coordinates
        val point = prefs.tapPoint(cmd)
            ?: return done(CommandResult.fail(if (foundSomething) Protocol.GESTURE_FAIL else Protocol.NOT_FOUND))
        tap(point.first, point.second) { ok ->
            Log.i(TAG, "$cmd: tap at ${point.first}%,${point.second}% ok=$ok")
            done(if (ok) CommandResult.OK else CommandResult.fail(Protocol.GESTURE_FAIL))
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
