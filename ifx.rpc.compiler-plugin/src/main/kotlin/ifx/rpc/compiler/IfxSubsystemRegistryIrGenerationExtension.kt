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
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val GENERATED_REGISTRY = ClassId.topLevel(FqName("ifx.generated.ServiceDescriptors"))
private val REGISTRY_TYPE = FqName("ifx.protocol.contract.ServiceDescriptorRegistry")
private val SUBSYSTEM_PACKAGE = FqName("ifx.subsystem")
private val HOST_FUNCTIONS = listOf("default", "subsystem")

class IfxSubsystemRegistryIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION")
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val registry = pluginContext.referenceClass(GENERATED_REGISTRY)
        if (registry == null) {
            pluginContext.messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "No generated IFX subsystem registry found at ${GENERATED_REGISTRY.asSingleFqName()}. " +
                    "Apply ifx.rpc.ksp to this subsystem module.",
            )
            return
        }

        val replacements = HOST_FUNCTIONS.associate { name ->
            val functions = pluginContext.referenceFunctions(
                CallableId(SUBSYSTEM_PACKAGE, Name.identifier(name)),
            )
            val generatedOverload = functions.single { !it.acceptsRegistry() }
            val explicitOverload = functions.single { it.acceptsRegistry() }
            generatedOverload to explicitOverload
        }

        moduleFragment.transform(
            object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    expression.transformChildren(this, null)
                    val replacement = replacements[expression.symbol] ?: return expression
                    val builder = DeclarationIrBuilder(
                        pluginContext,
                        expression.symbol,
                        expression.startOffset,
                        expression.endOffset,
                    )
                    val call = builder.irCall(replacement)
                    replacement.owner.parameters.forEach { targetParameter ->
                        call.arguments[targetParameter] = when {
                            targetParameter.kind == IrParameterKind.ExtensionReceiver -> {
                                val sourceParameter = expression.symbol.owner.parameters.single {
                                    it.kind == IrParameterKind.ExtensionReceiver
                                }
                                expression.arguments[sourceParameter]
                            }
                            targetParameter.type.classFqName == REGISTRY_TYPE -> builder.irGetObject(registry)
                            else -> {
                                val sourceParameter = expression.symbol.owner.parameters.single {
                                    it.kind == IrParameterKind.Regular && it.name == targetParameter.name
                                }
                                expression.arguments[sourceParameter]
                            }
                        }
                    }
                    return call
                }
            },
            null,
        )
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrSimpleFunctionSymbol.acceptsRegistry(): Boolean =
    owner.parameters.any { it.kind == IrParameterKind.Regular && it.type.classFqName == REGISTRY_TYPE }
