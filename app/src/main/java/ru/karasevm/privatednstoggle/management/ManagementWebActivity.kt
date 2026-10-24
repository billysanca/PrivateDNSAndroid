package ru.karasevm.privatednstoggle.management

import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** No JavaScript bridges, password interception, TLS bypass, file access or native API credentials. */
class ManagementWebActivity : AppCompatActivity() {
    private lateinit var web: WebView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val url = intent.getStringExtra("url") ?: return finish()
        if (!ManagementPolicy.validUrl(url)) return finish()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val address = TextView(this).apply { text = java.net.URI(url).host; setPadding(16,16,16,16) }
        root.addView(address)
        root.addView(Button(this).apply { text = "Fechar painel"; setOnClickListener { finish() } })
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            @Suppress("DEPRECATION")
            saveFormData = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val valid = ManagementPolicy.validUrl(request.url.toString())
                if (valid && request.isForMainFrame) address.text = request.url.host
                return !valid
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        web.loadUrl(url)
    }
    override fun onPause() { CookieManager.getInstance().flush(); super.onPause() }
    override fun onDestroy() { if (::web.isInitialized) { web.stopLoading(); web.destroy() }; super.onDestroy() }
}
