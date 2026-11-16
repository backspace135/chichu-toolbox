package com.chichu.toolbox

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.core.content.pm.PackageInfoCompat
import com.chichu.toolbox.core.ksu.CommandResult
import com.chichu.toolbox.core.ksu.KsuProtocol
import com.chichu.toolbox.core.ksu.KsuRepository
import com.chichu.toolbox.core.ksu.RootShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID

/**
 * Full upstream WebUI protocol for one trusted module page.
 *
 * Synchronous APIs (exec without callback, io.*, pm.*) run on the prompt channel:
 * the injected JS calls window.prompt with a "__chichu__" prefix and
 * [ModuleWebUi]'s onJsPrompt forwards here after re-checking the frame origin —
 * no addJavascriptInterface, so Root stays bound to the module's own origin.
 *
 * Asynchronous APIs (exec with callback, spawn) run over the origin-scoped
 * WebMessage bridge. io gives the page the same root filesystem reach that
 * ksu.exec already grants it; the origin check is the boundary. Package APIs
 * use the in-process PackageManager (current user), not a root round trip.
 */
internal class WebUiBridge(
    private val repository: KsuRepository,
    private val context: Context,
    private val moduleId: String,
) : AutoCloseable {
    private var session: RootShellSession? = null
    private val inputs = HashMap<String, InputStream>()
    private val outputs = HashMap<String, OutputStream>()
    private val randoms = HashMap<String, RandomFile>()
    private var closed = false

    private class InputStream(val path: String) {
        var position = 0L
    }

    private class OutputStream(val path: String) {
        val buffer = ByteArrayOutputStream()
    }

    private class RandomFile(val path: String, val writable: Boolean) {
        var data = ByteArray(0)
        var position = 0
        var dirty = false
    }

    private fun shell(): RootShellSession {
        check(!closed) { "WebUI 已关闭" }
        return session ?: RootShellSession.start(repository.rootShellCommand()).also { session = it }
    }

    private fun quote(path: String) = KsuProtocol.quote(path)

    private fun b64Decode(text: String): ByteArray = Base64.getMimeDecoder().decode(text)

    private fun b64Encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Synchronous prompt-channel entry. Returns the raw reply consumed by the injected JS. */
    fun sync(request: String): String {
        val root = JSONObject(request)
        return when (root.getString("op")) {
            "exec" -> shell().exec(root.getString("command")).stdout
            "listPackages", "pm.listPackages" -> listPackages(root.optString("type"))
            "getPackagesInfo", "pm.getPackagesInfo" -> packagesInfo(root.getString("packages"))
            "pm.version", "io.version" -> "1"
            "io.file" -> file(root)
            "io.fis" -> fis(root)
            "io.fos" -> fos(root)
            "io.raf" -> raf(root)
            else -> "null"
        }
    }

    // ---------------------------------------------------------------- pm ----

    private fun listPackages(type: String): String {
        val names = context.packageManager.getInstalledPackages(0)
            .filter { info ->
                val flags = info.applicationInfo?.flags ?: 0
                when (type.lowercase()) {
                    "system" -> flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                    "user" -> flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0
                    else -> true
                }
            }
            .map { it.packageName }
            .distinct()
            .sorted()
        val array = JSONArray()
        names.forEach(array::put)
        return array.toString()
    }

    private fun packagesInfo(packagesJson: String): String {
        val names = JSONArray(packagesJson)
        val array = JSONArray()
        for (index in 0 until names.length()) {
            val name = names.getString(index)
            try {
                val info = context.packageManager.getPackageInfo(name, 0)
                val app = info.applicationInfo
                array.put(JSONObject()
                    .put("packageName", info.packageName)
                    .put("versionName", info.versionName ?: "")
                    .put("versionCode", PackageInfoCompat.getLongVersionCode(info))
                    .put("appLabel", app?.loadLabel(context.packageManager)?.toString() ?: "")
                    .put("isSystem", app?.let { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
                        ?: JSONObject.NULL)
                    .put("uid", app?.uid ?: JSONObject.NULL)
                    .put("userId", app?.let { it.uid / 100000 } ?: JSONObject.NULL))
            } catch (_: PackageManager.NameNotFoundException) {
                array.put(JSONObject().put("packageName", name).put("error", "Package not found or inaccessible"))
            }
        }
        return array.toString()
    }

    // ------------------------------------------------------------ io.file ----

    private fun file(root: JSONObject): String {
        val path = root.getString("path")
        val method = root.getString("method")
        // Pure java.io.File string ops need no root round trip.
        when (method) {
            "getAbsolutePath" -> return File(path).absolutePath
            "getPath" -> return path
            "getName" -> return File(path).name
            "toString" -> return path
            "isHidden" -> return File(path).isHidden.toString()
            "getParent" -> return File(path).parent?.let { JSONObject.quote(it) } ?: "null"
        }
        val q = quote(path)
        fun run(script: String): RootShellSession.Result = shell().exec(script)
        fun bool(script: String): String = if (run(script).stdout.trim() == "true") "true" else "false"
        return when (method) {
            "exists" -> bool("[ -e $q ] && echo true || echo false")
            "isFile" -> bool("[ -f $q ] && echo true || echo false")
            "isDirectory" -> bool("[ -d $q ] && echo true || echo false")
            "canRead" -> bool("[ -r $q ] && echo true || echo false")
            "canWrite" -> bool("[ -w $q ] && echo true || echo false")
            "canExecute" -> bool("[ -x $q ] && echo true || echo false")
            "isBlock" -> bool("[ -b $q ] && echo true || echo false")
            "isCharacter" -> bool("[ -c $q ] && echo true || echo false")
            "isSymlink" -> bool("[ -L $q ] && echo true || echo false")
            "createNewFile" -> bool("if [ -e $q ]; then echo false; else ( : > $q ) && echo true || echo false; fi")
            "delete" -> bool("if [ -f $q ]; then rm -f $q && echo true || echo false; elif [ -d $q ]; then rmdir $q 2>/dev/null && echo true || echo false; else echo false; fi")
            "deleteRecursive" -> bool("rm -rf $q && echo true || echo false")
            "mkdir" -> bool("mkdir $q && echo true || echo false")
            "mkdirs" -> bool("mkdir -p $q && echo true || echo false")
            "renameTo" -> bool("mv $q ${quote(root.getString("destPath"))} && echo true || echo false")
            "length" -> run("stat -c %s $q").stdout.trim().toLongOrNull()?.toString() ?: "-1"
            "lastModified" -> run("stat -c %Y $q").stdout.trim().toLongOrNull()
                ?.let { (it * 1000).toString() } ?: "-1"
            "setLastModified" -> bool("touch -d @${root.getLong("time") / 1000} $q && echo true || echo false")
            "getCanonicalPath" -> run("realpath $q").stdout.trim().ifEmpty { File(path).absolutePath }
            "createNewSymlink" -> bool("ln -s ${quote(root.getString("target"))} $q && echo true || echo false")
            "createNewLink" -> bool("ln ${quote(root.getString("existing"))} $q && echo true || echo false")
            "clear" -> bool("( : > $q ) && echo true || echo false")
            "setReadOnly" -> bool("chmod a-w $q && echo true || echo false")
            "setReadable" -> bool(chmod(root, "r"))
            "setWritable" -> bool(chmod(root, "w"))
            "setExecutable" -> bool(chmod(root, "x"))
            "getFreeSpace" -> space(q, 0)
            "getTotalSpace" -> space(q, 1)
            "getUsableSpace" -> space(q, 2)
            "list" -> listing(q, prefix = null)
            "listFiles" -> listing(q, prefix = path)
            "newInputStream" -> newInputStreamId(path)
            "newOutputStream" -> newOutputStreamId(path, root.optBoolean("append"))
            else -> "null"
        }
    }

    private fun chmod(root: JSONObject, letter: String): String {
        val who = if (root.optBoolean("ownerOnly")) "u" else "a"
        val sign = if (root.optBoolean("value")) "+" else "-"
        return "chmod $who$sign$letter ${quote(root.getString("path"))} && echo true || echo false"
    }

    /** stat -f gives [free blockSize total available]; -1 on failure like java.io.File. */
    private fun space(q: String, which: Int): String {
        val parts = shell().exec("stat -f -c '%f %s %b %a' $q").stdout.trim().split(Regex("\\s+"))
        if (parts.size != 4) return "-1"
        val values = parts.mapNotNull { it.toLongOrNull() }
        if (values.size != 4) return "-1"
        val bytes = when (which) {
            0 -> values[0] * values[1]
            1 -> values[2] * values[1]
            else -> values[3] * values[1]
        }
        return bytes.toString()
    }

    private fun listing(q: String, prefix: String?): String {
        val result = shell().exec("( ls -A $q )")
        if (result.exitCode != 0) return "[]"
        val array = JSONArray()
        result.stdout.lineSequence().filter { it.isNotEmpty() }
            .forEach { array.put(if (prefix == null) it else "$prefix/$it") }
        return array.toString()
    }

    // ------------------------------------------------------- io streams ----

    private fun newInputStreamId(path: String): String {
        if (inputs.size + outputs.size >= 50) return ""
        if (shell().exec("[ -e ${quote(path)} ] && echo true || echo false").stdout.trim() != "true") return ""
        val id = UUID.randomUUID().toString()
        inputs[id] = InputStream(path)
        return id
    }

    private fun newOutputStreamId(path: String, append: Boolean): String {
        if (inputs.size + outputs.size >= 50) return ""
        if (shell().exec("[ -e ${quote(path)} ] && echo true || echo false").stdout.trim() != "true") return ""
        if (!append && shell().exec("( : > ${quote(path)} )").exitCode != 0) return ""
        val id = UUID.randomUUID().toString()
        outputs[id] = OutputStream(path)
        return id
    }

    private fun fis(root: JSONObject): String {
        val method = root.getString("method")
        if (method == "open") return newInputStreamId(root.getString("path"))
        val id = root.getString("id")
        val stream = inputs[id] ?: return if (method == "available") "0" else if (method == "read") "" else "false"
        val q = quote(stream.path)
        return when (method) {
            "read" -> {
                val max = if (root.has("maxBytes")) root.getInt("maxBytes").coerceIn(1, 64 * 1024) else 8 * 1024
                val result = shell().exec("tail -c +${stream.position + 1} $q | head -c $max | base64")
                val bytes = if (result.stdout.isBlank()) ByteArray(0) else b64Decode(result.stdout)
                stream.position += bytes.size
                if (bytes.isEmpty()) "" else result.stdout
            }
            "available" -> ((shell().exec("stat -c %s $q").stdout.trim().toLongOrNull() ?: 0L)
                - stream.position).coerceAtLeast(0).toString()
            "close" -> if (inputs.remove(id) != null) "true" else "false"
            else -> "null"
        }
    }

    private fun fos(root: JSONObject): String {
        val method = root.getString("method")
        if (method == "open") return newOutputStreamId(root.getString("path"), root.optBoolean("append"))
        val id = root.getString("id")
        val stream = outputs[id] ?: return "false"
        return when (method) {
            "write" -> {
                stream.buffer.write(b64Decode(root.getString("data")))
                "true"
            }
            "writeByte" -> {
                stream.buffer.write(root.getInt("b"))
                "true"
            }
            "flush" -> if (flushOutput(stream)) "true" else "false"
            "close" -> {
                val flushed = if (stream.buffer.size() > 0) flushOutput(stream) else true
                outputs.remove(id)
                if (flushed) "true" else "false"
            }
            else -> "null"
        }
    }

    private fun flushOutput(stream: OutputStream): Boolean {
        if (stream.buffer.size() == 0) return true
        val data = stream.buffer.toByteArray()
        val result = shell().exec("printf %s '${b64Encode(data)}' | base64 -d >> ${quote(stream.path)}")
        if (result.exitCode != 0) return false
        stream.buffer.reset()
        return true
    }

    // -------------------------------------------------- io.RandomAccess ----

    private fun raf(root: JSONObject): String {
        val method = root.getString("method")
        if (method == "open") {
            val path = root.getString("path")
            val mode = root.getString("mode")
            if (!Regex("r(w|ws|wd)?").matches(mode)) return ""
            val writable = mode != "r"
            if (!writable || shell().exec("[ -f ${quote(path)} ] && echo true || echo false").stdout.trim() == "true") {
                val loaded = if (writable && shell().exec("[ -f ${quote(path)} ] && echo true || echo false").stdout.trim() != "true") {
                    ByteArray(0)
                } else {
                    b64Decode(shell().exec("base64 ${quote(path)}").stdout)
                }
                val id = UUID.randomUUID().toString()
                randoms[id] = RandomFile(path, writable).apply { data = loaded }
                return id
            }
            return ""
        }
        val id = root.getString("id")
        val file = randoms[id] ?: return "null"
        fun readBytes(count: Int): ByteArray? {
            if (file.position + count > file.data.size) return null
            val result = file.data.copyOfRange(file.position, file.position + count)
            file.position += count
            return result
        }
        fun writeBytes(chunk: ByteArray) {
            val end = file.position + chunk.size
            if (end > file.data.size) file.data = file.data.copyOf(end)
            chunk.copyInto(file.data, file.position)
            file.position = end
            file.dirty = true
        }
        return when (method) {
            "read" -> readBytes(1)?.get(0)?.toInt()?.toString() ?: "-1"
            "readBytes" -> {
                val chunk = readBytes(root.getInt("len").coerceAtLeast(0)) ?: ByteArray(0)
                JSONObject.quote(b64Encode(chunk))
            }
            "readBoolean" -> readBytes(1)?.get(0)?.let { (it.toInt() != 0).toString() } ?: "null"
            "readByte" -> readBytes(1)?.get(0)?.toInt()?.toString() ?: "null"
            "readShort" -> readBytes(2)?.let { ((it[0].toInt() shl 8) or (it[1].toInt() and 0xff)).toString() } ?: "null"
            "readInt" -> readBytes(4)?.let { buffer ->
                ((buffer[0].toInt() shl 24) or (buffer[1].toInt() and 0xff shl 16) or
                    (buffer[2].toInt() and 0xff shl 8) or (buffer[3].toInt() and 0xff)).toString()
            } ?: "null"
            "readLong" -> readBytes(8)?.let { buffer ->
                var value = 0L
                buffer.forEach { value = (value shl 8) or (it.toLong() and 0xff) }
                value.toString()
            } ?: "null"
            "readFloat" -> readBytes(4)?.let { buffer ->
                Float.fromBits((buffer[0].toInt() shl 24) or (buffer[1].toInt() and 0xff shl 16) or
                    (buffer[2].toInt() and 0xff shl 8) or (buffer[3].toInt() and 0xff)).toString()
            } ?: "null"
            "readDouble" -> readBytes(8)?.let { buffer ->
                var bits = 0L
                buffer.forEach { bits = (bits shl 8) or (it.toLong() and 0xff) }
                Double.fromBits(bits).toString()
            } ?: "null"
            "readUTF" -> readModifiedUtf8(file)?.let { JSONObject.quote(it) } ?: "null"
            "readLine" -> readLine(file)?.let { JSONObject.quote(it) } ?: "null"
            "write" -> { writeBytes(byteArrayOf(root.getInt("b").toByte())); "null" }
            "writeBase64" -> { writeBytes(b64Decode(root.getString("data"))); "null" }
            "writeBoolean" -> { writeBytes(byteArrayOf(if (root.getBoolean("v")) 1 else 0)); "null" }
            "writeByte" -> { writeBytes(byteArrayOf(root.getInt("v").toByte())); "null" }
            "writeShort" -> {
                val v = root.getInt("v")
                writeBytes(byteArrayOf((v shr 8).toByte(), v.toByte())); "null"
            }
            "writeInt" -> {
                val v = root.getInt("v")
                writeBytes(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())); "null"
            }
            "writeLong" -> {
                var v = root.getLong("v")
                val out = ByteArray(8)
                for (i in 7 downTo 0) { out[i] = (v and 0xff).toByte(); v = v shr 8 }
                writeBytes(out); "null"
            }
            "writeFloat" -> { writeBytes(intToBytes(root.getDouble("v").toFloat().toRawBits())); "null" }
            "writeDouble" -> { writeBytes(longToBytes(root.getDouble("v").toRawBits())); "null" }
            "writeUTF" -> { writeBytes(encodeModifiedUtf8(root.getString("str"))); "null" }
            "seek" -> { file.position = root.getInt("pos").coerceAtLeast(0); "true" }
            "getFilePointer" -> file.position.toString()
            "length" -> file.data.size.toString()
            "setLength" -> {
                if (!file.writable) return "false"
                val length = root.getInt("newLength")
                file.data = if (length > file.data.size) file.data.copyOf(length) else file.data.copyOf(length.coerceAtLeast(0))
                file.dirty = true
                "true"
            }
            "close" -> {
                val ok = if (file.dirty && file.writable) {
                    shell().exec("printf %s '${b64Encode(file.data)}' | base64 -d > ${quote(file.path)}").exitCode == 0
                } else true
                randoms.remove(id)
                if (ok) "true" else "false"
            }
            else -> "null"
        }
    }

    private fun intToBytes(value: Int): ByteArray =
        byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun longToBytes(value: Long): ByteArray {
        val out = ByteArray(8)
        var v = value
        for (i in 7 downTo 0) { out[i] = (v and 0xff).toByte(); v = v shr 8 }
        return out
    }

    /** Java DataInput.readUTF: two length bytes then modified UTF-8; null at truncation. */
    private fun readModifiedUtf8(file: RandomFile): String? {
        if (file.position + 2 > file.data.size) return null
        val length = ((file.data[file.position].toInt() and 0xff) shl 8) or (file.data[file.position + 1].toInt() and 0xff)
        if (length < 0 || file.position + 2 + length > file.data.size) return null
        var index = file.position + 2
        val end = index + length
        val builder = StringBuilder()
        while (index < end) {
            val b = file.data[index].toInt() and 0xff
            when {
                b < 0x80 -> { builder.append(b.toChar()); index += 1 }
                b and 0xe0 == 0xc0 -> {
                    if (index + 1 >= end) return null
                    builder.append((((b and 0x1f) shl 6) or (file.data[index + 1].toInt() and 0x3f)).toChar()); index += 2
                }
                else -> {
                    if (index + 2 >= end) return null
                    builder.append((((b and 0x0f) shl 12) or ((file.data[index + 1].toInt() and 0x3f) shl 6)
                        or (file.data[index + 2].toInt() and 0x3f)).toChar()); index += 3
                }
            }
        }
        file.position = end
        return builder.toString()
    }

    private fun readLine(file: RandomFile): String? {
        if (file.position >= file.data.size) return null
        val builder = StringBuilder()
        while (file.position < file.data.size) {
            val byte = file.data[file.position]
            file.position += 1
            if (byte == '\n'.code.toByte()) break
            builder.append(byte.toInt().toChar())
        }
        return builder.toString()
    }

    private fun encodeModifiedUtf8(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (character in value) {
            val code = character.code
            when {
                code != 0 && code <= 0x7f -> out.write(code)
                code <= 0x7ff -> {
                    out.write(0xc0 or (code shr 6)); out.write(0x80 or (code and 0x3f))
                }
                else -> {
                    out.write(0xe0 or (code shr 12)); out.write(0x80 or ((code shr 6) and 0x3f))
                    out.write(0x80 or (code and 0x3f))
                }
            }
        }
        val body = out.toByteArray()
        check(body.size < 65536) { "UTF 数据超长" }
        return byteArrayOf((body.size shr 8).toByte(), body.size.toByte()) + body
    }

    // --------------------------------------------------------- async ----

    /**
     * Streaming spawn: per-line stdout/stderr data events, then exit, then error
     * for nonzero codes — the same window[name] contract as upstream.
     */
    suspend fun spawnAsync(command: String, argsJson: String, optionsJson: String?,
        callback: String, view: WebView, alive: () -> Boolean) = withContext(Dispatchers.IO) {
        val options = if (optionsJson != null) JSONObject(optionsJson) else JSONObject()
        val cwd = options.optString("cwd")
        val base = if (cwd.isNotEmpty()) cwd else "/data/adb/modules/${KsuProtocol.moduleId(moduleId)}"
        val script = StringBuilder("if [ \"\$(id -u)\" != 0 ]; then exit 126; fi\ncd ${KsuProtocol.quote(base)} && ")
        options.optJSONObject("env")?.let { env ->
            env.keys().forEach { key -> script.append("export $key=${KsuProtocol.quote(env.getString(key))}; ") }
        }
        script.append(command)
        if (argsJson.isNotBlank()) {
            val args = JSONArray(argsJson)
            for (index in 0 until args.length()) script.append(" ").append(args.getString(index))
        }
        script.append("\nexit\n")
        val process = ProcessBuilder(repository.rootShellCommand()).start()
        val stderrLines = ArrayList<String>()
        fun emit(js: String) {
            if (alive()) view.post { if (alive()) view.evaluateJavascript(js, null) }
        }
        fun emitData(name: String, line: String) {
            emit("(function(){try{window[${JSONObject.quote(callback)}].$name.emit('data', ${JSONObject.quote(line)});}catch(e){console.error(e);}})()")
        }
        try {
            coroutineScope {
                val stdoutJob = async {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { emitData("stdout", it) }
                    }
                }
                val stderrJob = async {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> stderrLines.add(line); emitData("stderr", line) }
                    }
                }
                try {
                    process.outputStream.bufferedWriter().use { it.write(script.toString()) }
                } catch (_: IOException) {
                    // The process may exit before stdin is fully consumed.
                }
                stdoutJob.join()
                stderrJob.join()
            }
            val code = runInterruptible { process.waitFor() }
            emit("(function(){try{window[${JSONObject.quote(callback)}].emit('exit', $code);}catch(e){console.error(e);}})()")
            if (code != 0) {
                val message = JSONObject.quote(stderrLines.joinToString("\n"))
                emit("(function(){try{var err=new Error();err.exitCode=$code;err.message=$message;" +
                    "window[${JSONObject.quote(callback)}].emit('error',err);}catch(e){console.error(e);}})()")
            }
        } finally {
            process.destroy()
        }
    }

    override fun close() {
        // Mirror upstream KsuIO.destroy(): pending output buffers reach the file.
        outputs.values.forEach { stream -> if (stream.buffer.size() > 0) flushOutput(stream) }
        randoms.entries.removeIf { (id, file) ->
            if (file.dirty && file.writable) {
                shell().exec("printf %s '${b64Encode(file.data)}' | base64 -d > ${quote(file.path)}")
            }
            true
        }
        closed = true
        outputs.clear()
        inputs.clear()
        session?.close()
        session = null
    }
}
