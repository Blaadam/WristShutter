package com.wristshutter

const val TAG = "WristShutter"

object Protocol {
    /** Shared with watch/manifest.xml, where it is written without dashes. */
    const val APP_UUID = "c9c13a59-0bd5-4c29-b2e2-a003d8158db8"

    const val CAMERA_PACKAGE = "com.sec.android.app.camera"

    /** Default port of the Connect IQ SDK's ADB bridge (AdbConnection in ciq-companion-app-sdk 2.2.0). */
    const val ADB_PORT = 7381

    const val NO_CAM = "NO_CAM"
    const val NOT_FOUND = "NOT_FOUND"
    const val GESTURE_FAIL = "GESTURE_FAIL"
    const val NO_A11Y = "NO_A11Y"

    /** The same command repeated within a second; ignored so a double press can't undo itself. */
    const val BUSY = "BUSY"
}

enum class Command(val wire: String) {
    SHOOT("shoot"),
    VIDEO("video"),
    FLIP("flip"),
    /** Carries "level", the lens factor as shown on Samsung's zoom buttons (e.g. "0.6", "3"). */
    ZOOM("zoom"),
    PING("ping");

    companion object {
        fun fromWire(s: String?): Command? = entries.firstOrNull { it.wire == s }
    }
}

/** [via] says which strategy pressed the button (e.g. "id stop_button", "desc 'Take picture'", "tap"). */
data class CommandResult(val ok: Boolean, val err: String = "", val via: String = "") {
    companion object {
        val OK = CommandResult(true)
        fun fail(err: String, via: String = "") = CommandResult(false, err, via)
    }
}
