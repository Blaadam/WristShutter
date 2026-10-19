package com.wristshutter

import android.content.Context
import android.content.SharedPreferences

/**
 * All user-editable settings. Everything a One UI update can break lives here, so it can be
 * fixed from the settings screen without a rebuild.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("wristshutter", Context.MODE_PRIVATE)

    /** View resource IDs, one per line, without or with the "package:id/" prefix. */
    fun viewIds(cmd: Command): List<String> =
        lines(sp.getString("ids_${cmd.wire}", null) ?: DEFAULT_IDS[cmd].orEmpty().joinToString("\n"))

    fun setViewIds(cmd: Command, raw: String) = putOverride("ids_${cmd.wire}", raw, DEFAULT_IDS[cmd])

    /** Content-description substrings, one per line, matched case-insensitively and tried in order. */
    fun descriptions(cmd: Command): List<String> =
        lines(sp.getString("desc_${cmd.wire}", null) ?: DEFAULT_DESCRIPTIONS[cmd].orEmpty().joinToString("\n"))

    fun setDescriptions(cmd: Command, raw: String) = putOverride("desc_${cmd.wire}", raw, DEFAULT_DESCRIPTIONS[cmd])

    /**
     * Stores [raw] only if it differs from the built-in default, so that improved defaults in a
     * later app version still apply to anything the user never changed.
     */
    private fun putOverride(key: String, raw: String, default: List<String>?) {
        val edit = sp.edit()
        if (lines(raw) == default.orEmpty()) edit.remove(key) else edit.putString(key, raw)
        edit.apply()
    }

    /** Clears every per-button override (IDs, descriptions, tap points). */
    fun resetButtons() {
        val edit = sp.edit()
        for (cmd in CAMERA_COMMANDS) {
            listOf("ids_", "desc_", "x_", "y_").forEach { edit.remove(it + cmd.wire) }
        }
        edit.apply()
    }

    /** Fallback tap position as percentages (0-100) of screen width and height, or null if unset. */
    fun tapPoint(cmd: Command): Pair<Float, Float>? {
        val x = sp.getFloat("x_${cmd.wire}", -1f)
        val y = sp.getFloat("y_${cmd.wire}", -1f)
        return if (x in 0f..100f && y in 0f..100f) x to y else null
    }

    fun setTapPoint(cmd: Command, x: Float?, y: Float?) =
        sp.edit().putFloat("x_${cmd.wire}", x ?: -1f).putFloat("y_${cmd.wire}", y ?: -1f).apply()

    /** Open Samsung Camera when shoot or video arrives while it is closed (spec §7.8). */
    var launchCamera: Boolean
        get() = sp.getBoolean("launch_camera", true)
        set(v) = sp.edit().putBoolean("launch_camera", v).apply()

    var dumpUi: Boolean
        get() = sp.getBoolean("dump_ui", false)
        set(v) = sp.edit().putBoolean("dump_ui", v).apply()

    /** Debug builds only: talk to the Connect IQ simulator over ADB instead of Garmin Connect. */
    var tethered: Boolean
        get() = BuildConfig.DEBUG && sp.getBoolean("tethered", false)
        set(v) = sp.edit().putBoolean("tethered", v).apply()

    private fun lines(raw: String) = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** Commands the user can configure (ping needs no camera button). */
        val CAMERA_COMMANDS = listOf(Command.SHOOT, Command.VIDEO, Command.FLIP)

        // Defaults from the node dump on the S26 Ultra (Samsung Camera 16.5.01.29, English UI, 2026-10-18).
        //
        // The photo shutter and the video record button are the same view (center_button_container);
        // only its description changes with the mode ("Take picture" vs "Start recording"). Clicking it
        // by ID would take a photo when asked to record, or record when asked to shoot, so shoot and
        // video match by description only and fail with NOT_FOUND in the wrong mode.
        // While recording, Stop is a separate view (stop_button) that only exists then, so its ID is safe.
        private val DEFAULT_IDS = mapOf(
            Command.VIDEO to listOf("stop_button"),
            Command.FLIP to listOf("switch_camera_button"),
        )

        private val DEFAULT_DESCRIPTIONS = mapOf(
            Command.SHOOT to listOf("Take picture"),
            Command.VIDEO to listOf("Start recording", "Stop recording"),
            Command.FLIP to listOf("Switch to front camera", "Switch to rear camera"),
        )
    }
}
