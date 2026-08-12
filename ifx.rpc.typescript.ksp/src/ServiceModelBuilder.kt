package ifx.rpc.typescript.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability

internal class ServiceModelBuilder {
    private val declarations = linkedMapOf<String, TypeDeclaration>()
    private val collecting = mutableSetOf<String>()

    fun build(contract: KSClassDeclaration): ServiceModel {
        declarations.clear()
        collecting.clear()
        val functions = contract.getAllFunctions().filterNot(::isAnyMethod).toList()
        val overloadCounts = functions.groupingBy { it.simpleName.asString() }.eachCount()
        val overloadIndexes = mutableMapOf<String, Int>()
        val operations = functions.map { function ->
            val name = function.simpleName.asString()
            val overloadIndex = overloadIndexes.merge(name, 1, Int::plus)!!
            operation(
                function,
                if (overloadCounts.getValue(name) == 1) name.upperCamelCase() else name.upperCamelCase() + overloadIndex,
            )
        }
        return ServiceModel(
            name = contract.simpleName.asString(),
            address = contract.qualifiedName!!.asString(),
            kind = if (contract.isUtility()) ServiceKind.UTILITY else ServiceKind.SERVICE,
            operations = operations,
            declarations = declarations.values.sortedBy(TypeDeclaration::qualifiedName),
        )
    }

