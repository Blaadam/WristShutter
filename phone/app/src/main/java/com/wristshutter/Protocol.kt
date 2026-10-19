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

    /** Mode can't switch while recording (Samsung Camera blocks it too). */
    const val RECORDING = "RECORDING"

    /** The camera didn't reach the requested mode in time. */
    const val MODE_FAIL = "MODE_FAIL"
}

/** Camera state reported to the watch in every ack ("mode"), read from view IDs. */
object CameraMode {
    const val PHOTO = "photo"
    const val VIDEO = "video"
    const val RECORDING = "recording"
    /** Camera closed, or in a mode other than Photo/Video. */
    const val UNKNOWN = ""
}

enum class Command(val wire: String) {
    /** The shutter: a photo in Photo mode, start/stop recording in Video mode. */
    SHOOT("shoot"),
    /** Switch Photo <-> Video. */
    MODE("mode"),
    FLIP("flip"),
    /** Carries "level", the lens factor as shown on Samsung's zoom buttons (e.g. "0.6", "3"). */
    ZOOM("zoom"),
    PING("ping");

    companion object {
        fun fromWire(s: String?): Command? = entries.firstOrNull { it.wire == s }
    }
}

/**
 * [via] says which strategy pressed the button (e.g. "id stop_button", "desc 'Take picture'", "tap").
 * [mode] is the [CameraMode] the command leaves behind.
 */
data class CommandResult(val ok: Boolean, val err: String = "", val via: String = "", val mode: String = "") {
    companion object {
        val OK = CommandResult(true)
        fun fail(err: String, via: String = "", mode: String = "") = CommandResult(false, err, via, mode)
    }
}
