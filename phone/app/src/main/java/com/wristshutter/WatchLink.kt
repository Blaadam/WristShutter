package com.wristshutter

import android.content.Context
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the Connect IQ SDK connection. Lives inside the accessibility service so the system keeps
 * it alive. Messages from the watch arrive on [onMessage]; acks go back via [reply].
 */
class WatchLink(
    baseContext: Context,
    private val tethered: Boolean,
    private val onMessage: (IQDevice, Map<*, *>) -> Unit,
) {
    // Every SDK call that takes a Context gets this one, so its receiver is crash-proofed.
    private val context: Context = SafeReceiverContext(baseContext)
    private val app = IQApp(Protocol.APP_UUID)
    private var ciq: ConnectIQ? = null

    fun start() {
        val type = if (tethered) ConnectIQ.IQConnectType.TETHERED else ConnectIQ.IQConnectType.WIRELESS
        Log.i(TAG, "Starting Connect IQ ($type)")
        val c = ConnectIQ.getInstance(context, type)
        if (tethered) c.adbPort = Protocol.ADB_PORT
        ciq = c
        // autoUI = false: a service must not pop "install Garmin Connect" dialogs.
        c.initialize(context, false, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                Status.sdkReady = true
                registerAll()
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                Log.w(TAG, "Connect IQ init failed: $status")
                Status.sdkReady = false
            }

            override fun onSdkShutDown() {
                Status.sdkReady = false
            }
        })
    }

    private fun registerAll() {
        val c = ciq ?: return
        val devices = try {
            c.knownDevices.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "knownDevices failed", e)
            emptyList()
        }
        if (devices.isEmpty()) Log.w(TAG, "No known Garmin devices. Is the watch paired in Garmin Connect?")
        for (device in devices) {
            Log.i(TAG, "Registering for ${device.friendlyName} (${device.deviceIdentifier}) status=${device.status}")
            try {
                // Use the known-device object for the name: the IQDevice passed to device events
                // can have an empty friendlyName.
                c.registerForDeviceEvents(device) { _, status ->
                    Log.i(TAG, "${device.friendlyName}: $status")
                    Status.updateDevice(device.friendlyName, status == IQDevice.IQDeviceStatus.CONNECTED)
                }
                c.registerForAppEvents(device, app) { d, _, data, status ->
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                        Log.w(TAG, "Bad message from ${d.friendlyName}: $status")
                        return@registerForAppEvents
                    }
                    data.filterIsInstance<Map<*, *>>().forEach { onMessage(d, it) }
                }
                Status.updateDevice(device.friendlyName, c.getDeviceStatus(device) == IQDevice.IQDeviceStatus.CONNECTED)
            } catch (e: Exception) {
                Log.w(TAG, "Register failed for ${device.friendlyName}", e)
            }
        }
    }

    /**
     * Asks the SDK for each known device's current status. Device events alone are not reliable:
     * none arrived after the watch came back from USB mass-storage mode, leaving the status stale.
     */
    fun refreshDeviceStatus() {
        val c = ciq ?: return
        if (!Status.sdkReady) return
        try {
            for (device in c.knownDevices.orEmpty()) {
                Status.updateDevice(device.friendlyName, c.getDeviceStatus(device) == IQDevice.IQDeviceStatus.CONNECTED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device status refresh failed", e)
        }
    }

    fun reply(device: IQDevice, id: Int, result: CommandResult) =
        send(device, id, hashMapOf("ack" to id, "ok" to result.ok, "err" to result.err))

    /** Interim ack: the command needs longer (camera cold start), so the watch should keep waiting. */
    fun replyWait(device: IQDevice, id: Int) = send(device, id, hashMapOf("ack" to id, "wait" to true))

    private fun send(device: IQDevice, id: Int, msg: HashMap<String, Any>) {
        try {
            ciq?.sendMessage(device, app, msg) { _, _, status ->
                if (status != ConnectIQ.IQMessageStatus.SUCCESS) Log.w(TAG, "Ack $id failed: $status")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage failed", e)
        }
    }

    fun stop() {
        val c = ciq ?: return
        ciq = null
        try {
            c.unregisterAllForEvents()
            c.shutdown(context)
        } catch (e: Exception) {
            Log.w(TAG, "Connect IQ shutdown failed", e)
        }
        Status.sdkReady = false
        Status.clear()
    }

    /** Read by the settings screen. */
    object Status {
        @Volatile var sdkReady = false
        private val devices = ConcurrentHashMap<String, Boolean>()

        val connectedDevices: List<String> get() = devices.filterValues { it }.keys.sorted()

        fun updateDevice(name: String, connected: Boolean) {
            devices[name] = connected
        }

        fun clear() = devices.clear()
    }
}
