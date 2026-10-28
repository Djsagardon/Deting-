package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

private const val TAG = "SafeWebView"
private const val MAX_RENDERER_RETRY_COUNT = 3

/**
 * Crash-resilient Android WebView component that intercepts and resolves
 * Chromium "Aw, Snap!" renderer process termination (onRenderProcessGone),
 * memory pressure, and navigation failures.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafeWebView(
    url: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isRendererCrashed by remember { mutableStateOf(false) }
    var crashErrorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Lifecycle cleanup
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                    Log.d(TAG, "WebView safely destroyed and memory freed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying WebView: ${e.message}")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalDark)
    ) {
        if (isRendererCrashed) {
            // High-fidelity Recovery Screen replacing Chromium "Aw, Snap!"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(CharcoalCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Renderer issue",
                        tint = BondlyPink,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Webpage Display Notice",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = crashErrorMessage ?: "The web rendering process encountered a memory limitation on this device and was safely recovered.",
                    color = TextLightGrey,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (retryCount < MAX_RENDERER_RETRY_COUNT) {
                    Button(
                        onClick = {
                            retryCount++
                            isRendererCrashed = false
                            crashErrorMessage = null
                            isLoading = true
                            webViewInstance?.let { wv ->
                                try {
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    wv.destroy()
                                } catch (_: Exception) {}
                            }
                            webViewInstance = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Reload Page", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        text = "Multiple reload attempts exhausted. Please check your device memory or try again later.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (onBack != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = "Go Back", color = TextLightGrey)
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Strict Security & Memory-Optimized Settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    Log.w(TAG, "WebView page load error: ${error?.description}")
                                }
                            }

                            /**
                             * CRITICAL FIX FOR CHROMIUM "AW, SNAP!" (API 26+)
                             * Returning true informs the OS that the app handled the crash
                             * and prevents Android from killing the entire application.
                             */
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                val didCrash = detail?.didCrash() ?: false
                                val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    detail?.rendererPriorityAtExit()
                                } else null

                                Log.e(
                                    TAG,
                                    "Chromium renderer process gone. didCrash=$didCrash, priorityAtExit=$priority"
                                )

                                isRendererCrashed = true
                                crashErrorMessage = if (didCrash) {
                                    "The webpage renderer crashed due to an internal browser engine fault. You can safely reload the page."
                                } else {
                                    "The system reclaimed browser memory while rendering this webpage. Tap reload to restart."
                                }

                                // Remove the defunct WebView from layout hierarchy
                                view?.let { wv ->
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    try {
                                        wv.destroy()
                                    } catch (_: Exception) {}
                                }
                                webViewInstance = null

                                // Return true: Handled! Do NOT terminate the host app process!
                                return true
                            }
                        }

                        loadUrl(url)
                    }
                },
                update = { wv ->
                    // Keep URL updated if retry triggered
                    if (!isRendererCrashed && wv.url != url) {
                        wv.loadUrl(url)
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BondlyPink, strokeWidth = 2.5.dp)
                }
            }
        }
    }
}
