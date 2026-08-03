package ifx.rpc.typescript.ksp

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
import java.io.OutputStreamWriter

internal class IfxTypeScriptProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val renderer = TypeScriptRenderer()
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val service = resolver.getClassDeclarationByName(resolver.getKSNameFromString("ifx.service.IService"))
            ?: return emptyList()
        val contracts = resolver.getAllFiles()
            .flatMap { file -> file.declarations.flatMap(::classes) }
            .filter { it.classKind == ClassKind.INTERFACE && it.qualifiedName?.asString() != service.qualifiedName?.asString() }
            .filter { service.asStarProjectedType().isAssignableFrom(it.asStarProjectedType()) }
            .toList()
        val invalid = contracts.filterNot { it.validate() }
        if (invalid.isNotEmpty()) return invalid
        contracts.forEach(::generate)
        generated = true
        return emptyList()
    }

    private fun generate(contract: KSClassDeclaration) {
        val model = try {
            ServiceModelBuilder().build(contract)
        } catch (exception: ModelException) {
            logger.error(exception.message.orEmpty(), exception.node)
            return
        }
        val output = codeGenerator.createNewFile(
            Dependencies(false, contract.containingFile!!),
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
}
