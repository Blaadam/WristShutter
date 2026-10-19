package com.wristshutter

/**
 * The last few commands and their results, kept in memory for the settings screen so everyday
 * checks don't need adb. The service and the activity share one process.
 */
object CommandLog {
    private const val MAX = 20

    class Entry(val at: Long, val source: String, val cmd: Command, val level: String?, val result: CommandResult)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(source: String, cmd: Command, level: String?, result: CommandResult) {
        entries.addFirst(Entry(System.currentTimeMillis(), source, cmd, level, result))
        while (entries.size > MAX) entries.removeLast()
    }

    /** Newest first. */
    @Synchronized
    fun recent(): List<Entry> = entries.toList()
}
