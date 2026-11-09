package com.chichu.toolbox.core.ksu

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class KsuProtocolTest {
    @Test fun parsesActualKsudStringFlags() {
        val modules = KsuProtocol.modules("""[{"id":"test.module","name":"测试模块","version":"1.0","description":"test","enabled":"true","update":"false","remove":"false","web":"true","action":"false"}]""")
        assertEquals("test.module", modules.single().id)
        assertTrue(modules.single().enabled)
        assertTrue(modules.single().web)
        assertFalse(modules.single().remove)
    }
    @Test fun emptyArrayIsDifferentFromCommunicationFailure() {
        assertTrue(KsuProtocol.modules("[]").isEmpty())
        assertThrows(IOException::class.java) { KsuProtocol.modules("") }
        assertThrows(IOException::class.java) { CommandResult(1, "permission denied").requireSuccess() }
    }
    @Test fun rejectsMalformedAndIncompleteRecords() {
        listOf("{}", "[null]", "[{\"id\":\"foo\"}]", "not json").forEach {
            assertThrows(IOException::class.java) { KsuProtocol.modules(it) }
        }
    }
    @Test fun rejectsShellAndDirectoryEscapesInModuleIds() {
        listOf("../other", "x;id", "x'", "/data", "x/y", "").forEach {
            assertThrows(IOException::class.java) { KsuProtocol.moduleId(it) }
        }
        assertEquals("'a'\\''b'", KsuProtocol.quote("a'b"))
    }
    @Test fun webOriginAndDecodedPathsAreRestricted() {
        assertEquals("index.html", KsuProtocol.webPath("https://module.chichu.local/", "module.chichu.local"))
        assertEquals("assets/app.js", KsuProtocol.webPath("https://module.chichu.local/assets/app.js?v=1", "module.chichu.local"))
        listOf("https://evil.test/", "http://module.chichu.local/", "https://module.chichu.local:443/", "https://user@module.chichu.local/", "file:///data/adb/", "https://module.chichu.local/%2e%2e/secret", "https://module.chichu.local/x/%2e/secret", "https://module.chichu.local/%5csecret", "https://module.chichu.local/%00").forEach {
            assertNull(it, KsuProtocol.webPath(it, "module.chichu.local"))
        }
        val first = KsuProtocol.webHost("module.a")
        val second = KsuProtocol.webHost("module.b")
        assertNotEquals(first, second)
        assertNull(KsuProtocol.webPath("https://$first/index.html", second))
    }
    @Test fun managementRequiresIdentityAndMatchingUapi() {
        val notManager = KernelStatus(0, 33000, 1, 6)
        assertNotNull(notManager.managementIssue)
        assertNotNull(KernelStatus(0, 33000, 2, 5).managementIssue)
        assertNotNull(KernelStatus(-19, 0, 0, 0).managementIssue)
        assertNull(KernelStatus(0, 33000, 2, 6).managementIssue)
        assertEquals(RootMode.LateLoad, KernelStatus(0, 33000, 7, 6).mode)
        assertEquals(RootMode.Bundled, KernelStatus(0, 33000, 19, 6).mode)
    }
    @Test fun bridgeRejectsFramesRemoteOriginsAndClosedSessions() {
        val host = KsuProtocol.webHost("module.a")
        val origin = "https://$host"
        assertTrue(KsuProtocol.bridgeAllowed(origin, true, true, host))
        assertFalse(KsuProtocol.bridgeAllowed(origin, false, true, host))
        assertFalse(KsuProtocol.bridgeAllowed(origin, true, false, host))
        assertFalse(KsuProtocol.bridgeAllowed("https://evil.test", true, true, host))
        assertFalse(KsuProtocol.bridgeAllowed("null", true, true, host))
        assertFalse(KsuProtocol.bridgeAllowed("https://${KsuProtocol.webHost("module.b")}", true, true, host))
    }
    @Test fun existingRootSelectsOnlyAPinnedMatchingDaemon() {
        val text = "version: 32680\nflags: 0x5\nuapi_version: 5\nfeatures: 0x7\nlkm: true\n"
        val kernel = KsuProtocol.kernelInfo(text)
        assertEquals(32680, kernel.version)
        assertEquals(RootMode.LateLoad, kernel.mode)
        assertEquals(RootAccess.ExistingV5, KsuProtocol.existingAccess(kernel))
        assertFalse(kernel.manager)
        assertEquals(RootAccess.ExistingV6, KsuProtocol.existingAccess(kernel.copy(uapi = 6)))
        assertThrows(IOException::class.java) { KsuProtocol.existingAccess(kernel.copy(uapi = 7)) }
        assertThrows(IOException::class.java) { KsuProtocol.kernelInfo(text.replace("32680", "0")) }
        assertThrows(IOException::class.java) { KsuProtocol.kernelInfo("version: 32680\nflags: invalid\nuapi_version: 5") }
        assertThrows(IOException::class.java) { KsuProtocol.kernelInfo("version: 32680") }
    }
}
