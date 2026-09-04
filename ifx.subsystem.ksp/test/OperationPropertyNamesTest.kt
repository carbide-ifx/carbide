package ifx.subsystem.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class OperationPropertyNamesTest {
    @Test
    fun `allocates stable descriptor members around reserved names`() {
        assertEquals(
            listOf("lookup", "addressOperation"),
            operationPropertyNames(
                listOf("lookup", "address"),
                setOf("address"),
            ),
        )
    }
}
