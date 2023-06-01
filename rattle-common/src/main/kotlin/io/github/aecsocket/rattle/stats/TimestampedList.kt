package io.github.aecsocket.rattle.stats

interface TimestampedList<T> {
    val buffer: Long

    val size: Int

    fun getLast(ms: Long): List<T>
}

interface MutableTimestampedList<T> : TimestampedList<T> {
    override var buffer: Long

    fun add(value: T)

    fun clear()

    operator fun plusAssign(value: T) = add(value)
}

class TimestampedListImpl<T> internal constructor(override var buffer: Long) : MutableTimestampedList<T> {
    private data class Entry<T>(
        val at: Long,
        val value: T,
    )

    private val entries = ArrayList<Entry<T>>()

    override val size: Int
        get() = entries.size

    override fun getLast(ms: Long): List<T> {
        val result = ArrayList<T>()
        val threshold = System.currentTimeMillis() - ms
        var idx = entries.size - 1
        while (idx >= 0 && entries[idx].at > threshold) {
            result.add(0, entries[idx].value)
            idx -= 1
        }
        return result
    }

    override fun add(value: T) {
        val now = System.currentTimeMillis()
        entries += Entry(now, value)

        val threshold = now - buffer
        while (entries.isNotEmpty() && entries[0].at < threshold) {
            entries.removeAt(0)
        }
    }

    override fun clear() {
        entries.clear()
    }
}

fun <T> timestampedList(buffer: Long): MutableTimestampedList<T> =
    TimestampedListImpl(buffer)
