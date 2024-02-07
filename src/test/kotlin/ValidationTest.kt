package arve.test

import arve.host.Host
import arve.service.ServiceBase
import arve.test.perf.Echo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec

//class ValidationTest : StringSpec({
//    "Cannot add instance that does not implement ServiceBase" {
//        val host = Host()
//        val service = object {}
//        shouldThrow<IllegalArgumentException> {
//            host.addService(service, service::class)
//        }
//    }
//
//    "Cannot add instance that does not implement Contract" {
//        val host = Host()
//        class NotEchoService: ServiceBase{}
//        shouldThrow<IllegalArgumentException> {
//            host.addService<Echo>(NotEchoService())
//        }
//    }
//})
