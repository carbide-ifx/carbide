package ifx.rpc.fixture

import ifx.service.IService

interface IFixtureService : IService {
    suspend fun echo(value: String): String
}

class FixtureService : IFixtureService {
    override suspend fun echo(value: String): String = "echo:$value"
}
