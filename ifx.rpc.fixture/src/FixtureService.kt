package ifx.rpc.fixture

import ifx.service.IService
import kotlinx.coroutines.flow.Flow

/** KSP fixture compiled for JVM and macOS Native. */
interface IFixtureService : IService {
    suspend fun echo(value: String): String
    fun values(): Flow<Int>
}
