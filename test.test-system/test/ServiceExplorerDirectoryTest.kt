import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceExplorerDirectoryTest {
    @Test
    fun `container environment selects the packaged service explorer`() {
        assertEquals(
            "/app/webapps/service-explorer",
            serviceExplorerDirectory(
                mapOf(SERVICE_EXPLORER_DIRECTORY_ENV to "/app/webapps/service-explorer"),
            ),
        )
    }

    @Test
    fun `local runs use the npm dist directory`() {
        assertEquals(SERVICE_EXPLORER_DIRECTORY, serviceExplorerDirectory(emptyMap()))
    }
}
