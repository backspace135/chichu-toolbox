package com.chichu.toolbox

import com.chichu.toolbox.core.ksu.*
import org.junit.Assert.*
import org.junit.Test

class RootStateTest {
    private fun snapshot(access: RootAccess, manager: Boolean) = RootSnapshot(
        KernelStatus(0, 32680, if (manager) 2 else 0, KSU_UAPI),
        true, "certificate", "ksud", true, access, null, false,
    )

    @Test fun existingRootWithLoadedModulesDoesNotReportZeroGrants() {
        val state = RootState(snapshot = snapshot(RootAccess.ExistingV5, false), modulesLoaded = true)
        assertTrue(state.policySummary.contains("授权列表不可读取"))
        assertTrue(state.policySummary.contains("管理器身份"))
        assertFalse(state.policySummary.contains("0 个"))
    }

    @Test fun onlySuccessfullyReadEmptyPolicyListReportsZero() {
        val state = RootState(snapshot = snapshot(RootAccess.Manager, true))
        assertTrue(state.policySummary.contains("尚未成功读取"))
        assertFalse(state.policySummary.contains("0 个"))
        assertEquals("0 个 UID / 0 个应用已允许", state.copy(applications = emptyList()).policySummary)
    }
}
