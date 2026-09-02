package ifx.telemetry.otel.internal

import kotlin.time.Clock

internal fun unixTimeNanos(): Long {
    val now = Clock.System.now()
    return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
}
