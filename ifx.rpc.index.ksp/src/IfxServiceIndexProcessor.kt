package ifx.rpc.index.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.OutputStreamWriter

class IfxServiceIndexProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private var generated = false

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val service = resolver.getClassDeclarationByName(resolver.getKSNameFromString("ifx.service.IService"))
            ?: return emptyList()
        val contracts = resolver.getAllFiles()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration ->
                declaration.classKind == ClassKind.INTERFACE &&
                    declaration.qualifiedName?.asString() !in FRAMEWORK_MARKERS &&
                    service.asStarProjectedType().isAssignableFrom(declaration.asStarProjectedType())
            }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()

        if (contracts.isNotEmpty()) {
            val moduleId = resolver.getModuleName().asString().toIdentifier()
            val indexName = "IfxServiceIndex$moduleId"
            val dependencies = Dependencies(
                aggregating = true,
                *contracts.mapNotNull(KSClassDeclaration::containingFile).toTypedArray(),
            )
            val output = codeGenerator.createNewFile(dependencies, INDEX_PACKAGE, indexName)
            OutputStreamWriter(output).use { writer ->
                val services = contracts.joinToString(",\n") { contract ->
                    "    \"${contract.qualifiedName!!.asString()}\""
                }
                writer.write(
                    """package $INDEX_PACKAGE

import ifx.service.IfxServiceIndex

@IfxServiceIndex(
$services,
)
public object $indexName
""",
                )
            }
        }

        generated = true
        return emptyList()
    }

    private fun String.toIdentifier(): String {
        val identifier = split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotEmpty)
            .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
            .ifEmpty { "Module" }
        return if (identifier.first().isLetter()) identifier else "Module$identifier"
    }

    private companion object {
        const val INDEX_PACKAGE = "ifx.service.index"
        val FRAMEWORK_MARKERS = setOf("ifx.service.IService", "ifx.service.IUtility")
    }
}
