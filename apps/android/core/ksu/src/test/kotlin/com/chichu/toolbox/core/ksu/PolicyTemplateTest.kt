package com.chichu.toolbox.core.ksu

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PolicyTemplateTest {
    private val template = PolicyTemplate(0, 0, 2, listOf(0, 3003), 8192, "u:r:ksu:s0", 1)

    @Test fun templateRoundtripAndApplicationPreserveCredentials() {
        val parsed = PolicyTemplate.parse(template.serialize())
        assertEquals(template, parsed)
        val policy = AppPolicy.absent("com.example", 10001).apply(parsed)
        assertTrue(policy.allowed)
        assertFalse(policy.useDefault)
        assertEquals(template, AppPolicy.decode(policy.encode()).template())
    }

    @Test fun invalidCredentialsAreRejectedAtFileAndUserBoundary() {
        listOf(
            template.serialize().replace("\"uid\":0", "\"uid\":-1"),
            template.serialize().replace("\"uid\":0", "\"uid\":0.5"),
            template.serialize().replace("\"namespace\":2", "\"namespace\":3"),
            template.serialize().replace("\"flags\":1", "\"flags\":4"),
            template.serialize().replace("u:r:ksu:s0", "bad\"domain"),
            "{}", "[]", "null", "broken",
        ).forEach { assertThrows(it, IOException::class.java) { PolicyTemplate.parse(it) } }
    }

    @Test fun excessiveGroupsAndUnsupportedCapabilityBitsAreRejected() {
        assertThrows(IOException::class.java) { PolicyTemplate.parse(template.copy(groups = (0..32).toList()).serialize()) }
        assertThrows(IOException::class.java) { PolicyTemplate.parse(template.copy(capabilities = 1L shl 41).serialize()) }
    }

    @Test fun defaultGrantIncludesDomainRequiredByKernelProfileValidation() {
        val policy = AppPolicy.absent("com.example", 10001).grant(true)
        assertEquals("u:r:ksu:s0", policy.context)
        assertTrue(policy.useDefault)
    }

    @Test fun upstreamKsudTemplateImportMapsNamesAndRules() {
        val source = """
            {"id":"root","name":"root","description":"root","author":"t","local":true,
             "namespace":"ISOLATED","uid":0,"gid":0,"groups":["ROOT","SYSTEM","SHELL","UNKNOWN_GROUP"],
             "capabilities":["CAP_CHOWN","CAP_DAC_OVERRIDE","CAP_UNKNOWN"],"context":"u:r:ksu:s0",
             "rules":["allow domain ksu_file file open","allow domain ksu_file file read"],
             "flags":["NO_NEW_PRIVS"]}
        """.trimIndent()
        val imported = PolicyTemplate.fromUpstream(source)
        assertEquals(PolicyTemplate(0, 0, 2, listOf(0, 1000, 2000), 3L, "u:r:ksu:s0", 1,
            "allow domain ksu_file file open\nallow domain ksu_file file read"), imported)
        assertEquals(imported, PolicyTemplate.parse(imported.serialize()))
    }

    @Test fun upstreamDefaultsAndMissingFieldsMapLikeTheOriginalManager() {
        assertEquals(PolicyTemplate(0, 0, 0, emptyList(), 0L, "u:r:ksu:s0", 1, ""),
            PolicyTemplate.fromUpstream("""{"id":"x"}"""))
    }

    @Test fun brokenUpstreamTemplatesAreRejected() {
        assertThrows(IOException::class.java) { PolicyTemplate.fromUpstream("broken") }
        assertThrows(IOException::class.java) { PolicyTemplate.fromUpstream("[]") }
    }
}
