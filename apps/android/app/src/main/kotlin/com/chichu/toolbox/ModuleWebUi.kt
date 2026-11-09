package com.chichu.toolbox

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chichu.toolbox.core.ksu.KsuProtocol
import com.chichu.toolbox.core.ksu.KsuRepository
import com.chichu.toolbox.core.ksu.RootModule
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.cancelChildren

/** Origin-scoped messages replace addJavascriptInterface, which would expose Root to frames. */
// supported is checked before AndroidView creation; onRelease only runs for that created view.
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
@Composable
internal fun ModuleWebUi(module: RootModule, repository: KsuRepository, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose {
            active = false
            scope.coroutineContext.cancelChildren()
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    val supported = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }
    DetailSheet(true, "${module.name} · WebUI", close) {
        if (!supported) Hint("当前 Android System WebView 不支持按来源隔离的消息桥，请更新系统 WebView 后重试。")
        error?.let { Hint(it) }
        if (supported && !rendererGone) AndroidView(
            modifier = Modifier.fillMaxWidth().height(400.dp),
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
                    val info = JSONObject().put("id", module.id).put("name", module.name).put("version", module.version).toString()
                    WebViewCompat.addWebMessageListener(this, "NativeRoot", setOf(origin)) { view, message, source, mainFrame, _ ->
                        // Trust is scoped to this explicitly approved WebView and its local main document.
                        if (!KsuProtocol.bridgeAllowed(source.toString(), mainFrame, active, host)) return@addWebMessageListener
                        val data = message.data
                        if (data == null) { error = "WebUI 消息必须为文本"; return@addWebMessageListener }
                        val request = try { JSONObject(data) } catch (failure: JSONException) {
                            error = "WebUI 消息格式错误：${failure.message}"; return@addWebMessageListener
                        }
                        val callback = request.optString("callback")
                        if (!Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(callback)) {
                            error = "WebUI 回调名无效"; return@addWebMessageListener
                        }
                        val command = try { request.getString("command") } catch (failure: JSONException) {
                            error = "WebUI 缺少命令：${failure.message}"; return@addWebMessageListener
                        }
                        scope.launch {
                            try {
                                val result = repository.webExec(module.id, command)
                                if (active) view.evaluateJavascript("window[${JSONObject.quote(callback)}](${result.code},${JSONObject.quote(result.output)},${JSONObject.quote(result.stderr)})", null)
                            } catch (failure: IOException) {
                                error = failure.message
                                if (active) view.evaluateJavascript("window[${JSONObject.quote(callback)}](-1,'',${JSONObject.quote(failure.message)})", null)
                            }
                        }
                    }
                    WebViewCompat.addDocumentStartJavaScript(this, """
                        if (window === window.top) {
                          window.ksu = {
                            exec: function(command, options, callback) {
                              var opts = JSON.parse(options);
                              if (Object.keys(opts).length) throw new Error('exec options are not supported by this host');
                              NativeRoot.postMessage(JSON.stringify({command:command,callback:callback}));
                            },
                            moduleInfo: function() { return ${JSONObject.quote(info)}; },
                            toast: function(message) { console.info(message); }
                          };
                        }
                    """.trimIndent(), setOf(origin))
                    webViewClient = object : WebViewClient() {
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
                                view.post { error = failure.message }
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

private fun response(code: Int, reason: String, mime: String, bytes: ByteArray) = WebResourceResponse(
    mime, "UTF-8", code, reason, mapOf(
        "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'none'; child-src 'none'; object-src 'none'; base-uri 'none'; connect-src 'self'",
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "no-store",
    ), ByteArrayInputStream(bytes),
)
