package arve.test

import arve.host.Host
import arve.ifx.ProxyFactory
import arve.ifx.WrappedTestException
import component.access.exception.contract.IExceptionAccess
import component.access.exception.contract.IntRequest
import component.access.exception.contract.StringResult
import component.access.exception.service.ExceptionAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SuspendTest : StringSpec({
    val port = Host.randomFreePort()
    val proxyFactory = ProxyFactory(port)
    beforeSpec {
        Host(port)
            .addService<IExceptionAccess>(ExceptionAccess())
            .start()
    }
    "Blocking" {
        val client = proxyFactory.create<IExceptionAccess>()
        client.test(IntRequest(1)) shouldBe StringResult("test result")
    }

    "Blocking exception" {
        val client = proxyFactory.create<IExceptionAccess>()
        shouldThrow<WrappedTestException> { client.testException(1) }
    }
    "Suspend" {
        val client = proxyFactory.create<IExceptionAccess>()
        runBlocking {
            client.testSuspend(1) shouldBe StringResult("testSuspend result")
        }
    }
    "Suspend exception" {
        val client = proxyFactory.create<IExceptionAccess>()
        shouldThrow<WrappedTestException> { client.testExceptionSuspend(1) }
    }


})
