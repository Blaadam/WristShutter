package com.wristshutter

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/** Logs the camera's node tree so you can find button IDs: `adb logcat -s WristShutter`. */
object NodeDumper {
    fun dump(root: AccessibilityNodeInfo) {
        Log.i(TAG, "===== UI dump: ${root.packageName} =====")
        walk(root, 0)
        Log.i(TAG, "===== end dump =====")
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int) {
        val bounds = Rect().also(node::getBoundsInScreen)
        Log.i(
            TAG,
            "  ".repeat(depth) +
                "id=${node.viewIdResourceName ?: "-"}" +
                " class=${node.className?.toString()?.substringAfterLast('.') ?: "-"}" +
                " text=${node.text?.let { "\"$it\"" } ?: "-"}" +
                " desc=${node.contentDescription?.let { "\"$it\"" } ?: "-"}" +
                " clickable=${node.isClickable}" +
                (if (node.isSelected) " selected" else "") +
                // e.g. the mode carousel is a SeekBar; its position identifies Photo/Video in any language
                (node.rangeInfo?.let { " range=${it.current}/${it.min}..${it.max}" } ?: "") +
                " bounds=${bounds.toShortString()}",
        )
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
    }
}
