package com.chichu.toolbox

import com.chichu.toolbox.core.shizuku.PermissionPrompt
import org.junit.Assert.*
import org.junit.Test

class PermissionPromptsTest {
    private fun request(token: String) = PermissionPrompt(token, 10001, 42, 77, "test.app")

    @Test fun invalidCandidateDoesNotDismissRealRequest() {
        val prompts = PermissionPrompts()
        prompts.replace(request("real"))
        prompts.replace(null)
        assertEquals("real", prompts.current?.token)
    }
    @Test fun oldConfirmationCannotClearNewRequest() {
        val prompts = PermissionPrompts()
        val first = request("first")
        prompts.replace(first)
        assertTrue(prompts.begin(first))
        assertNull(prompts.replace(request("second")))
        prompts.finish(first)
        assertEquals("second", prompts.current?.token)
        assertFalse(prompts.busy)
    }
    @Test fun replacementRejectsOnlyUnansweredRequest() {
        val prompts = PermissionPrompts()
        val first = request("first")
        prompts.replace(first)
        assertEquals(first, prompts.replace(request("second")))
        assertTrue(prompts.begin(requireNotNull(prompts.current)))
        assertFalse(prompts.begin(requireNotNull(prompts.current)))
    }
}
