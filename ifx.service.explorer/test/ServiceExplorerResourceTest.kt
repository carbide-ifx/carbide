package ifx.service.explorer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ServiceExplorerResourceTest {
    @Test
    fun `published module contains the service explorer frontend`() {
        val html = bundledServiceExplorerAsset("index.html").decodeToString()
        val javascript = bundledServiceExplorerAsset("test-ui.js.gz")

        assertContains(html, "iFX Service Explorer")
        assertTrue(javascript.isNotEmpty())
    }
}
