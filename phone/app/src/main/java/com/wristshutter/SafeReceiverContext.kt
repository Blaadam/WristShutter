package com.wristshutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Context handed to the Connect IQ SDK. The SDK registers its IQMessageReceiver on this context,
 * and its onReceive deserializes the watch payload on the main thread without catching errors.
 * One corrupted message ("IllegalArgumentException: 8 > -119128774" in Serializer.deserialize,
 * seen 2026-10-18) killed the whole process, and with it the accessibility service. Here each
 * receiver is wrapped so a bad broadcast is logged and dropped instead.
 *
 * The same instance must be passed to ConnectIQ.getInstance, initialize and shutdown, so that
 * unregisterReceiver can find the wrapper.
 */
class SafeReceiverContext(base: Context) : ContextWrapper(base) {
    private val wrappers = HashMap<BroadcastReceiver, BroadcastReceiver>()

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? =
        super.registerReceiver(receiver?.let(::wrap), filter)

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? =
        super.registerReceiver(receiver?.let(::wrap), filter, flags)

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        val wrapper = synchronized(wrappers) { receiver?.let(wrappers::remove) }
        super.unregisterReceiver(wrapper ?: receiver)
    }

    private fun wrap(inner: BroadcastReceiver): BroadcastReceiver = synchronized(wrappers) {
        wrappers.getOrPut(inner) {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    try {
                        inner.onReceive(context, intent)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Dropped malformed Connect IQ broadcast ${intent.action}", e)
                    }
                }
            }
        }
    }
}
