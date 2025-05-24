package component.access.polymorphism.service

import component.access.polymorphism.contract.PolymorphicAccess
import component.access.polymorphism.contract.PolymorphicAccess.RecordRequest
import component.access.polymorphism.contract.PolymorphicAccess.RecordResponse

class JavaEchoService : PolymorphicAccess {
    override fun echo(input: RecordRequest): RecordResponse = when (input) {
            is RecordRequest.Please -> RecordResponse.Yes(input.message);
            is RecordRequest.Thanks -> RecordResponse.No(input.message)
        }

}
