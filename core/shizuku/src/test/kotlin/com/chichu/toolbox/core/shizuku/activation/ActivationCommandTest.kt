package com.chichu.toolbox.core.shizuku.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivationCommandTest {
    @Test
    fun quotesNativeAndApkPathsForShell() {
        assertEquals(
            "'/data/app/test dir/lib/arm64/libchichu_starter.so' --apk='/data/app/test dir/base.apk'",
            ActivationCommand.create("/data/app/test dir/lib/arm64", "/data/app/test dir/base.apk"),
        )
    }

    @Test
    fun escapesSingleQuotesInsteadOfEndingShellArgument() {
        assertEquals(
            "'/a'\"'\"'b/libchichu_starter.so' --apk='/x'\"'\"'y.apk'",
            ActivationCommand.create("/a'b", "/x'y.apk"),
        )
    }

    @Test
    fun installationUpdateChangesActivationCommand() {
        assertNotEquals(
            ActivationCommand.create("/data/app/old/lib/arm64", "/data/app/old/base.apk"),
            ActivationCommand.create("/data/app/new/lib/arm64", "/data/app/new/base.apk"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesRelativeNativePath() {
        ActivationCommand.create("lib/arm64", "/data/app/base.apk")
    }
}
