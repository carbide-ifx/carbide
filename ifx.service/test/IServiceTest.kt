package ifx.service

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class IServiceTest {
    @Test
    fun `logger is stable for an implementation class`() {
        val first = FirstService()
        val second = FirstService()

        assertSame(first.log, first.log)
        assertSame(first.log, second.log)
        assertNotSame(first.log, SecondService().log)
    }

    private class FirstService : IService
    private class SecondService : IService
}
