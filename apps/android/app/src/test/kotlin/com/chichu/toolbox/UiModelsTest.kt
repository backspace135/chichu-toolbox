package com.chichu.toolbox

import com.chichu.toolbox.core.common.ServiceIdentity
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.common.ShellGrant
import org.junit.Assert.*
import org.junit.Test

class UiModelsTest {
    @Test fun offlineAndFailureNeverShowConnectedIdentity() {
        assertEquals("未激活", ServiceState.Offline.presentation().title)
        assertEquals("正在连接", ServiceState.Activating.presentation().title)
        val failed = ServiceState.Failed("连接校验失败").presentation()
        assertEquals("连接失败", failed.title)
        assertEquals("连接校验失败", failed.detail)
        val online = ServiceState.Connected(ServiceIdentity(2000, 13, 6)).presentation()
        assertEquals("已连接", online.title)
        assertTrue(online.detail.contains("Shell"))
        assertFalse(online.detail.contains("Root"))
    }

    @Test fun searchMatchesLabelOrPackageWithoutChangingGrants() {
        val apps = listOf(
            ApplicationItem("示例管理器", "com.example.manager", 10001, ShellGrant.Allowed),
            ApplicationItem("测试工具", "org.test.tool", 10002, ShellGrant.Denied),
        )
        assertEquals(listOf(apps[0]), apps.matching("EXAMPLE"))
        assertEquals(listOf(apps[1]), apps.matching("测试"))
        assertEquals(apps, apps.matching("  "))
        assertTrue(apps.matching("不存在").isEmpty())
        assertEquals("已拒绝", ShellGrant.Denied.label)
        assertEquals("未授权", ShellGrant.Ask.label)
    }
}
