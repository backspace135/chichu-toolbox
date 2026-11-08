package com.chichu.toolbox.core.shizuku

import com.chichu.toolbox.core.shizuku.adb.SerialResolver
import org.junit.Assert.*
import org.junit.Test

class SerialResolverTest {
    @Test fun bothDiscoveryTypesShareOneActiveResolution() {
        val callbacks = mutableListOf<(String?) -> Unit>()
        val started = mutableListOf<String>()
        val published = mutableListOf<String>()
        val resolver = SerialResolver<String>({ item, done -> started += item; callbacks += done }, { published += it })
        resolver.found("pair", "pair")
        resolver.found("connect", "connect")
        assertEquals(listOf("pair"), started)
        callbacks[0]("paired")
        assertEquals(listOf("pair", "connect"), started)
        callbacks[1]("connected")
        assertEquals(listOf("paired", "connected"), published)
    }
    @Test fun lostAndRediscoveredServiceRejectsOldResult() {
        val callbacks = mutableListOf<(String?) -> Unit>()
        val published = mutableListOf<String>()
        val resolver = SerialResolver<String>({ _, done -> callbacks += done }, { published += it })
        resolver.found("service", "old")
        resolver.lost("service")
        resolver.found("service", "new")
        callbacks[0]("stale")
        callbacks[1]("fresh")
        assertEquals(listOf("fresh"), published)
    }
    @Test fun failuresRetryButNeverLoopForever() {
        val callbacks = mutableListOf<(String?) -> Unit>()
        val resolver = SerialResolver<String>({ _, done -> callbacks += done }, { fail("unexpected publication") })
        resolver.found("service", "item")
        callbacks[0](null)
        callbacks[1](null)
        callbacks[2](null)
        assertEquals(3, callbacks.size)
    }
}
