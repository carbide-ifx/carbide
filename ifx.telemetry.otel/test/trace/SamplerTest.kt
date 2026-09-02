package ifx.telemetry.otel.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SamplerTest {
    @Test
    fun `always on and always off return their fixed decisions`() {
        assertEquals(SamplingDecision.RECORD_AND_SAMPLE, AlwaysOnSampler.shouldSample(context()))
        assertEquals(SamplingDecision.DROP, AlwaysOffSampler.shouldSample(context()))
    }

    @Test
    fun `parent based delegates root decisions`() {
        assertEquals(
            SamplingDecision.DROP,
            ParentBasedSampler(AlwaysOffSampler).shouldSample(context()),
        )
    }

    @Test
    fun `parent based preserves parent decisions`() {
        val sampledParent = parent(traceFlags = "01")
        val unsampledParent = parent(traceFlags = "00")

        assertEquals(
            SamplingDecision.RECORD_AND_SAMPLE,
            ParentBasedSampler(AlwaysOffSampler).shouldSample(context(parent = sampledParent)),
        )
        assertEquals(
            SamplingDecision.DROP,
            ParentBasedSampler(AlwaysOnSampler).shouldSample(context(parent = unsampledParent)),
        )
    }

    @Test
    fun `probability sampler decisions are deterministic and monotonic`() {
        val lowRandomness = context(traceId = "00000000000000000000000000000000")
        val highRandomness = context(traceId = "ffffffffffffffffffffffffffffffff")

        assertEquals(SamplingDecision.DROP, ProbabilitySampler(0.5).shouldSample(lowRandomness))
        assertEquals(
            SamplingDecision.RECORD_AND_SAMPLE,
            ProbabilitySampler(0.5).shouldSample(highRandomness),
        )
        assertEquals(SamplingDecision.DROP, ProbabilitySampler(0.0).shouldSample(highRandomness))
        assertEquals(
            SamplingDecision.RECORD_AND_SAMPLE,
            ProbabilitySampler(1.0).shouldSample(lowRandomness),
        )
    }

    @Test
    fun `probability sampler rejects invalid probabilities`() {
        assertFailsWith<IllegalArgumentException> { ProbabilitySampler(-0.1) }
        assertFailsWith<IllegalArgumentException> { ProbabilitySampler(1.1) }
        assertFailsWith<IllegalArgumentException> { ProbabilitySampler(Double.NaN) }
    }
}

private fun context(
    parent: SamplingParent? = null,
    traceId: String = "4bf92f3577b34da6a3ce929d0e0e4736",
) = SamplingContext(
    parent = parent,
    traceId = traceId,
    name = "test.Service/call()",
    kind = SpanKind.CLIENT,
    attributes = mapOf("rpc.system.name" to "ifx"),
)

private fun parent(traceFlags: String) = SamplingParent(
    traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
    spanId = "00f067aa0ba902b7",
    traceFlags = traceFlags,
    traceState = null,
    isRemote = true,
)
