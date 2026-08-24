package ifx.stdlib

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class TimeSpanTest {
    private val a = Instant.parse("2000-01-01T00:00:00Z")
    private val b = Instant.parse("2000-01-01T06:00:00Z")
    private val c = Instant.parse("2000-01-01T12:00:00Z")
    private val d = Instant.parse("2000-01-01T18:00:00Z")

    @Test
    fun constructionAndProperties() {
        assertEquals(TimeSpan(a, b), TimeSpan(a to b))
        assertEquals(TimeSpan(a, b), a until b)
        assertIs<TimeSpan.Closed>(a until b)
        assertTrue((a until null).isOpenEnded())
        assertFalse((a until b).isOpenEnded())
        assertTrue((a until a).isEmpty())
        assertEquals(Duration.INFINITE, (a until null).duration)
        assertEquals(Duration.ZERO, (a until a).duration)
        assertEquals(Duration.parse("6h"), (a until b).duration)

        val minimum = Instant.fromEpochSeconds(Long.MIN_VALUE)
        val maximum = Instant.fromEpochSeconds(Long.MAX_VALUE)
        assertEquals(Duration.INFINITE, (minimum until maximum).duration)
    }

    @Test
    fun instantContainmentIsHalfOpen() {
        val span = a until c

        assertTrue(a in span)
        assertTrue(b in span)
        assertFalse(c in span)
        assertTrue(span.containsInclusive(c))
        assertFalse((a until a).contains(a))
    }

    @Test
    fun spanContainmentHandlesClosedAndOpenEnds() {
        assertTrue((b until c) in (a until d))
        assertTrue((b until null) in (a until null))
        assertFalse((b until null) in (a until d))
        assertFalse((a until b) in (b until null))
    }

    @Test
    fun intersectionDistinguishesOverlapFromTouching() {
        val first = a until b
        val adjacent = b until c
        val overlapping = a until c

        assertFalse(first.intersects(adjacent))
        assertTrue(first.intersectsInclusive(adjacent))
        assertTrue(first.intersects(overlapping))
        assertTrue((a until null).intersects(b until null))
    }

    @Test
    fun copyPreservesOrChangesTheEnd() {
        val closed = a until c

        assertEquals(b until c, closed.copy(from = b))
        assertEquals(b, closed.copy(from = b).from)
        assertIs<TimeSpan.Closed>(closed.copy())
        assertTrue(closed.copy(to = null).isOpenEnded())
    }

    @Test
    fun jsonShapeMatchesNjordTimeSpan() {
        val closed = a until b
        val open = a until null

        assertEquals("""{"from":"$a","to":"$b"}""", Json.encodeToString(closed))
        assertEquals("""{"from":"$a","to":"$b"}""", Json.encodeToString<TimeSpan>(closed))
        assertEquals("""{"from":"$a","to":null}""", Json.encodeToString(open))
        assertEquals(closed, Json.decodeFromString<TimeSpan.Closed>("""{"to":"$b","from":"$a"}"""))
        assertEquals(open, Json.decodeFromString<TimeSpan>("""{"from":"$a","to":null}"""))
        assertFailsWith<SerializationException> {
            Json.decodeFromString<TimeSpan.Closed>("""{"from":"$a","to":null}""")
        }
    }

    @Test
    fun stringRepresentationIsReadable() {
        assertEquals("TimeSpan($a..$b)", (a until b).toString())
        assertEquals("TimeSpan($a..infinity)", (a until null).toString())
    }
}
