package ifx.rpc.fixture

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GeneratedDescriptorTest {
    private val service = object : IFixtureService {
        override suspend fun echo(value: String): String = "echo:$value"
        override fun values(): Flow<Int> = flowOf(1, 2, 3)
    }

    private val client = IFixtureServiceDescriptor.createClient(IFixtureServiceDescriptor.bind(service))

    @Test
    fun `generated descriptor dispatches unary and stream calls`() = runTest {
        client.echo("hello") shouldBe "echo:hello"
        client.values().toList() shouldBe listOf(1, 2, 3)
    }
}
