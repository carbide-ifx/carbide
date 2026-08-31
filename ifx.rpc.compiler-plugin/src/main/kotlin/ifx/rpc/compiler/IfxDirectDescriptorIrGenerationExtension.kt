package ifx.rpc.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val HOST_CLASS_ID = ClassId.topLevel(FqName("ifx.host.IHost"))
private val HOST_COMPANION_CLASS_ID = HOST_CLASS_ID.createNestedClassId(Name.identifier("Companion"))
private val PROXY_FACTORY_CLASS_ID = ClassId.topLevel(FqName("ifx.proxy.factory.IProxyFactory"))
private val PROXY_CREATE_CALLABLE_ID = CallableId(FqName("ifx.proxy.factory"), Name.identifier("create"))
private val DESCRIPTOR_TYPE = FqName("ifx.protocol.contract.ServiceDescriptor")

/** Replaces typed host/proxy conveniences with calls that pass their generated descriptor directly. */
class IfxDirectDescriptorIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION")
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val hostClass = pluginContext.referenceClass(HOST_CLASS_ID)
        val hostCompanion = pluginContext.referenceClass(HOST_COMPANION_CLASS_ID)
        val proxyFactoryClass = pluginContext.referenceClass(PROXY_FACTORY_CLASS_ID)

        val replacements = buildMap<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> {
            if (hostClass != null && hostCompanion != null) {
                val hostIntrinsics = hostCompanion.owner.functions
                    .filter { it.name.asString() == "registerService" }
                    .associateBy { it.regularParameterName() }
                val hostTargets = hostClass.owner.functions
                    .filter { it.name.asString() == "registerService" && it.acceptsDescriptor() }
                    .associateBy { it.regularParameterName(excludingDescriptor = true) }
                hostIntrinsics.forEach { (parameterName, intrinsic) ->
                    hostTargets[parameterName]?.let { target -> put(intrinsic.symbol, target.symbol) }
                }
            }
            if (proxyFactoryClass != null) {
                val proxyIntrinsic = pluginContext.referenceFunctions(PROXY_CREATE_CALLABLE_ID).singleOrNull()
                val proxyTarget = proxyFactoryClass.owner.functions.singleOrNull {
                    it.name.asString() == "create" && it.acceptsDescriptor()
                }
                if (proxyIntrinsic != null && proxyTarget != null) put(proxyIntrinsic, proxyTarget.symbol)
            }
        }

        moduleFragment.transform(
            object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    expression.transformChildren(this, null)
                    val replacement = replacements[expression.symbol]
                    if (replacement == null) {
                        injectDefaultDescriptors(expression, pluginContext)
                        return expression
                    }
                    val contractType = expression.typeArguments.singleOrNull()
                    val contract = contractType?.classOrNull
                    if (contractType == null || contract == null) {
                        pluginContext.messageCollector.report(
                            CompilerMessageSeverity.ERROR,
                            "IFX typed service calls require a concrete IService type argument",
                        )
                        return expression
                    }
                    val descriptor = pluginContext.descriptorFor(contract)
                    if (descriptor == null) {
                        val contractName = contract.owner.fqNameWhenAvailable?.asString() ?: contract.owner.name.asString()
                        pluginContext.messageCollector.report(
                            CompilerMessageSeverity.ERROR,
                            "No generated IFX descriptor found for $contractName. " +
                                "Apply ifx.subsystem.ksp to the consuming subsystem module.",
                        )
                        return expression
                    }

                    val builder = DeclarationIrBuilder(
                        pluginContext,
                        expression.symbol,
                        expression.startOffset,
                        expression.endOffset,
                    )
                    return builder.irCall(replacement).apply {
                        type = expression.type
                        typeArguments[0] = contractType
                        replacement.owner.parameters.forEach { targetParameter ->
                            arguments[targetParameter] = when {
                                targetParameter.kind == IrParameterKind.DispatchReceiver -> {
                                    val sourceReceiver = expression.symbol.owner.parameters.single {
                                        it.kind == IrParameterKind.ExtensionReceiver
                                    }
                                    expression.arguments[sourceReceiver]
                                }
                                targetParameter.type.classFqName == DESCRIPTOR_TYPE -> builder.irGetObject(descriptor)
                                else -> {
                                    val sourceParameter = expression.symbol.owner.parameters.single {
                                        it.kind == targetParameter.kind && it.name == targetParameter.name
                                    }
                                    expression.arguments[sourceParameter]
                                }
                            }
                        }
                    }
                }
            },
            null,
        )
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
@Suppress("DEPRECATION")
private fun injectDefaultDescriptors(expression: IrCall, pluginContext: IrPluginContext) {
    expression.symbol.owner.parameters
        .filter { parameter ->
            parameter.kind == IrParameterKind.Regular &&
                parameter.type.classFqName == DESCRIPTOR_TYPE &&
                expression.arguments[parameter] == null
        }
        .forEach { parameter ->
            val contractType = ((parameter.type as? IrSimpleType)
                ?.arguments
                ?.singleOrNull() as? IrTypeProjection)
                ?.type
            val contract = contractType?.classOrNull
            val descriptor = contract?.let(pluginContext::descriptorFor)
            if (descriptor == null) {
                pluginContext.messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "No generated IFX descriptor found for default descriptor parameter ${parameter.name}",
                )
            } else {
                val builder = DeclarationIrBuilder(
                    pluginContext,
                    expression.symbol,
                    expression.startOffset,
                    expression.endOffset,
                )
                expression.arguments[parameter] = builder.irGetObject(descriptor)
            }
        }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
@Suppress("DEPRECATION")
private fun IrPluginContext.descriptorFor(contract: IrClassSymbol): IrClassSymbol? {
    val contractName = contract.owner.fqNameWhenAvailable ?: return null
    val descriptorName = contractName.parent().child(Name.identifier("${contract.owner.name}Descriptor"))
    return referenceClass(ClassId.topLevel(descriptorName))
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun org.jetbrains.kotlin.ir.declarations.IrSimpleFunction.acceptsDescriptor(): Boolean =
    parameters.any { it.kind == IrParameterKind.Regular && it.type.classFqName == DESCRIPTOR_TYPE }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun org.jetbrains.kotlin.ir.declarations.IrSimpleFunction.regularParameterName(
    excludingDescriptor: Boolean = false,
): Name? = parameters.singleOrNull {
    it.kind == IrParameterKind.Regular && (!excludingDescriptor || it.type.classFqName != DESCRIPTOR_TYPE)
}?.name
