package ifx.telemetry.otel.trace

import ifx.logging.LogCorrelation
import ifx.telemetry.otel.TelemetryResource
import ifx.telemetry.otel.internal.ActiveSpan
import ifx.telemetry.otel.internal.isSampled
import ifx.telemetry.otel.internal.newSpanId
import ifx.telemetry.otel.internal.newTraceId
import ifx.telemetry.otel.internal.unixTimeNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import kotlin.time.TimeMark

data class SpanContext(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
    val traceState: String? = null,
) {
    val traceParent: String get() = "00-$traceId-$spanId-$traceFlags"
}

class Span internal constructor(
    val context: SpanContext,
    initialAttributes: Map<String, String>,
) {
    private val attributesMutex = Mutex()
    private val attributes = initialAttributes.toMutableMap()

    suspend fun setAttribute(name: String, value: String) {
        attributesMutex.lock()
        try {
            attributes[name] = value
        } finally {
            attributesMutex.unlock()
        }
    }

    suspend fun setAttribute(name: String, value: Number) = setAttribute(name, value.toString())

    suspend fun setAttribute(name: String, value: Boolean) = setAttribute(name, value.toString())

    internal suspend fun attributes(): Map<String, String> {
        attributesMutex.lock()
        try {
            return attributes.toMap()
        } finally {
            attributesMutex.unlock()
        }
    }
}

class Tracer(
    private val spanProcessor: SpanProcessor,
    private val resource: TelemetryResource,
    private val sampler: Sampler = ParentBasedSampler(),
    private val onObservabilityFailure: suspend (Throwable) -> Unit = {},
) {
    suspend fun <T> span(
        name: String,
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, String> = emptyMap(),
        block: suspend Span.() -> T,
    ): T = span(name, kind, attributes, parent = currentCoroutineContext()[ActiveSpan], block)

    internal suspend fun <T> span(
        name: String,
        kind: SpanKind,
        attributes: Map<String, String>,
        parent: ActiveSpan?,
        block: suspend Span.() -> T,
    ): T {
        val started = start(name, kind, attributes, parent)
        var failure: Throwable? = null
        try {
            return withContext(started.coroutineContext) { started.span.block() }
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            finish(started, failure)
        }
    }

    internal fun <T> spanFlow(
        name: String,
        kind: SpanKind,
        attributes: Map<String, String>,
        parent: ActiveSpan? = null,
        inheritCurrentParent: Boolean = false,
        flow: Span.() -> Flow<T>,
    ): Flow<T> = flow {
        val resolvedParent = if (inheritCurrentParent) currentCoroutineContext()[ActiveSpan] else parent
        val started = start(name, kind, attributes, resolvedParent)
        var failure: Throwable? = null
        try {
            started.span.flow()
                .flowOn(started.coroutineContext)
                .collect { emit(it) }
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            finish(started, failure)
        }
    }

    suspend fun flush() = spanProcessor.flush()

    suspend fun shutdown() = spanProcessor.shutdown()

    internal suspend fun reportFailure(failure: Throwable) {
        try {
            onObservabilityFailure(failure)
        } catch (_: Throwable) {
            // Observability must not alter the outcome of instrumented application code.
        }
    }

    private suspend fun start(
        name: String,
        kind: SpanKind,
        attributes: Map<String, String>,
        parent: ActiveSpan?,
    ): StartedSpan {
        require(name.isNotBlank()) { "span name must not be blank" }
        val traceId = parent?.traceId ?: newTraceId()
        val samplingContext = SamplingContext(
            parent = parent?.toSamplingParent(),
            traceId = traceId,
            name = name,
            kind = kind,
            attributes = attributes,
        )
        val decision = try {
            sampler.shouldSample(samplingContext)
        } catch (failure: Throwable) {
            reportFailure(failure)
            samplingContext.parent.inheritedDecision()
        }
        val activeSpan = ActiveSpan(
            traceId = traceId,
            spanId = newSpanId(),
            traceFlags = parent?.traceFlags.withSamplingDecision(decision),
            traceState = parent?.traceState,
        )
        val context = SpanContext(
            traceId = activeSpan.traceId,
            spanId = activeSpan.spanId,
            traceFlags = activeSpan.traceFlags,
            traceState = activeSpan.traceState,
        )
        return StartedSpan(
            span = Span(context, attributes),
            activeSpan = activeSpan,
            parent = parent,
            name = name,
            kind = kind,
            startTimeUnixNano = unixTimeNanos(),
            startMark = TimeSource.Monotonic.markNow(),
            coroutineContext = activeSpan + LogCorrelation(
                traceId = context.traceId,
                spanId = context.spanId,
                traceFlags = context.traceFlags,
            ),
        )
    }

    private suspend fun finish(started: StartedSpan, failure: Throwable?) {
        if (!started.activeSpan.traceFlags.isSampled()) return
        val errorType = failure?.errorType()
        val span = FinishedSpan(
            resource = resource,
            traceId = started.activeSpan.traceId,
            spanId = started.activeSpan.spanId,
            parentSpanId = started.parent?.spanId,
            traceFlags = started.activeSpan.traceFlags,
            traceState = started.activeSpan.traceState,
            name = started.name,
            kind = started.kind,
            startTimeUnixNano = started.startTimeUnixNano,
            endTimeUnixNano = started.startTimeUnixNano + started.startMark.elapsedNow().inWholeNanoseconds,
            attributes = buildMap {
                putAll(started.span.attributes())
                errorType?.let { put("error.type", it) }
            },
            error = failure?.let {
                SpanError(
                    type = requireNotNull(errorType),
                    message = it.message,
                    stackTrace = it.stackTraceToString(),
                )
            },
        )
        try {
            spanProcessor.onEnd(span)
        } catch (processorFailure: Throwable) {
            reportFailure(processorFailure)
        }
    }
}

fun <T> Flow<T>.inSpan(
    tracer: Tracer,
    name: String,
    kind: SpanKind = SpanKind.INTERNAL,
    attributes: Map<String, String> = emptyMap(),
): Flow<T> = tracer.spanFlow(
    name = name,
    kind = kind,
    attributes = attributes,
    inheritCurrentParent = true,
) { this@inSpan }

private data class StartedSpan(
    val span: Span,
    val activeSpan: ActiveSpan,
    val parent: ActiveSpan?,
    val name: String,
    val kind: SpanKind,
    val startTimeUnixNano: Long,
    val startMark: TimeMark,
    val coroutineContext: kotlin.coroutines.CoroutineContext,
)

private fun ActiveSpan.toSamplingParent(): SamplingParent = SamplingParent(
    traceId = traceId,
    spanId = spanId,
    traceFlags = traceFlags,
    traceState = traceState,
    isRemote = isRemote,
)

private fun SamplingParent?.inheritedDecision(): SamplingDecision = when {
    this == null -> SamplingDecision.DROP
    isSampled -> SamplingDecision.RECORD_AND_SAMPLE
    else -> SamplingDecision.DROP
}

private fun String?.withSamplingDecision(decision: SamplingDecision): String {
    val inheritedFlags = this?.toIntOrNull(16) ?: 0
    val flags = when (decision) {
        SamplingDecision.DROP -> inheritedFlags and 0xfe
        SamplingDecision.RECORD_AND_SAMPLE -> inheritedFlags or 0x01
    }
    return flags.toString(16).padStart(2, '0')
}

private fun Throwable.errorType(): String = this::class.qualifiedName ?: this::class.simpleName ?: "Throwable"