    private fun KSClassDeclaration.isUtility(): Boolean =
        getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == UTILITY }

    private fun operation(function: KSFunctionDeclaration, typeName: String): OperationModel {
        if (function.typeParameters.isNotEmpty() || function.parameters.size > 1) {
            fail("Service operation ${function.simpleName.asString()} must have at most one parameter and no type parameters", function)
        }
        val returnType = function.returnType?.resolve()
            ?: fail("Service operation ${function.simpleName.asString()} has no return type", function)
        val returnName = returnType.declaration.qualifiedName?.asString()
        val interaction = when {
            function.isFireAndForget() -> Interaction.FIRE_AND_FORGET
            returnName == FLOW -> Interaction.REQUEST_STREAM
            else -> Interaction.REQUEST_RESPONSE
        }
        val isSuspend = Modifier.SUSPEND in function.modifiers
        if ((interaction == Interaction.REQUEST_STREAM && isSuspend) || (interaction != Interaction.REQUEST_STREAM && !isSuspend)) {
            fail("Service operation ${function.simpleName.asString()} has an invalid suspend/Flow combination", function)
        }
        if (function.isFireAndForget() && (returnName != "kotlin.Unit" || !isSuspend)) {
            fail("@FireAndForget may only be used on suspending IFX service methods returning Unit", function)
        }
        val parameter = function.parameters.singleOrNull()
        val response = if (interaction == Interaction.REQUEST_STREAM) {
            returnType.arguments.singleOrNull()?.type?.resolve()?.let(::typeRef)
                ?: fail("Stream operation ${function.simpleName.asString()} must return Flow<T>", function)
        } else {
            typeRef(returnType)
        }
        return OperationModel(
            name = function.simpleName.asString(),
            typeName = typeName,
            route = operationRoute(function),
            parameterName = parameter?.name?.asString(),
            request = parameter?.type?.resolve()?.let(::typeRef) ?: TypeRef.VoidType,
            // Kotlin callers evaluate parameter defaults before invoking the generated proxy.
            // TypeScript cannot reproduce an arbitrary Kotlin default expression, so RPC inputs stay required.
            requestOptional = false,
            response = response,
            interaction = interaction,
        )
    }

    private fun typeRef(type: KSType): TypeRef {
        rejectUnsupportedSerializationAnnotations(type.annotations, type.declaration)
        val unwrapped = when (val declaration = type.declaration) {
            is KSTypeAlias -> typeRef(declaration.type.resolve())
            is KSTypeParameter -> TypeRef.TypeParameter(declaration.simpleName.asString())
            is KSClassDeclaration -> classTypeRef(type, declaration)
            else -> fail("Unsupported type ${type.declaration.simpleName.asString()}", type.declaration)
        }
        return if (type.nullability == Nullability.NULLABLE && unwrapped !is TypeRef.Nullable) {
            TypeRef.Nullable(unwrapped)
        } else {
            unwrapped
        }
    }

    private fun classTypeRef(type: KSType, declaration: KSClassDeclaration): TypeRef {
        val qualifiedName = declaration.qualifiedName?.asString()
            ?: fail("Local and anonymous types are not supported", declaration)
        return when (qualifiedName) {
            "kotlin.String", "kotlin.Char" -> TypeRef.StringType
            "kotlin.Boolean" -> TypeRef.BooleanType
            in NUMBER_TYPES -> TypeRef.NumberType
            "kotlin.Unit" -> TypeRef.VoidType
            in ARRAY_TYPES -> TypeRef.ArrayType(typeArgument(type, 0))
            "kotlin.BooleanArray" -> TypeRef.ArrayType(TypeRef.BooleanType)
            "kotlin.CharArray" -> TypeRef.ArrayType(TypeRef.StringType)
            in NUMBER_ARRAY_TYPES -> TypeRef.ArrayType(TypeRef.NumberType)
            in MAP_TYPES -> {
                val key = type.arguments.getOrNull(0)?.type?.resolve()
                    ?: fail("Map keys must be String", declaration)
                if (key.declaration.qualifiedName?.asString() != "kotlin.String" || key.nullability == Nullability.NULLABLE) {
                    fail("Only Map<String, T> is supported in TypeScript contracts", declaration)
                }
                TypeRef.RecordType(typeArgument(type, 1))
            }
            else -> {
                collectDeclaration(declaration)
                TypeRef.Named(qualifiedName, type.arguments.mapIndexed { index, argument ->
                    argument.type?.resolve()?.let(::typeRef)
                        ?: fail("Star-projected type argument $index is not supported for $qualifiedName", declaration)
                })
            }
        }
    }

    private fun collectDeclaration(declaration: KSClassDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString()
            ?: fail("Local and anonymous types are not supported", declaration)
        if (qualifiedName in declarations || !collecting.add(qualifiedName)) return
        requireSerializable(declaration)
        declarations[qualifiedName] = when {
            declaration.classKind == ClassKind.ENUM_CLASS -> enumDeclaration(declaration, qualifiedName)
            Modifier.SEALED in declaration.modifiers -> sealedDeclaration(declaration, qualifiedName)
            Modifier.VALUE in declaration.modifiers -> valueDeclaration(declaration, qualifiedName)
            declaration.classKind == ClassKind.INTERFACE || Modifier.ABSTRACT in declaration.modifiers ->
                fail("Open polymorphic type $qualifiedName is not supported; use a sealed @Serializable hierarchy", declaration)
            else -> objectDeclaration(declaration, qualifiedName)
        }
        collecting.remove(qualifiedName)
    }

    private fun enumDeclaration(declaration: KSClassDeclaration, qualifiedName: String): TypeDeclaration.StringUnion =
        TypeDeclaration.StringUnion(
            qualifiedName = qualifiedName,
            values = declaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { it.serialName() }
                .toList(),
        )

    private fun sealedDeclaration(declaration: KSClassDeclaration, qualifiedName: String): TypeDeclaration.SealedUnion {
        val subclasses = sealedLeaves(declaration).toList()
        if (subclasses.isEmpty()) fail("Sealed type $qualifiedName has no subclasses", declaration)
        return TypeDeclaration.SealedUnion(
            qualifiedName = qualifiedName,
            typeParameters = declaration.typeParameters.map { it.name.asString() },
            discriminator = declaration.annotation(JSON_CLASS_DISCRIMINATOR)
                ?.arguments?.firstOrNull { it.name?.asString() == "discriminator" }
                ?.value as? String ?: "type",
            variants = subclasses.map { subclass ->
                requireSerializable(subclass)
                collectDeclaration(subclass)
                SealedVariant(
                    serialName = subclass.classSerialName(),
                    type = TypeRef.Named(
                        subclass.qualifiedName!!.asString(),
                        subclass.typeParameters.map { TypeRef.TypeParameter(it.name.asString()) },
                    ),
                )
            },
        )
    }

    private fun sealedLeaves(declaration: KSClassDeclaration): Sequence<KSClassDeclaration> =
        declaration.getSealedSubclasses().flatMap { subclass ->
            if (Modifier.SEALED in subclass.modifiers) sealedLeaves(subclass) else sequenceOf(subclass)
        }

    private fun valueDeclaration(declaration: KSClassDeclaration, qualifiedName: String): TypeDeclaration.Alias {
        val property = declaration.serializedProperties().singleOrNull()
            ?: fail("Serializable value class $qualifiedName must have exactly one serialized property", declaration)
        return TypeDeclaration.Alias(
            qualifiedName = qualifiedName,
            typeParameters = declaration.typeParameters.map { it.name.asString() },
            target = typeRef(property.type.resolve()),
        )
    }

    private fun objectDeclaration(declaration: KSClassDeclaration, qualifiedName: String): TypeDeclaration.ObjectType {
        val properties = declaration.serializedProperties()
            .map { property ->
                rejectUnsupportedSerializationAnnotations(property)
                PropertyModel(
                    name = property.serialName(),
                    type = typeRef(property.type.resolve()),
                    optional = property.hasConstructorDefault() && property.annotation(REQUIRED) == null,
                )
            }
            .distinctBy(PropertyModel::name)
            .toList()
        return TypeDeclaration.ObjectType(
            qualifiedName = qualifiedName,
            typeParameters = declaration.typeParameters.map { it.name.asString() },
            properties = properties,
        )
    }

    private fun KSClassDeclaration.serializedProperties(): Sequence<KSPropertyDeclaration> {
        val constructorProperties = primaryConstructor
            ?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            .orEmpty()
        return getAllProperties().filter { property ->
            !property.isTransient() && if (containingFile == null) {
                property.simpleName.asString() in constructorProperties
            } else {
                property.hasBackingField
            }
        }
    }

    private fun requireSerializable(declaration: KSClassDeclaration) {
        val annotation = declaration.annotation(SERIALIZABLE)
            ?: fail("Type ${declaration.qualifiedName?.asString()} must be annotated with @Serializable", declaration)
        val serializer = annotation.arguments.firstOrNull { it.name?.asString() == "with" }?.value as? KSType
        rejectCustomSerializer(serializer, declaration)
        rejectUnsupportedSerializationAnnotations(declaration)
        declaration.containingFile?.let(::rejectUnsupportedSerializationAnnotations)
    }

    private fun rejectUnsupportedSerializationAnnotations(annotated: KSAnnotated) =
        rejectUnsupportedSerializationAnnotations(annotated.annotations, annotated as KSNode)

    private fun rejectUnsupportedSerializationAnnotations(annotations: Sequence<KSAnnotation>, node: KSNode) {
        val resolved = annotations.toList()
        val annotation = resolved.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() in UNSUPPORTED_SERIALIZATION_ANNOTATIONS
        }
        if (annotation != null) {
            fail("@${annotation.shortName.asString()} is not supported for TypeScript generation", node)
        }
        val serializer = resolved.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE
        }?.arguments?.firstOrNull { it.name?.asString() == "with" }?.value as? KSType
        rejectCustomSerializer(serializer, node)
    }

    private fun rejectCustomSerializer(serializer: KSType?, node: KSNode) {
        val serializerName = serializer?.declaration?.qualifiedName?.asString()
        if (serializerName != null && serializerName != "kotlinx.serialization.KSerializer") {
            fail("Custom serializer $serializerName is not supported for TypeScript generation", node)
        }
    }

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

    private fun KSAnnotated.annotation(qualifiedName: String): KSAnnotation? = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }

    private fun typeArgument(type: KSType, index: Int): TypeRef =
        type.arguments.getOrNull(index)?.type?.resolve()?.let(::typeRef)
            ?: fail("Missing type argument $index for ${type.declaration.qualifiedName?.asString()}", type.declaration)

    private fun operationRoute(function: KSFunctionDeclaration): String {
        val parameter = function.parameters.singleOrNull()?.type?.resolve()?.let(::kotlinTypeName).orEmpty()
        return "${function.simpleName.asString()}($parameter)"
    }

    private fun kotlinTypeName(type: KSType): String {
        val raw = type.declaration.qualifiedName?.asString() ?: type.toString()
        val arguments = type.arguments.joinToString(prefix = "<", postfix = ">") { argument ->
            argument.type?.resolve()?.let(::kotlinTypeName) ?: "*"
        }.takeIf { type.arguments.isNotEmpty() }.orEmpty()
        return raw + arguments + if (type.nullability == Nullability.NULLABLE) "?" else ""
    }

    private fun fail(message: String, node: KSNode): Nothing = throw ModelException(message, node)

    private fun isAnyMethod(function: KSFunctionDeclaration): Boolean =
        function.simpleName.asString() in setOf("equals", "hashCode", "toString")

    private fun KSFunctionDeclaration.isFireAndForget(): Boolean = annotations.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == FIRE_AND_FORGET
    }

    private fun String.upperCamelCase(): String = replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }

    private companion object {
        const val UTILITY = "ifx.service.IUtility"
        const val FIRE_AND_FORGET = "ifx.service.FireAndForget"
        const val FLOW = "kotlinx.coroutines.flow.Flow"
        const val SERIALIZABLE = "kotlinx.serialization.Serializable"
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
        val UNSUPPORTED_SERIALIZATION_ANNOTATIONS = setOf(
            "kotlinx.serialization.Contextual",
            "kotlinx.serialization.Polymorphic",
            "kotlinx.serialization.UseContextualSerialization",
            "kotlinx.serialization.UseSerializers",
        )
    }
}

internal class ModelException(message: String, val node: KSNode) : RuntimeException(message)
