package ifx.stdlib.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val EUROPE_OSLO: ZoneId = ZoneId.of("Europe/Oslo") // Should be only ZoneId for Oslo in njord backend

fun Instant.inOsloTime(): ZonedDateTime = atZone(EUROPE_OSLO)
fun DateTimeFormatter.inOsloTime(): DateTimeFormatter = withZone(EUROPE_OSLO)
fun Clock.inOsloTime(): Clock = withZone(EUROPE_OSLO)
fun LocalDateTime.inOsloTime(): ZonedDateTime = atZone(EUROPE_OSLO)
fun ZonedDateTime.inOsloTime(): ZonedDateTime = withZoneSameInstant(EUROPE_OSLO)

inline fun <reified T> LocalDateTime.inOsloTime(): T = when (T::class) {
    ZonedDateTime::class -> this.atZone(EUROPE_OSLO) as T
    Instant::class -> this.atZone(EUROPE_OSLO).toInstant() as T
    else -> throw IllegalArgumentException("Unsupported type")
}

fun YearMonth.getFirstInstant(): Instant = ZonedDateTime.of(year, monthValue, 1, 0, 0, 0, 0, EUROPE_OSLO).toInstant()
fun YearMonth.getLastInstant(): Instant =
    ZonedDateTime.of(year, monthValue, lengthOfMonth(), 23, 59, 59, 999_999_999, EUROPE_OSLO).toInstant()
