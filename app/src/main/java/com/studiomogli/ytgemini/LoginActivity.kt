package com.studiomogli.ytgemini

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTH_URL = "extra_auth_url"
        const val DEFAULT_GOOGLE_LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fapp"
    }

    private lateinit var loginWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        loginWebView = findViewById(R.id.loginWebView)
        val btnClose: TextView = findViewById(R.id.btnCloseLogin)
        val btnDone: TextView = findViewById(R.id.btnDoneLogin)

        btnClose.setOnClickListener {
            CookieManager.getInstance().flush()
            setResult(RESULT_CANCELED)
            finish()
        }

        btnDone.setOnClickListener {
            CookieManager.getInstance().flush()
            setResult(RESULT_OK)
            finish()
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(loginWebView, true)

        val realDefaultUa = WebSettings.getDefaultUserAgent(this)
        val cleanMobileChromeUa = realDefaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        loginWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = cleanMobileChromeUa
        }

        loginWebView.webChromeClient = WebChromeClient()
        loginWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (isReturnHost(uri)) {
                    completeLogin()
                    return true
                }
                val scheme = uri.scheme?.lowercase() ?: return true
                return scheme != "https" && scheme != "http"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                if (url != null && isReturnHost(Uri.parse(url))) {
                    completeLogin()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (loginWebView.canGoBack()) loginWebView.goBack()
                else {
                    CookieManager.getInstance().flush()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        })

        val targetUrl = intent.getStringExtra(EXTRA_AUTH_URL) ?: DEFAULT_GOOGLE_LOGIN_URL
        loginWebView.loadUrl(targetUrl)
    }

    private fun isReturnHost(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""
        return (host == "gemini.google.com" || host == "studio.youtube.com") && !path.contains("signin")
    }

    private fun completeLogin() {
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        loginWebView.destroy()
        super.onDestroy()
    }
}
