package com.chichu.toolbox.core.ghostlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KernelReleaseTest {
    @Test
    fun retainsCompleteReleaseIncludingBuildSuffix() {
        val release = requireNotNull(KernelRelease.parse("6.6.118-android15-8-g1234-ab9876"))
        assertEquals(6, release.major)
        assertEquals(6, release.minor)
        assertEquals(118, release.patch)
        assertEquals("-android15-8-g1234-ab9876", release.suffix)
        assertEquals("6.6.118-android15-8-g1234-ab9876", release.full)
    }

    @Test
    fun releasesWithSameNumbersAreNotEquivalent() {
        assertNotEquals(KernelRelease.parse("6.6.118-android15-8"), KernelRelease.parse("6.6.118-android15-9"))
        assertNotEquals(KernelRelease.parse("6.6.118"), KernelRelease.parse("6.6.118+"))
    }

    @Test
    fun rejectsPartialVersionsExtraTextAndOverflow() {
        listOf("", "6.6", "Linux 6.6.118", "6.6.118\n", "6.6.118 suffix", "2147483648.6.118").forEach {
            assertNull(it, KernelRelease.parse(it))
        }
    }
}
