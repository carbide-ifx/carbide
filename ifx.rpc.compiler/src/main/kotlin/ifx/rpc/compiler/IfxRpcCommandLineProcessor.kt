package ifx.rpc.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

const val IFX_RPC_PLUGIN_ID = "ifx.rpc.compiler"

@OptIn(ExperimentalCompilerApi::class)
class IfxRpcCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = IFX_RPC_PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
