package ifx.logging

import ifx.service.IService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test


class LoggingTests {
    @Test
    fun `from Service`() {
        SomeManager().anOperation()
    }

    @Test
    fun `from code`() {
        Log.info { "Hello" }
        KotlinLogging.logger { }.info { "Hello" }
    }
}

class SomeManager : IService {
    fun anOperation() {
        val e = Exception("A test error")
        Log(this).info { "Performing an operation" }
//        Log(this).warn(e) { "This test is logging an exception:" }
    }
}
