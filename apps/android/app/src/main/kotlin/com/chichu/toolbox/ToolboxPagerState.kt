package com.chichu.toolbox

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

/** A retargetable tab scroll. New taps retain the displayed position and current velocity. */
@Stable
internal class ToolboxPagerState(val pager: PagerState, private val scope: CoroutineScope) {
    private var requestedPage by mutableStateOf<Int?>(null)
    val selectedPage: Int get() = requestedPage ?: pager.currentPage
    private var navigationJob: Job? = null
    private var generation = 0L
    private var velocity = 0f

    fun select(page: Int) {
        if (page !in 0 until pager.pageCount || requestedPage == page) return
        if (requestedPage == null && !pager.isScrollInProgress && pager.currentPage == page) return
        val operation = ++generation
        val initialVelocity = velocity
        navigationJob?.cancel()
        requestedPage = page
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // Match the gesture driver's priority: a new tap can also take over its
                // release spring, and the next finger-down can take over this animation.
                with(pager) {
                    scroll(MutatePriority.UserInput) {
                        updateTargetPage(page)
                        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                        val distance = (page - currentPage - currentPageOffsetFraction) * pageSize
                        var consumed = 0f
                        animate(0f, distance, initialVelocity, PagerNavigationSpringSpec) { position, speed ->
                            velocity = speed
                            consumed += scrollBy(position - consumed)
                        }
                        updateCurrentPage(page)
                    }
                }
            } finally {
                // An obsolete operation cannot clear the new target or its velocity.
                if (operation == generation) {
                    requestedPage = null
                    navigationJob = null
                    velocity = 0f
                }
            }
        }
        navigationJob = job
        job.start()
    }

    fun onDrag() {
        generation++
        navigationJob?.cancel()
        navigationJob = null
        requestedPage = null
        velocity = 0f
    }
}
