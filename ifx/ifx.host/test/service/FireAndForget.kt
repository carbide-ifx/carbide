package ifx.host.service

import ifx.host.contract.CustomException
import ifx.host.contract.IFireAndForget

class FireAndForget : IFireAndForget {
    var fireAndForgetCalled = false
    var blockingFireAndForgetCalled = false
    var fireAndForgetParamCalled = ""
    var blockingFireAndForgetParamCalled = ""

    override suspend fun fireAndForget() {
        fireAndForgetCalled = true
    }

    override fun blockingFireAndForget() { blockingFireAndForgetCalled = true }

    override suspend fun fireAndForgetParam(a: String) { fireAndForgetParamCalled = a }

    override fun blockingFireAndForgetParam(a: String) { blockingFireAndForgetParamCalled = a }

    override suspend fun fireAndForgetWithException() = throw CustomException("Error in IFireAndForget")

    override fun blockingFireAndForgetWithException() = throw CustomException("Error in IFireAndForget")
}


