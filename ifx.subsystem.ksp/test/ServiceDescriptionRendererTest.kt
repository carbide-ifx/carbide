package ifx.subsystem.ksp

import ifx.rpc.schema.ksp.Interaction
import ifx.rpc.schema.ksp.OperationModel
import ifx.rpc.schema.ksp.PropertyModel
import ifx.rpc.schema.ksp.ServiceKind
import ifx.rpc.schema.ksp.ServiceModel
import ifx.rpc.schema.ksp.TypeDeclaration
import ifx.rpc.schema.ksp.TypeRef
import kotlin.test.Test
import kotlin.test.assertContains

class ServiceDescriptionRendererTest {
    @Test
    fun `renders the canonical compiler schema into runtime service metadata`() {
        val schema = ServiceModel(
            name = "CatalogService",
            address = "example.CatalogService",
            kind = ServiceKind.SERVICE,
            operations = listOf(
                OperationModel(
                    name = "find",
                    route = "find(example.Criteria)",
                    parameterName = "criteria",
                    request = TypeRef.Named("example.Criteria"),
                    response = TypeRef.RecordType(TypeRef.StringType),
                    interaction = Interaction.REQUEST_RESPONSE,
                ),
            ),
            declarations = listOf(
                TypeDeclaration.ObjectType(
                    qualifiedName = "example.Criteria",
                    typeParameters = emptyList(),
                    properties = listOf(
                        PropertyModel("query", TypeRef.Nullable(TypeRef.StringType), optional = true),
                    ),
                ),
            ),
        )

        val source = ServiceDescriptionRenderer().render(schema)

        assertContains(source, "address = \"example.CatalogService\"")
        assertContains(source, "request = TypeReference.Named(\"example.Criteria\", listOf())")
        assertContains(source, "response = TypeReference.RecordType(TypeReference.StringType)")
        assertContains(source, "PropertyDescription(name = \"query\", type = TypeReference.Nullable(TypeReference.StringType), optional = true)")
    }
}
