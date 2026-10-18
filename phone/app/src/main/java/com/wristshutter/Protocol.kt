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
}

enum class Command(val wire: String) {
    SHOOT("shoot"),
    VIDEO("video"),
    FLIP("flip"),
    PING("ping");

    companion object {
        fun fromWire(s: String?): Command? = entries.firstOrNull { it.wire == s }
    }
}

data class CommandResult(val ok: Boolean, val err: String = "") {
    companion object {
        val OK = CommandResult(true)
        fun fail(err: String) = CommandResult(false, err)
    }
}
