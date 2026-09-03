package ifx.telemetry.otel.trace

data class SamplingContext(
    val parent: SamplingParent?,
    val traceId: String,
    val name: String,
    val kind: SpanKind,
    val attributes: Map<String, String>,
    val links: List<SpanLink> = emptyList(),
)

data class SamplingParent(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
    val traceState: String?,
    val isRemote: Boolean,
) {
    val isSampled: Boolean
        get() = traceFlags.toIntOrNull(16)?.and(1) == 1
}

enum class SamplingDecision {
    DROP,
    RECORD_AND_SAMPLE,
}

fun interface Sampler {
    fun shouldSample(context: SamplingContext): SamplingDecision
}

object AlwaysOnSampler : Sampler {
    override fun shouldSample(context: SamplingContext): SamplingDecision =
        SamplingDecision.RECORD_AND_SAMPLE
}

object AlwaysOffSampler : Sampler {
    override fun shouldSample(context: SamplingContext): SamplingDecision = SamplingDecision.DROP
}

/** Preserves an upstream sampling decision and delegates only root traces to [root]. */
class ParentBasedSampler(
    private val root: Sampler = AlwaysOnSampler,
) : Sampler {
    override fun shouldSample(context: SamplingContext): SamplingDecision = when {
        context.parent == null -> root.shouldSample(context)
        context.parent.isSampled -> SamplingDecision.RECORD_AND_SAMPLE
        else -> SamplingDecision.DROP
    }
}

/**
 * Deterministic head sampling based on the 56 least-significant bits of the trace ID.
 * The same trace ID therefore receives the same decision at a given probability.
 */
class ProbabilitySampler(
    val probability: Double,
) : Sampler {
    init {
        require(probability.isFinite() && probability in 0.0..1.0) {
            "probability must be finite and between 0.0 and 1.0"
        }
    }

    override fun shouldSample(context: SamplingContext): SamplingDecision {
        if (probability == 0.0) return SamplingDecision.DROP
        if (probability == 1.0) return SamplingDecision.RECORD_AND_SAMPLE

        val randomness = context.traceId.takeLast(14).toULong(radix = 16).toDouble()
        val rejectionThreshold = (1.0 - probability) * TWO_TO_THE_56
        return if (randomness >= rejectionThreshold) {
            SamplingDecision.RECORD_AND_SAMPLE
        } else {
            SamplingDecision.DROP
        }
    }
}

private const val TWO_TO_THE_56 = 72_057_594_037_927_936.0
