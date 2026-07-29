package ifx.rpc.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrAnnotationImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.konan.isNative

private val SERVICE_CLASS_ID = ClassId.topLevel(FqName("ifx.service.IService"))
private val ASSOCIATION_CLASS_ID =
    ClassId.topLevel(FqName("ifx.protocol.contract.WithIfxServiceDescriptor"))

class IfxServiceDescriptorIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION")
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val serviceClass = pluginContext.referenceClass(SERVICE_CLASS_ID) ?: return
        val associationClass = if (pluginContext.platform.isNative()) {
            pluginContext.referenceClass(ASSOCIATION_CLASS_ID)
                ?: error("IFX descriptor association type is missing from the Native runtime")
        } else {
            null
        }
        val associationConstructor = associationClass?.owner?.constructors?.single()?.symbol

        moduleFragment.files
            .asSequence()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<IrClass>()
            .forEach { declaration ->
                if (declaration.kind != ClassKind.INTERFACE) return@forEach
                if (declaration.symbol == serviceClass) return@forEach
                if (!declaration.isServiceContract(serviceClass)) return@forEach

                val contractName = declaration.fqNameWhenAvailable ?: return@forEach
                val descriptorClassId = ClassId.topLevel(FqName("${contractName.asString()}Descriptor"))
                val descriptorClass = pluginContext.referenceClass(descriptorClassId)

                if (descriptorClass == null) {
                    pluginContext.messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "No generated IFX descriptor found for $contractName. " +
                            "Apply the IFX KSP processor to this source set.",
                    )
                    return@forEach
                }

                if (associationClass == null || associationConstructor == null) return@forEach

                val descriptorType = descriptorClass.defaultType
                val descriptorReference = IrClassReferenceImpl(
                    startOffset = declaration.startOffset,
                    endOffset = declaration.endOffset,
                    type = pluginContext.irBuiltIns.kClassClass.typeWith(descriptorType),
                    symbol = descriptorClass,
                    classType = descriptorType,
                )
                val annotation = IrAnnotationImpl.fromSymbolOwner(
                    startOffset = declaration.startOffset,
                    endOffset = declaration.endOffset,
                    type = associationClass.defaultType,
                    constructorSymbol = associationConstructor,
                ).apply {
                    arguments[0] = descriptorReference
                }

                declaration.annotations += annotation
            }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClass.isServiceContract(
    serviceClass: IrClassSymbol,
    visited: MutableSet<IrClassSymbol> = mutableSetOf(),
): Boolean {
    if (!visited.add(symbol)) return false

    return superTypes.any { superType ->
        val superClass = superType.classOrNull ?: return@any false
        superClass == serviceClass || superClass.owner.isServiceContract(serviceClass, visited)
    }
}
