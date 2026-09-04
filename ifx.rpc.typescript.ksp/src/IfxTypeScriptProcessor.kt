package ifx.rpc.typescript.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.validate
import ifx.rpc.schema.ksp.ModelException
import ifx.rpc.schema.ksp.ServiceModelBuilder
import java.io.OutputStreamWriter

internal class IfxTypeScriptProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val renderer = TypeScriptRenderer()
    private var generated = false

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val service = resolver.getClassDeclarationByName(resolver.getKSNameFromString("ifx.service.IService"))
            ?: return emptyList()
        val localContracts = resolver.getAllFiles()
            .flatMap { file -> file.declarations.flatMap(::classes) }
        val indexedContracts = resolver.getDeclarationsFromPackage(INDEX_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .flatMap(::indexedServiceNames)
            .mapNotNull { name ->
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(name))
            }
        val contracts = (localContracts + indexedContracts)
            .filter { it.classKind == ClassKind.INTERFACE && it.qualifiedName?.asString() !in FRAMEWORK_MARKERS }
            .filter { service.asStarProjectedType().isAssignableFrom(it.asStarProjectedType()) }
            .distinctBy { it.qualifiedName?.asString() }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()
        val invalid = contracts.filterNot { it.validate() }
        if (invalid.isNotEmpty()) return invalid
        contracts.forEach(::generate)
        generated = true
        return emptyList()
    }

    private fun indexedServiceNames(index: KSClassDeclaration): Sequence<String> =
        index.annotations
            .filter { annotation ->
                annotation.annotationType.resolve().declaration.qualifiedName?.asString() == INDEX_ANNOTATION
            }
            .flatMap { annotation ->
                annotation.arguments.asSequence().flatMap { argument ->
                    (argument.value as? List<*>)?.asSequence()?.filterIsInstance<String>() ?: emptySequence()
                }
            }

    private fun generate(contract: KSClassDeclaration) {
        val model = try {
            ServiceModelBuilder().build(contract)
        } catch (exception: ModelException) {
            logger.error(exception.message.orEmpty(), exception.node)
            return
        }
        val output = codeGenerator.createNewFile(
            contract.containingFile?.let { Dependencies(false, it) } ?: Dependencies.ALL_FILES,
            contract.packageName.asString(),
            contract.simpleName.asString(),
            "ts",
        )
        OutputStreamWriter(output).use { writer -> writer.write(renderer.render(model)) }
    }

    private fun classes(declaration: KSDeclaration): Sequence<KSClassDeclaration> = sequence {
        if (declaration is KSClassDeclaration) {
            yield(declaration)
            declaration.declarations.forEach { nested -> yieldAll(classes(nested)) }
        }
    }

    private companion object {
        const val INDEX_PACKAGE = "ifx.service.index"
        const val INDEX_ANNOTATION = "ifx.service.IfxServiceIndex"
        val FRAMEWORK_MARKERS = setOf("ifx.service.IService", "ifx.service.IUtility")
    }
}
