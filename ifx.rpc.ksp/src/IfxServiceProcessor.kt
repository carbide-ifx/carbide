package ifx.rpc.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

class IfxServiceProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val service = resolver.getClassDeclarationByName(resolver.getKSNameFromString("ifx.service.IService"))
            ?: return emptyList()
        val contracts = resolver.getAllFiles()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE && it.qualifiedName?.asString() != service.qualifiedName?.asString() }
            .filter { service.asStarProjectedType().isAssignableFrom(it.asStarProjectedType()) }
            .toList()
        if (contracts.any { !it.validate(resolver) }) return contracts
        contracts.forEach(::generate)
        generated = true
        return emptyList()
    }

    private fun KSClassDeclaration.validate(resolver: Resolver): Boolean =
        getAllFunctions().filterNot(::isAnyMethod).all { function ->
            val returnDeclaration = function.returnType?.resolve()?.declaration?.qualifiedName?.asString()
            val isFlow = returnDeclaration == "kotlinx.coroutines.flow.Flow"
            val valid = function.typeParameters.isEmpty() && function.parameters.size <= 1 &&
                ((isFlow && !function.modifiers.contains(Modifier.SUSPEND)) || (!isFlow && function.modifiers.contains(Modifier.SUSPEND)))
            if (!valid) logger.error(
                "IFX service method ${function.simpleName.asString()} must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                function
            )
            valid
        }

    private fun generate(contract: KSClassDeclaration) {
        val packageName = contract.packageName.asString()
        val contractName = contract.simpleName.asString()
        val descriptorName = "${contractName}Descriptor"
        val functions = contract.getAllFunctions().filterNot(::isAnyMethod).toList()
        val address = contract.qualifiedName!!.asString()
        val packageDeclaration = packageName.takeIf { it.isNotBlank() }?.let { "package $it\n" }.orEmpty()
        val output = codeGenerator.createNewFile(
            Dependencies(false, contract.containingFile!!), packageName, descriptorName
        )
        OutputStreamWriter(output).use { writer ->
            writer.write("""$packageDeclaration

import ifx.protocol.contract.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

public object $descriptorName : ServiceDescriptor<$contractName> {
    override val contract = $contractName::class
    override val address = "$address"
    override fun createClient(binding: IBinding): $contractName = object : $contractName {
${functions.joinToString("\n") { clientMethod(it) }}
    }
    override fun bind(instance: $contractName): IBinding = object : IBinding {
        override suspend fun fireAndForget(operation: String, message: Message) {
            withContext(message.parseContext()) {
                when (operation) {
${functions.filter { it.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit" }.joinToString("\n") { serverUnitBranch(it) }}
                    else -> error("No fire-and-forget operation: ${'$'}operation")
                }
            }
        }
        override suspend fun requestResponse(operation: String, message: Message): Message = withContext(message.parseContext()) {
            when (operation) {
${functions.filter { it.returnType!!.resolve().declaration.qualifiedName?.asString() !in setOf("kotlin.Unit", "kotlinx.coroutines.flow.Flow") }.joinToString("\n") { serverResponseBranch(it) }}
                else -> error("No request-response operation: ${'$'}operation")
            }
        }
        override suspend fun requestStream(operation: String, message: Message): Flow<Message> = withContext(message.parseContext()) {
            when (operation) {
${functions.filter { it.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.flow.Flow" }.joinToString("\n") { serverStreamBranch(it) }}
                else -> error("No request-stream operation: ${'$'}operation")
            }
        }
    }
}
""")
        }
    }

    private fun clientMethod(function: KSFunctionDeclaration): String {
        val name = function.simpleName.asString()
        val parameter = function.parameters.singleOrNull()
        val declaration = function.returnType!!.resolve().declaration.qualifiedName!!.asString()
        val signature = signature(function)
        val params = parameter?.let { "${it.name!!.asString()}: ${typeName(it.type.resolve())}" } ?: ""
        val argument = parameter?.name?.asString()
        return when (declaration) {
            "kotlin.Unit" -> "        override suspend fun $name($params) { binding.fireAndForget(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()"}) }"
            "kotlinx.coroutines.flow.Flow" -> {
                val element = function.returnType!!.resolve().arguments.single().type!!.resolve()
                "        override fun $name($params): ${typeName(function.returnType!!.resolve())} = flow { emitAll(binding.requestStream(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()" }).map { it.decode<${typeName(element)}>() }) }"
            }
            else -> "        override suspend fun $name($params): ${typeName(function.returnType!!.resolve())} = binding.requestResponse(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()" }).decode()"
        }
    }

    private fun serverUnitBranch(function: KSFunctionDeclaration): String =
        "                    \"${signature(function)}\" -> instance.${call(function)}"

    private fun serverResponseBranch(function: KSFunctionDeclaration): String =
        "                    \"${signature(function)}\" -> instance.${call(function)}.encodeToMessage()"

    private fun serverStreamBranch(function: KSFunctionDeclaration): String {
        val element = function.returnType!!.resolve().arguments.single().type!!.resolve()
        return "                    \"${signature(function)}\" -> instance.${call(function)}.map { it.encodeToMessage() }"
    }

    private fun call(function: KSFunctionDeclaration): String = function.simpleName.asString() +
        function.parameters.singleOrNull()?.let { "(message.decode<${typeName(it.type.resolve())}>())" }.orEmpty().ifEmpty { "()" }

    private fun signature(function: KSFunctionDeclaration): String {
        val parameter = function.parameters.singleOrNull()?.type?.resolve()?.let(::typeName).orEmpty()
        return "${function.simpleName.asString()}($parameter)"
    }

    private fun isAnyMethod(function: KSFunctionDeclaration): Boolean =
        function.simpleName.asString() in setOf("equals", "hashCode", "toString")

    private fun typeName(type: KSType): String = type.declaration.qualifiedName?.asString()?.let { raw ->
        if (type.arguments.isEmpty()) raw + if (type.nullability.name == "NULLABLE") "?" else ""
        else raw + type.arguments.joinToString(prefix = "<", postfix = ">") { argument -> typeName(argument.type!!.resolve()) } + if (type.nullability.name == "NULLABLE") "?" else ""
    } ?: type.toString()
}
