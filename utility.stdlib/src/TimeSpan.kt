package ifx.stdlib

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A half-open span between two instants: [from] is included and [to] is excluded.
 * A `null` [to] represents an open-ended span.
 *
 * Operations assume that [to] is not earlier than [from]. This is not enforced so
 * existing data can still be represented and inspected.
 */
@Serializable
open class TimeSpan(
    open val from: Instant,
    open val to: Instant?,
) {
    constructor(pair: Pair<Instant, Instant>) : this(pair.first, pair.second)

    /** A [TimeSpan] whose end is known at compile time to be non-null. */
    @Serializable(with = ClosedTimeSpanSerializer::class)
    class Closed(
        override val from: Instant,
        override val to: Instant,
    ) : TimeSpan(from, to) {
        /** Includes the normally exclusive end instant in the containment check. */
        fun containsInclusive(instant: Instant): Boolean = instant in this || instant == to
    }

    fun isOpenEnded(): Boolean = to == null

    fun isEmpty(): Boolean = from == to

    /**
     * The duration of this span, to millisecond precision, or [Duration.INFINITE]
     * when this span is open-ended or its duration exceeds [Duration]'s finite range.
     */
    val duration: Duration
        get() {
            val end = to ?: return Duration.INFINITE
            val difference = end - from
            return if (difference.isInfinite()) difference else difference.inWholeMilliseconds.milliseconds
        }

    /**
     * Returns whether this span and [other] overlap. Merely adjacent spans do not overlap.
     */
    fun intersects(other: TimeSpan): Boolean {
        val thisEnd = to
        val otherEnd = other.to
        if (thisEnd == null) return otherEnd == null || from < otherEnd
        if (otherEnd == null) return other.from < thisEnd
        return from < otherEnd && other.from < thisEnd
    }

    /**
     * Returns whether this span and [other] overlap or touch at an end point.
     */
    fun intersectsInclusive(other: TimeSpan): Boolean {
        val thisEnd = to
        val otherEnd = other.to
        if (thisEnd == null) return otherEnd == null || from <= otherEnd
        if (otherEnd == null) return other.from <= thisEnd
        return from <= otherEnd && other.from <= thisEnd
    }

    /** Returns whether [other] is entirely contained in this span. */
    operator fun contains(other: TimeSpan): Boolean {
        val thisEnd = to
        val otherEnd = other.to
        return other.from in this && (thisEnd == null || otherEnd != null && thisEnd >= otherEnd)
    }

    /** Returns whether [instant] is at or after [from] and before [to], when present. */
    operator fun contains(instant: Instant): Boolean {
        val end = to
        return instant >= from && (end == null || instant < end)
    }

    fun copy(from: Instant = this.from, to: Instant? = this.to): TimeSpan =
        if (to == null) TimeSpan(from, null) else Closed(from, to)

    final override fun equals(other: Any?): Boolean =
        other is TimeSpan && from == other.from && to == other.to

    final override fun hashCode(): Int = 31 * from.hashCode() + (to?.hashCode() ?: 0)

    final override fun toString(): String = "TimeSpan($from..${to ?: "infinity"})"
}

/** Creates a closed-ended, half-open [TimeSpan]. */
infix fun Instant.until(to: Instant): TimeSpan.Closed = TimeSpan.Closed(this, to)

/** Creates a half-open [TimeSpan], which is open-ended when [to] is `null`. */
infix fun Instant.until(to: Instant?): TimeSpan = TimeSpan(this, to)

object ClosedTimeSpanSerializer : KSerializer<TimeSpan.Closed> {
    private val instantSerializer = serializer<Instant>()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ifx.stdlib.TimeSpan.Closed") {
        element<Instant>("from")
        element<Instant>("to")
    }

    override fun serialize(encoder: Encoder, value: TimeSpan.Closed) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, instantSerializer, value.from)
            encodeSerializableElement(descriptor, 1, instantSerializer, value.to)
        }
    }

    override fun deserialize(decoder: Decoder): TimeSpan.Closed = decoder.decodeStructure(descriptor) {
        var from: Instant? = null
        var to: Instant? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> from = decodeSerializableElement(descriptor, index, instantSerializer)
                1 -> to = decodeSerializableElement(descriptor, index, instantSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected index: $index")
            }
        }

        TimeSpan.Closed(
            requireNotNull(from) { "from must not be null" },
            requireNotNull(to) { "to must not be null" },
        )
    }
}
