package ifx.host.service

import ifx.host.contract.CustomException

object MyClient{
    fun throwException(): Boolean {
        doTheThrow()
        return true
    }

    fun doTheThrow(): Unit = throw CustomException("This is an Error from MyClient")
}
