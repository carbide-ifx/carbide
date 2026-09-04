package ifx.rpc.schema.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceMethodValidationTest {
    @Test
    fun `reports stable diagnostics for invalid rpc method shapes`() {
        val diagnostics = validateServiceMethods(
            listOf(
                validMethod("valid"),
                validMethod("tooManyArguments").copy(parameterCount = 2),
                validMethod("generic").copy(hasTypeParameters = true),
                validMethod("blocking").copy(isSuspending = false),
                validMethod("suspendingStream").copy(returnsFlow = true),
                validMethod("invalidNotification").copy(returnsUnit = false, isFireAndForget = true),
                validMethod("lookup"),
                validMethod("lookup"),
            ),
        )

        assertEquals(
            listOf(
                "IFX service method tooManyArguments must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                "IFX service method generic must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                "IFX service method blocking must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                "IFX service method suspendingStream must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                "@FireAndForget may only be used on suspending IFX service methods returning Unit.",
                "IFX service method overloads are not supported: lookup. Use distinct operation names.",
            ),
            diagnostics.map(ServiceMethodDiagnostic::message),
        )
    }

    private fun validMethod(name: String) = ServiceMethodShape(
        name = name,
        parameterCount = 1,
        hasTypeParameters = false,
        isSuspending = true,
        returnsFlow = false,
        returnsUnit = true,
        isFireAndForget = false,
    )
}
