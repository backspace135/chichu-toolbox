package com.chichu.toolbox.core.shizuku.activation

class ActivationGate {
    private var generation = 0L
    private var active: Long? = null
    private var interrupter: (() -> Unit)? = null

    @Synchronized fun begin(): Long? {
        if (active != null) return null
        return (++generation).also { active = it }
    }

    @Synchronized fun isCurrent(operation: Long) = active == operation
    @Synchronized fun register(operation: Long, interrupt: () -> Unit) {
        if (active == operation) interrupter = interrupt else interrupt()
    }
    @Synchronized fun interrupt(operation: Long) {
        if (active == operation) { interrupter?.invoke(); interrupter = null }
    }
    @Synchronized fun finish(operation: Long) {
        if (active == operation) { active = null; interrupter = null }
    }
    @Synchronized fun cancel() {
        interrupter?.invoke()
        interrupter = null
        active = null
        generation++
    }
}
