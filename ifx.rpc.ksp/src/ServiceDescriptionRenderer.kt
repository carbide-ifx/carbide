package ifx.rpc.ksp

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability

/** Renders the runtime schema beside the executable Kotlin service descriptor. */
internal class ServiceDescriptionRenderer {
    private val declarations = linkedMapOf<String, String>()
    private val collecting = mutableSetOf<String>()

    fun render(contract: KSClassDeclaration): String {
        declarations.clear()
        collecting.clear()
        val operations = contract.getAllFunctions().filterNot(::isAnyMethod).map(::operation).toList()
        return "ServiceDescription(\n" +
            "        name = ${literal(contract.simpleName.asString())},\n" +
            "        address = ${literal(contract.qualifiedName!!.asString())},\n" +
            "        operations = listOf(${operations.joinToString(",") { "\n            $it" }}\n        ),\n" +
            "        types = listOf(${declarations.values.joinToString(",") { "\n            $it" }}\n        ),\n" +
            "    )"
    }

    private fun operation(function: KSFunctionDeclaration): String {
        val returnType = function.returnType!!.resolve()
        val returnName = returnType.declaration.qualifiedName?.asString()
        val interaction = when {
            function.isFireAndForget() -> "InteractionType.FIRE_AND_FORGET"
            returnName == FLOW -> "InteractionType.REQUEST_STREAM"
            else -> "InteractionType.REQUEST_RESPONSE"
        }
        val parameter = function.parameters.singleOrNull()
        val response = if (returnName == FLOW) typeRef(returnType.arguments.single().type!!.resolve()) else typeRef(returnType)
        return "OperationDescription(" +
            "name = ${literal(function.simpleName.asString())}, " +
            "route = ${literal(signature(function))}, " +
            "parameterName = ${parameter?.name?.asString()?.let(::literal) ?: "null"}, " +
            "request = ${parameter?.type?.resolve()?.let(::typeRef) ?: "TypeReference.VoidType"}, " +
            "response = $response, interaction = $interaction)"
    }

    private fun typeRef(type: KSType): String {
        val unwrapped = when (val declaration = type.declaration) {
            is KSTypeAlias -> typeRef(declaration.type.resolve())
            is KSTypeParameter -> "TypeReference.Parameter(${literal(declaration.simpleName.asString())})"
            is KSClassDeclaration -> classTypeRef(type, declaration)
            else -> error("Unsupported RPC type ${declaration.simpleName.asString()}")
        }
        return if (type.nullability == Nullability.NULLABLE && !unwrapped.startsWith("TypeReference.Nullable(")) {
            "TypeReference.Nullable($unwrapped)"
        } else unwrapped
    }

    private fun classTypeRef(type: KSType, declaration: KSClassDeclaration): String {
        val name = declaration.qualifiedName?.asString()
            ?: error("Local and anonymous RPC types are not supported")
        return when (name) {
            "kotlin.String", "kotlin.Char" -> "TypeReference.StringType"
            "kotlin.Boolean" -> "TypeReference.BooleanType"
            in NUMBER_TYPES -> "TypeReference.NumberType"
            "kotlin.Unit" -> "TypeReference.VoidType"
            in ARRAY_TYPES -> "TypeReference.ArrayType(${typeArgument(type, 0)})"
            "kotlin.BooleanArray" -> "TypeReference.ArrayType(TypeReference.BooleanType)"
            "kotlin.CharArray" -> "TypeReference.ArrayType(TypeReference.StringType)"
            in NUMBER_ARRAY_TYPES -> "TypeReference.ArrayType(TypeReference.NumberType)"
            in MAP_TYPES -> "TypeReference.RecordType(${typeArgument(type, 1)})"
            else -> {
                collectDeclaration(declaration)
                val arguments = type.arguments.mapNotNull { it.type?.resolve() }.map(::typeRef)
                "TypeReference.Named(${literal(name)}, listOf(${arguments.joinToString()}))"
            }
        }
    }

    private fun collectDeclaration(declaration: KSClassDeclaration) {
        val name = declaration.qualifiedName?.asString()
            ?: error("Local and anonymous RPC types are not supported")
        if (name in declarations || !collecting.add(name)) return

        // Reserve insertion order before walking child types, so definitions remain deterministic.
        declarations[name] = ""
        declarations[name] = when {
            declaration.classKind == ClassKind.ENUM_CLASS -> stringUnion(declaration, name)
            Modifier.SEALED in declaration.modifiers -> sealedUnion(declaration, name)
            Modifier.VALUE in declaration.modifiers -> alias(declaration, name)
            else -> objectType(declaration, name)
        }
        collecting.remove(name)
    }

    private fun stringUnion(declaration: KSClassDeclaration, name: String): String {
        val values = declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { literal(it.serialName()) }
            .toList()
        return "TypeDescription.StringUnion(name = ${literal(name)}, values = listOf(${values.joinToString()}))"
    }

    private fun sealedUnion(declaration: KSClassDeclaration, name: String): String {
        val discriminator = declaration.annotation(JSON_CLASS_DISCRIMINATOR)
            ?.arguments?.firstOrNull { it.name?.asString() == "discriminator" }
            ?.value as? String ?: "type"
        val variants = sealedLeaves(declaration).map { variant ->
            collectDeclaration(variant)
            val variantName = variant.qualifiedName!!.asString()
            val arguments = variant.typeParameters.map {
                "TypeReference.Parameter(${literal(it.name.asString())})"
            }
            "UnionVariantDescription(" +
                "serialName = ${literal(variant.classSerialName())}, " +
                "type = TypeReference.Named(${literal(variantName)}, listOf(${arguments.joinToString()})))"
        }.toList()
        return "TypeDescription.SealedUnion(" +
            "name = ${literal(name)}, " +
            "typeParameters = ${typeParameters(declaration)}, " +
            "discriminator = ${literal(discriminator)}, " +
            "variants = listOf(${variants.joinToString()}))"
    }

