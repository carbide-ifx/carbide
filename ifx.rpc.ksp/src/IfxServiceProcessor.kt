package ifx.rpc.ksp

import com.google.devtools.ksp.KspExperimental
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

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val service = resolver.getClassDeclarationByName(resolver.getKSNameFromString("ifx.service.IService"))
            ?: return emptyList()
        val sourceFiles = resolver.getAllFiles().toList()
        val generatedDependencies = Dependencies(aggregating = true, *sourceFiles.toTypedArray())
        val localContracts = sourceFiles.asSequence()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<KSClassDeclaration>()
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
        if (contracts.any { !it.validate(resolver) }) return contracts
        val moduleName = resolver.getModuleName().asString()
        contracts
            .filter { contract ->
                resolver.getClassDeclarationByName(
                    resolver.getKSNameFromString(descriptorQualifiedName(contract)),
                ) == null
            }
            .forEach { contract -> generate(contract, generatedDependencies) }
        generateRegistry(moduleName, contracts, generatedDependencies)
        if (resolver.hasSubsystemHostSupport()) {
            generateSubsystemHostConveniences(moduleName, generatedDependencies)
        }
        generated = true
        return emptyList()
    }

    private fun Resolver.hasSubsystemHostSupport(): Boolean =
        getClassDeclarationByName(getKSNameFromString(SUBSYSTEM_HOST_SUPPORT)) != null

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

    private fun KSClassDeclaration.validate(resolver: Resolver): Boolean =
        getAllFunctions().filterNot(::isAnyMethod).all { function ->
            val returnDeclaration = function.returnType?.resolve()?.declaration?.qualifiedName?.asString()
            val isFlow = returnDeclaration == "kotlinx.coroutines.flow.Flow"
            val validShape = function.typeParameters.isEmpty() && function.parameters.size <= 1 &&
                ((isFlow && !function.modifiers.contains(Modifier.SUSPEND)) || (!isFlow && function.modifiers.contains(Modifier.SUSPEND)))
            if (!validShape) logger.error(
                "IFX service method ${function.simpleName.asString()} must be a suspend unary/Unit method or a non-suspending Flow method, with at most one parameter and no type parameters.",
                function
            )
            val validFireAndForget = !function.isFireAndForget() ||
                (returnDeclaration == "kotlin.Unit" && function.modifiers.contains(Modifier.SUSPEND))
            if (!validFireAndForget) logger.error(
                "@FireAndForget may only be used on suspending IFX service methods returning Unit.",
                function,
            )
            validShape && validFireAndForget
        }

    private fun generate(contract: KSClassDeclaration, dependencies: Dependencies) {
        val packageName = contract.packageName.asString()
        val contractName = contract.simpleName.asString()
        val descriptorName = "${contractName}Descriptor"
        val functions = contract.getAllFunctions().filterNot(::isAnyMethod).toList()
        val address = contract.qualifiedName!!.asString()
        val packageDeclaration = packageName.takeIf { it.isNotBlank() }?.let { "package $it\n" }.orEmpty()
        val description = ServiceDescriptionRenderer().render(contract)
        val output = codeGenerator.createNewFile(
            dependencies,
            packageName,
            descriptorName,
        )
        OutputStreamWriter(output).use { writer ->
            writer.write("""$packageDeclaration

import ifx.protocol.contract.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

private class ${contractName}Proxy(
    private val binding: IBinding,
) : $contractName {
    override val logger = ifx.logging.Log("${address}Proxy")
${functions.joinToString("\n") { clientMethod(it) }}
}

public object $descriptorName : ServiceDescriptor<$contractName> {
    override val contract = $contractName::class
    override val address = "$address"
    override val description = $description
    override fun createClient(binding: IBinding): $contractName = ${contractName}Proxy(binding)
    override fun bind(instance: $contractName): IBinding = object : IBinding {
        override suspend fun fireAndForget(operation: String, message: Message) {
            when (operation) {
${functions.filter { it.isFireAndForget() }.joinToString("\n") { serverUnitBranch(it) }}
                else -> error("No fire-and-forget operation: ${'$'}operation")
            }
        }
        override suspend fun requestResponse(operation: String, message: Message): Message = when (operation) {
${functions.filter { it.returnType!!.resolve().declaration.qualifiedName?.asString() != "kotlinx.coroutines.flow.Flow" && !it.isFireAndForget() }.joinToString("\n") { serverResponseBranch(it) }}
            else -> error("No request-response operation: ${'$'}operation")
        }
        override suspend fun requestStream(operation: String, message: Message): Flow<Message> = when (operation) {
${functions.filter { it.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.flow.Flow" }.joinToString("\n") { serverStreamBranch(it) }}
            else -> error("No request-stream operation: ${'$'}operation")
        }
    }
}
""")
        }
    }

    private fun generateRegistry(
        moduleName: String,
        contracts: List<KSClassDeclaration>,
        dependencies: Dependencies,
    ) {
        val registryName = "${moduleName.toIdentifier()}ServiceDescriptors"
        val output = codeGenerator.createNewFile(
            dependencies,
            GENERATED_PACKAGE,
            registryName,
        )
        OutputStreamWriter(output).use { writer ->
            val branches = contracts.joinToString("\n") { contract ->
                val contractName = contract.qualifiedName!!.asString()
                "            $contractName::class -> ${descriptorQualifiedName(contract)}"
            }
            writer.write(
                """package $GENERATED_PACKAGE

import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.service.IService
import kotlin.reflect.KClass

public object $registryName : ServiceDescriptorRegistry {
    @Suppress("UNCHECKED_CAST")
    override fun <T : IService> find(contract: KClass<T>): ServiceDescriptor<T>? =
        when (contract) {
$branches
            else -> null
        } as ServiceDescriptor<T>?
}
""",
            )
        }
    }

    private fun generateSubsystemHostConveniences(
        moduleName: String,
        dependencies: Dependencies,
    ) {
        val moduleId = moduleName.toIdentifier()
        val registryName = "${moduleId}ServiceDescriptors"
        val output = codeGenerator.createNewFile(
            dependencies,
            SUBSYSTEM_PACKAGE,
            "${moduleId}SubsystemHost",
        )
        OutputStreamWriter(output).use { writer ->
            writer.write(
                """package $SUBSYSTEM_PACKAGE

import ifx.generated.$registryName
import ifx.host.Host
import ifx.host.HostBuilder

/** Creates a host using the services reachable from this subsystem module. */
public fun Host.Companion.subsystem(
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit,
): Host = Host(
    name = name,
    serviceDescriptors = $registryName,
    configure = configure,
)

/** Creates the default RSocket host using the services reachable from this subsystem module. */
public fun Host.Companion.default(
    port: Int = 0,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit = {},
): Host = default(
    serviceDescriptors = $registryName,
    port = port,
    name = name,
    configure = configure,
)
""",
            )
        }
    }

    private fun descriptorQualifiedName(contract: KSClassDeclaration): String =
        contract.packageName.asString().takeIf(String::isNotBlank)?.let { "$it." }.orEmpty() +
            "${contract.simpleName.asString()}Descriptor"

    private fun String.toIdentifier(): String {
        val identifier = split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotEmpty)
            .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
            .ifEmpty { "Module" }
        return if (identifier.first().isLetter()) identifier else "Module$identifier"
    }

    private fun clientMethod(function: KSFunctionDeclaration): String {
        val name = function.simpleName.asString()
        val parameter = function.parameters.singleOrNull()
        val declaration = function.returnType!!.resolve().declaration.qualifiedName!!.asString()
        val signature = signature(function)
        val params = parameter?.let { "${it.name!!.asString()}: ${typeName(it.type.resolve())}" } ?: ""
        val argument = parameter?.name?.asString()
        return when (declaration) {
            "kotlin.Unit" -> if (function.isFireAndForget()) {
                "    override suspend fun $name($params) { binding.fireAndForget(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()"}) }"
            } else {
                "    override suspend fun $name($params) { binding.requestResponse(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()"}) }"
            }
            "kotlinx.coroutines.flow.Flow" -> {
                val element = function.returnType!!.resolve().arguments.single().type!!.resolve()
                "    override fun $name($params): ${typeName(function.returnType!!.resolve())} = flow { emitAll(binding.requestStream(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()" }).map { it.decode<${typeName(element)}>() }) }"
            }
            else -> "    override suspend fun $name($params): ${typeName(function.returnType!!.resolve())} = binding.requestResponse(\"$signature\", ${argument?.let { "$it.encodeToMessage()" } ?: "emptyMessage()" }).decode()"
        }
    }

    private fun serverUnitBranch(function: KSFunctionDeclaration): String =
        "                    \"${signature(function)}\" -> instance.${call(function)}"

    private fun serverResponseBranch(function: KSFunctionDeclaration): String =
        if (function.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit") {
            "                    \"${signature(function)}\" -> { instance.${call(function)}; Unit.encodeToMessage() }"
        } else {
            "                    \"${signature(function)}\" -> instance.${call(function)}.encodeToMessage()"
        }

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

    private fun KSFunctionDeclaration.isFireAndForget(): Boolean = annotations.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == "ifx.service.FireAndForget"
    }

    private fun typeName(type: KSType): String = type.declaration.qualifiedName?.asString()?.let { raw ->
        if (type.arguments.isEmpty()) raw + if (type.nullability.name == "NULLABLE") "?" else ""
        else raw + type.arguments.joinToString(prefix = "<", postfix = ">") { argument -> typeName(argument.type!!.resolve()) } + if (type.nullability.name == "NULLABLE") "?" else ""
    } ?: type.toString()

    private companion object {
        const val INDEX_PACKAGE = "ifx.service.index"
        const val INDEX_ANNOTATION = "ifx.service.IfxServiceIndex"
        const val GENERATED_PACKAGE = "ifx.generated"
        const val SUBSYSTEM_PACKAGE = "ifx.subsystem"
        const val SUBSYSTEM_HOST_SUPPORT = "ifx.subsystem.SubsystemHostSupport"
        val FRAMEWORK_MARKERS = setOf("ifx.service.IService", "ifx.service.IUtility")
    }
}
