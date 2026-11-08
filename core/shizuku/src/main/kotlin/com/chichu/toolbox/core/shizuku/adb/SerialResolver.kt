package com.chichu.toolbox.core.shizuku.adb

internal class SerialResolver<T>(
    private val resolve: (T, (T?) -> Unit) -> Unit,
    private val publish: (T) -> Unit,
) {
    private data class Request<T>(val key: String, val value: T, val generation: Long, val attempt: Int = 0)
    private val alive = mutableMapOf<String, Long>()
    private val queue = ArrayDeque<Request<T>>()
    private var generation = 0L
    private var active: Request<T>? = null

    @Synchronized fun found(key: String, value: T) {
        if (key in alive) return
        val version = ++generation
        alive[key] = version
        queue.addLast(Request(key, value, version))
        next()
    }

    @Synchronized fun lost(key: String) { alive.remove(key) }

    private fun next() {
        if (active != null) return
        while (queue.isNotEmpty()) {
            val request = queue.removeFirst()
            if (alive[request.key] != request.generation) continue
            active = request
            try { resolve(request.value) { complete(request, it) }
            } catch (_: RuntimeException) { complete(request, null) }
            return
        }
    }

    @Synchronized private fun complete(request: Request<T>, result: T?) {
        if (active !== request) return
        active = null
        if (alive[request.key] == request.generation) {
            if (result != null) publish(result)
            else if (request.attempt < 2) queue.addLast(request.copy(attempt = request.attempt + 1))
        }
        next()
    }
}