    private fun sealedLeaves(declaration: KSClassDeclaration): Sequence<KSClassDeclaration> =
        declaration.getSealedSubclasses().flatMap { subclass ->
            if (Modifier.SEALED in subclass.modifiers) sealedLeaves(subclass) else sequenceOf(subclass)
        }

    private fun alias(declaration: KSClassDeclaration, name: String): String {
        val property = declaration.getAllProperties().single { it.hasBackingField && !it.isTransient() }
        return "TypeDescription.Alias(" +
            "name = ${literal(name)}, typeParameters = ${typeParameters(declaration)}, target = ${typeRef(property.type.resolve())})"
    }

    private fun objectType(declaration: KSClassDeclaration, name: String): String {
        val properties = declaration.getAllProperties()
            .filter { it.hasBackingField && !it.isTransient() }
            .distinctBy { it.serialName() }
            .map { property ->
                "PropertyDescription(" +
                    "name = ${literal(property.serialName())}, " +
                    "type = ${typeRef(property.type.resolve())}, " +
                    "optional = ${property.hasConstructorDefault() && property.annotation(REQUIRED) == null})"
            }
            .toList()
        return "TypeDescription.ObjectType(" +
            "name = ${literal(name)}, " +
            "typeParameters = ${typeParameters(declaration)}, " +
            "properties = listOf(${properties.joinToString()}))"
    }

    private fun typeParameters(declaration: KSClassDeclaration): String =
        "listOf(${declaration.typeParameters.joinToString { literal(it.name.asString()) }})"

    private fun KSPropertyDeclaration.hasConstructorDefault(): Boolean {
        val owner = parentDeclaration as? KSClassDeclaration ?: return false
        return owner.primaryConstructor?.parameters?.firstOrNull {
            it.name?.asString() == simpleName.asString()
        }?.hasDefault == true
    }

    private fun KSAnnotated.isTransient(): Boolean = annotation(TRANSIENT) != null

    private fun KSDeclaration.serialName(): String = annotation(SERIAL_NAME)
        ?.arguments?.firstOrNull { it.name?.asString() == "value" }
        ?.value as? String ?: simpleName.asString()

    private fun KSClassDeclaration.classSerialName(): String = annotation(SERIAL_NAME)
        ?.arguments?.firstOrNull { it.name?.asString() == "value" }
        ?.value as? String ?: qualifiedName!!.asString()

    private fun KSAnnotated.annotation(name: String): KSAnnotation? = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == name
    }

    private fun typeArgument(type: KSType, index: Int): String =
        type.arguments[index].type?.resolve()?.let(::typeRef)
            ?: error("Missing RPC type argument $index")

    private fun signature(function: KSFunctionDeclaration): String {
        val parameter = function.parameters.singleOrNull()?.type?.resolve()?.let(::typeName).orEmpty()
        return "${function.simpleName.asString()}($parameter)"
    }

    private fun typeName(type: KSType): String {
        val raw = type.declaration.qualifiedName?.asString() ?: type.toString()
        val arguments = type.arguments.joinToString(prefix = "<", postfix = ">") { argument ->
            argument.type?.resolve()?.let(::typeName) ?: "*"
        }.takeIf { type.arguments.isNotEmpty() }.orEmpty()
        return raw + arguments + if (type.nullability == Nullability.NULLABLE) "?" else ""
    }

    private fun literal(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun isAnyMethod(function: KSFunctionDeclaration): Boolean =
        function.simpleName.asString() in setOf("equals", "hashCode", "toString")

    private fun KSFunctionDeclaration.isFireAndForget(): Boolean = annotations.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == FIRE_AND_FORGET
    }

    private companion object {
        const val FIRE_AND_FORGET = "ifx.service.FireAndForget"
        const val FLOW = "kotlinx.coroutines.flow.Flow"
        const val SERIAL_NAME = "kotlinx.serialization.SerialName"
        const val TRANSIENT = "kotlinx.serialization.Transient"
        const val REQUIRED = "kotlinx.serialization.Required"
        const val JSON_CLASS_DISCRIMINATOR = "kotlinx.serialization.json.JsonClassDiscriminator"

        val NUMBER_TYPES = setOf(
            "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
            "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong",
        )
        val ARRAY_TYPES = setOf(
            "kotlin.Array", "kotlin.collections.Iterable", "kotlin.collections.Collection",
            "kotlin.collections.MutableCollection", "kotlin.collections.List", "kotlin.collections.MutableList",
            "kotlin.collections.Set", "kotlin.collections.MutableSet",
        )
        val NUMBER_ARRAY_TYPES = setOf(
            "kotlin.ByteArray", "kotlin.ShortArray", "kotlin.IntArray", "kotlin.LongArray",
            "kotlin.FloatArray", "kotlin.DoubleArray", "kotlin.UByteArray", "kotlin.UShortArray",
            "kotlin.UIntArray", "kotlin.ULongArray",
        )
        val MAP_TYPES = setOf("kotlin.collections.Map", "kotlin.collections.MutableMap")
    }
}
