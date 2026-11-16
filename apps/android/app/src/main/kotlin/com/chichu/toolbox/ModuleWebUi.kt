package com.chichu.toolbox

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JsPromptResult
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chichu.toolbox.core.ksu.KsuProtocol
import com.chichu.toolbox.core.ksu.KsuRepository
import com.chichu.toolbox.core.ksu.RootModule
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelChildren
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Module WebUI with the complete upstream JS protocol.
 *
 * Two origin-scoped channels, no addJavascriptInterface:
 * - "NativeRoot" WebMessage listener (main document only) for the callback APIs:
 *   exec (with cwd/env options), spawn (streaming), toast, fullScreen, exit.
 * - window.prompt with a "__chichu__" prefix for the synchronous APIs:
 *   exec without callback, listPackages, getPackagesInfo, io.*, pm.*.
 *   onJsPrompt re-checks the calling frame's URL against this module's origin.
 */
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
@Composable
internal fun ModuleWebUi(module: RootModule, repository: KsuRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(true) }
    var barsHidden by remember { mutableStateOf(false) }
    val bridge = remember(module.id) { WebUiBridge(repository, context, module.id) }
    DisposableEffect(module.id) {
        onDispose {
            active = false
            scope.coroutineContext.cancelChildren()
            bridge.close()
            if (barsHidden) restoreSystemBars(context)
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    val supported = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("${module.name} · 仅本次打开允许 Root")
        if (!supported) Hint("当前 Android System WebView 不支持按来源隔离的消息桥，请更新系统 WebView 后重试。")
        error?.let { Hint(it) }
        val webHeight = (LocalWindowInfo.current.containerDpSize.height - 280.dp * LocalDensity.current.fontScale).coerceIn(120.dp, 520.dp)
        if (supported && !rendererGone) AndroidView(
            modifier = Modifier.fillMaxWidth().height(webHeight),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    val host = KsuProtocol.webHost(module.id)
                    val origin = "https://$host"
                    val info = JSONObject()
                        .put("moduleDir", "/data/adb/modules/${module.id}")
                        .put("id", module.id).put("name", module.name).put("version", module.version)
                        .put("description", module.description).put("enabled", module.enabled)
                        .put("update", module.update).put("remove", module.remove)
                        .put("web", module.web).put("action", module.action).toString()
                    WebViewCompat.addWebMessageListener(this, "NativeRoot", setOf(origin)) { view, message, source, mainFrame, _ ->
                        // Trust is scoped to this explicitly approved WebView and its local main document.
                        if (!KsuProtocol.bridgeAllowed(source.toString(), mainFrame, active, host)) return@addWebMessageListener
                        val data = message.data
                        if (data == null) { error = "WebUI 消息必须为文本"; return@addWebMessageListener }
                        val request = try { JSONObject(data) } catch (failure: JSONException) {
                            error = "WebUI 消息格式错误：${failure.message}"; return@addWebMessageListener
                        }
                        val callback = request.optString("callback")
                        when (request.optString("op", "exec")) {
                            "exec" -> {
                                if (!Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(callback)) {
                                    error = "WebUI 回调名无效"; return@addWebMessageListener
                                }
                                val command = try { request.getString("command") } catch (failure: JSONException) {
                                    error = "WebUI 缺少命令：${failure.message}"; return@addWebMessageListener
                                }
                                val options = request.optString("options").ifBlank { null }
                                scope.launch {
                                    try {
                                        val result = repository.webExec(module.id, command, options)
                                        if (active) view.evaluateJavascript(
                                            "window[${JSONObject.quote(callback)}](${result.code},${JSONObject.quote(result.output)},${JSONObject.quote(result.stderr)})", null)
                                    } catch (failure: IOException) {
                                        error = failure.message
                                        if (active) view.evaluateJavascript(
                                            "window[${JSONObject.quote(callback)}](-1,'',${JSONObject.quote(failure.message)})", null)
                                    }
                                }
                            }
                            "spawn" -> {
                                if (!Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(callback)) {
                                    error = "WebUI 回调名无效"; return@addWebMessageListener
                                }
                                val command = try { request.getString("command") } catch (failure: JSONException) {
                                    error = "WebUI 缺少命令：${failure.message}"; return@addWebMessageListener
                                }
                                scope.launch {
                                    try {
                                        bridge.spawnAsync(command, request.optString("args").ifBlank { "[]" },
                                            request.optString("options").ifBlank { null }, callback, view) { active }
                                    } catch (failure: IOException) {
                                        error = failure.message
                                    }
                                }
                            }
                            "toast" -> view.post {
                                Toast.makeText(context, request.optString("message"), Toast.LENGTH_SHORT).show()
                            }
                            "fullScreen" -> view.post {
                                val enabled = request.optBoolean("enabled")
                                (view.context as? Activity)?.window?.let { window ->
                                    setBars(window, enabled)
                                    barsHidden = enabled
                                }
                            }
                            "edgeToEdge" -> {
                                // The embedded sheet already lays out inside system bar insets.
                            }
                            "exit" -> view.post(onClose)
                            else -> error = "未知 WebUI 操作：${request.optString("op")}"
                        }
                    }
                    WebViewCompat.addDocumentStartJavaScript(this, """
                        if (window === window.top) {
                          var __chichuSeq = 0;
                          function __chichuCall(payload) {
                            return prompt('__chichu__' + JSON.stringify(payload));
                          }
                          function __chichuParse(reply) {
                            try { return JSON.parse(reply); } catch (error) { return null; }
                          }
                          window.ksu = {
                            exec: function(command, options, callback) {
                              if (typeof options === 'function') { callback = options; options = null; }
                              if (typeof callback === 'function') {
                                var name = '__chichu_exec_' + (++__chichuSeq);
                                window[name] = function(errno, stdout, stderr) { delete window[name]; callback(errno, stdout, stderr); };
                                callback = name;
                              }
                              if (typeof callback === 'string' && callback.length) {
                                NativeRoot.postMessage(JSON.stringify({op: 'exec', command: String(command),
                                  options: typeof options === 'string' ? options : JSON.stringify(options || {}),
                                  callback: callback}));
                                return;
                              }
                              return __chichuCall({op: 'exec', command: String(command)});
                            },
                            spawn: function(command, args, options, callback) {
                              var cb;
                              if (typeof callback === 'string' && callback.length) {
                                cb = callback;
                              } else {
                                function Stdio() { this.listeners = {}; }
                                Stdio.prototype.on = function(event, listener) {
                                  if (!this.listeners[event]) { this.listeners[event] = []; }
                                  this.listeners[event].push(listener);
                                };
                                Stdio.prototype.emit = function(event) {
                                  var evArgs = Array.prototype.slice.call(arguments, 1);
                                  if (this.listeners[event]) { this.listeners[event].forEach(function(l) { l.apply(null, evArgs); }); }
                                };
                                function ChildProcess() {
                                  this.listeners = {};
                                  this.stdout = new Stdio();
                                  this.stderr = new Stdio();
                                }
                                ChildProcess.prototype.on = function(event, listener) {
                                  if (!this.listeners[event]) { this.listeners[event] = []; }
                                  this.listeners[event].push(listener);
                                };
                                ChildProcess.prototype.emit = function(event) {
                                  var evArgs = Array.prototype.slice.call(arguments, 1);
                                  if (this.listeners[event]) { this.listeners[event].forEach(function(l) { l.apply(null, evArgs); }); }
                                };
                                var name = '__chichu_spawn_' + (++__chichuSeq);
                                var child = new ChildProcess();
                                child.on('exit', function() { delete window[name]; });
                                window[name] = child;
                                if (typeof callback === 'function') { child.on('exit', callback); }
                                cb = name;
                                NativeRoot.postMessage(JSON.stringify({op: 'spawn', command: String(command),
                                  args: typeof args === 'string' ? args : JSON.stringify(args || []),
                                  options: typeof options === 'string' ? options : JSON.stringify(options || {}),
                                  callback: cb}));
                                return child;
                              }
                              NativeRoot.postMessage(JSON.stringify({op: 'spawn', command: String(command),
                                args: typeof args === 'string' ? args : JSON.stringify(args || []),
                                options: typeof options === 'string' ? options : JSON.stringify(options || {}),
                                callback: cb}));
                            },
                            toast: function(message) { NativeRoot.postMessage(JSON.stringify({op: 'toast', message: String(message)})); },
                            fullScreen: function(enabled) { NativeRoot.postMessage(JSON.stringify({op: 'fullScreen', enabled: !!enabled})); },
                            enableEdgeToEdge: function(enabled) { NativeRoot.postMessage(JSON.stringify({op: 'edgeToEdge', enabled: !!enabled})); },
                            moduleInfo: function() { return ${JSONObject.quote(info)}; },
                            listPackages: function(type) { return __chichuCall({op: 'listPackages', type: String(type === undefined ? '' : type)}); },
                            getPackagesInfo: function(packages) {
                              return __chichuCall({op: 'getPackagesInfo', packages: typeof packages === 'string' ? packages : JSON.stringify(packages)});
                            },
                            exit: function() { NativeRoot.postMessage(JSON.stringify({op: 'exit'})); },
                            io: function() {
                              function fileCall(method, path, extra, raw) {
                                var payload = {op: 'io.file', method: method, path: String(path)};
                                for (var key in extra || {}) { payload[key] = extra[key]; }
                                var reply = __chichuCall(payload);
                                return raw ? reply : __chichuParse(reply);
                              }
                              return {
                                version: function() { return 1; },
                                File: function(path) {
                                  return {
                                    exists: function() { return fileCall('exists', path); },
                                    isFile: function() { return fileCall('isFile', path); },
                                    isDirectory: function() { return fileCall('isDirectory', path); },
                                    canRead: function() { return fileCall('canRead', path); },
                                    canWrite: function() { return fileCall('canWrite', path); },
                                    canExecute: function() { return fileCall('canExecute', path); },
                                    createNewFile: function() { return fileCall('createNewFile', path); },
                                    delete: function() { return fileCall('delete', path); },
                                    deleteRecursive: function() { return fileCall('deleteRecursive', path); },
                                    mkdir: function() { return fileCall('mkdir', path); },
                                    mkdirs: function() { return fileCall('mkdirs', path); },
                                    renameTo: function(destPath) { return fileCall('renameTo', path, {destPath: destPath}); },
                                    list: function() { return fileCall('list', path, null, true) || '[]'; },
                                    listFiles: function() { return fileCall('listFiles', path, null, true) || '[]'; },
                                    length: function() { return fileCall('length', path); },
                                    lastModified: function() { return fileCall('lastModified', path); },
                                    setLastModified: function(time) { return fileCall('setLastModified', path, {time: time}); },
                                    getAbsolutePath: function() { return fileCall('getAbsolutePath', path, null, true); },
                                    getCanonicalPath: function() { return fileCall('getCanonicalPath', path, null, true); },
                                    getParent: function() { return fileCall('getParent', path); },
                                    getPath: function() { return fileCall('getPath', path, null, true); },
                                    getName: function() { return fileCall('getName', path, null, true); },
                                    isHidden: function() { return fileCall('isHidden', path); },
                                    isBlock: function() { return fileCall('isBlock', path); },
                                    isCharacter: function() { return fileCall('isCharacter', path); },
                                    isSymlink: function() { return fileCall('isSymlink', path); },
                                    createNewSymlink: function(target) { return fileCall('createNewSymlink', path, {target: target}); },
                                    createNewLink: function(existing) { return fileCall('createNewLink', path, {existing: existing}); },
                                    clear: function() { return fileCall('clear', path); },
                                    setReadOnly: function() { return fileCall('setReadOnly', path); },
                                    setReadable: function(readable, ownerOnly) { return fileCall('setReadable', path, {value: readable, ownerOnly: ownerOnly}); },
                                    setWritable: function(writable, ownerOnly) { return fileCall('setWritable', path, {value: writable, ownerOnly: ownerOnly}); },
                                    setExecutable: function(executable, ownerOnly) { return fileCall('setExecutable', path, {value: executable, ownerOnly: ownerOnly}); },
                                    getFreeSpace: function() { return fileCall('getFreeSpace', path); },
                                    getTotalSpace: function() { return fileCall('getTotalSpace', path); },
                                    getUsableSpace: function() { return fileCall('getUsableSpace', path); },
                                    newInputStream: function() { return fileCall('newInputStream', path, null, true); },
                                    newOutputStream: function(append) { return fileCall('newOutputStream', path, {append: append}, true); },
                                    toString: function() { return fileCall('toString', path, null, true); }
                                  };
                                },
                                FileInputStream: function() {
                                  return {
                                    open: function(path) { return __chichuCall({op: 'io.fis', method: 'open', path: String(path)}); },
                                    read: function(id, maxBytes) { return __chichuCall({op: 'io.fis', method: 'read', id: id, maxBytes: maxBytes}); },
                                    available: function(id) { return __chichuParse(__chichuCall({op: 'io.fis', method: 'available', id: id})) || 0; },
                                    close: function(id) { return __chichuParse(__chichuCall({op: 'io.fis', method: 'close', id: id})) === true; }
                                  };
                                },
                                FileOutputStream: function() {
                                  return {
                                    open: function(path, append) { return __chichuCall({op: 'io.fos', method: 'open', path: String(path), append: !!append}); },
                                    write: function(id, data) { return __chichuParse(__chichuCall({op: 'io.fos', method: 'write', id: id, data: data})) === true; },
                                    writeByte: function(id, b) { return __chichuParse(__chichuCall({op: 'io.fos', method: 'writeByte', id: id, b: b})) === true; },
                                    flush: function(id) { return __chichuParse(__chichuCall({op: 'io.fos', method: 'flush', id: id})) === true; },
                                    close: function(id) { return __chichuParse(__chichuCall({op: 'io.fos', method: 'close', id: id})) === true; }
                                  };
                                },
                                RandomAccessFile: function() {
                                  function rafParse(method, id, extra) {
                                    return __chichuParse(__chichuCall({op: 'io.raf', method: method, id: id}.concat ? Object.assign({op: 'io.raf', method: method, id: id}, extra || {}) : {}));
                                  }
                                  function rafRaw(method, id, extra) {
                                    return __chichuCall(Object.assign({op: 'io.raf', method: method, id: id}, extra || {}));
                                  }
                                  return {
                                    open: function(path, mode) { return rafRaw('open', null, {path: String(path), mode: String(mode)}); },
                                    read: function(id) { return rafParse('read', id); },
                                    readBytes: function(id, len) { return rafRaw('readBytes', id, {len: len}); },
                                    readBoolean: function(id) { return rafParse('readBoolean', id); },
                                    readByte: function(id) { return rafParse('readByte', id); },
                                    readInt: function(id) { return rafParse('readInt', id); },
                                    readLong: function(id) { return rafParse('readLong', id); },
                                    readShort: function(id) { return rafParse('readShort', id); },
                                    readFloat: function(id) { return rafParse('readFloat', id); },
                                    readDouble: function(id) { return rafParse('readDouble', id); },
                                    readUTF: function(id) { return rafParse('readUTF', id); },
                                    readLine: function(id) { return rafParse('readLine', id); },
                                    write: function(id, b) { rafParse('write', id, {b: b}); },
                                    writeBase64: function(id, data) { rafParse('writeBase64', id, {data: data}); },
                                    writeBoolean: function(id, v) { rafParse('writeBoolean', id, {v: v}); },
                                    writeByte: function(id, v) { rafParse('writeByte', id, {v: v}); },
                                    writeInt: function(id, v) { rafParse('writeInt', id, {v: v}); },
                                    writeLong: function(id, v) { rafParse('writeLong', id, {v: v}); },
                                    writeShort: function(id, v) { rafParse('writeShort', id, {v: v}); },
                                    writeFloat: function(id, v) { rafParse('writeFloat', id, {v: v}); },
                                    writeDouble: function(id, v) { rafParse('writeDouble', id, {v: v}); },
                                    writeUTF: function(id, str) { rafParse('writeUTF', id, {str: str}); },
                                    seek: function(id, pos) { return rafParse('seek', id, {pos: pos}) === true; },
                                    getFilePointer: function(id) { return rafParse('getFilePointer', id); },
                                    length: function(id) { return rafParse('length', id); },
                                    setLength: function(id, newLength) { return rafParse('setLength', id, {newLength: newLength}) === true; },
                                    close: function(id) { return rafParse('close', id) === true; }
                                  };
                                }
                              };
                            },
                            pm: function() {
                              return {
                                version: function() { return 1; },
                                listPackages: function(type) { return __chichuCall({op: 'pm.listPackages', type: String(type === undefined ? '' : type)}); },
                                getPackagesInfo: function(packages) {
                                  return __chichuCall({op: 'pm.getPackagesInfo', packages: typeof packages === 'string' ? packages : JSON.stringify(packages)});
                                }
                              };
                            }
                          };
                        }
                    """.trimIndent(), setOf(origin))
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                            if (!message.startsWith("__chichu__")) return false
                            if (!active || KsuProtocol.webPath(url, host) == null) {
                                result.cancel()
                                return true
                            }
                            val reply = try {
                                bridge.sync(message.removePrefix("__chichu__"))
                            } catch (failure: Exception) {
                                Log.w("ModuleWebUi", "WebUI 同步调用失败", failure)
                                "null"
                            }
                            result.confirm(reply)
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                            if (request.isForMainFrame) error = "主文档加载失败：${request.url}\n${failure.description}（${failure.errorCode}）"
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) error = "主文档加载失败：${request.url}\nHTTP ${response.statusCode} ${response.reasonPhrase}"
                        }
                        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                            error = if (detail.didCrash()) "模块 WebUI 渲染进程崩溃，请关闭后重新打开。" else "系统已终止模块 WebUI，请关闭后重新打开。"
                            active = false
                            scope.coroutineContext.cancelChildren()
                            rendererGone = true
                            return true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            KsuProtocol.webPath(request.url.toString(), host) == null

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            val path = KsuProtocol.webPath(request.url.toString(), host)
                                ?: return response(403, "Forbidden", "text/plain", "外部来源已阻止".toByteArray())
                            return try {
                                val data = runBlocking { repository.webAsset(module.id, path) }
                                val mime = when (path.substringAfterLast('.', "")) {
                                    "html" -> "text/html"
                                    "js", "mjs" -> "text/javascript"
                                    "css" -> "text/css"
                                    "json" -> "application/json"
                                    "svg" -> "image/svg+xml"
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "webp" -> "image/webp"
                                    "woff2" -> "font/woff2"
                                    else -> "application/octet-stream"
                                }
                                response(200, "OK", mime, data)
                            } catch (failure: IOException) {
                                if (request.isForMainFrame) view.post { error = "主文档读取失败：${request.url}\n${failure.message}" }
                                response(404, "Not Found", "text/plain", "WebUI 文件读取失败".toByteArray())
                            }
                        }
                    }
                    loadUrl("$origin/index.html")
                }
            },
            onRelease = {
                WebViewCompat.removeWebMessageListener(it, "NativeRoot")
                it.stopLoading()
                it.destroy()
            },
        )
    }
}

private fun setBars(window: android.view.Window, hidden: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        if (hidden) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun restoreSystemBars(context: Context) {
    (context as? Activity)?.window?.let { setBars(it, false) }
}

private fun response(code: Int, reason: String, mime: String, bytes: ByteArray) = WebResourceResponse(
    mime, "UTF-8", code, reason, mapOf(
        "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'none'; child-src 'none'; object-src 'none'; base-uri 'none'; connect-src 'self'",
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "no-store",
    ), ByteArrayInputStream(bytes),
)
