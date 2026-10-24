package com.cyber1sh.shiftalarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView

/**
 * Ubeya's own crew site in a WebView, where you sign in. The app never sees the code or
 * number typed here; it only keeps the cookies the site sets, as a browser would.
 */
class LoginActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var done: Button
    private var signedIn = false
    @Volatile private var probing = false

    private val poll =
        object : Runnable {
            override fun run() {
                probe()
                handler.postDelayed(this, 4000L)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        web = findViewById(R.id.login_web)
        status = findViewById(R.id.login_status)
        done = findViewById(R.id.login_done)
        done.setOnClickListener { finish() }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = leaveApp(request.url)

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = probeSoon()

                override fun onPageFinished(view: WebView, url: String?) = probeSoon()
            }
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(Ubeya.WEB + "/home")
    }

    /** Ubeya and its sign-in helpers stay in the WebView; phone numbers, mail and stores open outside. */
    private fun leaveApp(url: Uri): Boolean {
        val host = url.host.orEmpty()
        val isWeb = url.scheme == "https" || url.scheme == "http"
        val inside = listOf("ubeya.com", "google.com", "gstatic.com", "recaptcha.net").any { host == it || host.endsWith(".$it") }
        if (isWeb && inside) return false
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
        return true
    }

    private fun probeSoon() {
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 800L)
    }

    /** Asks Ubeya, with the WebView's cookies, whether anyone is signed in yet. */
    private fun probe() {
        if (probing) return
        probing = true
        Thread {
            val who = runCatching { Ubeya(this).whoAmI(refresh = false) }
            runOnUiThread {
                probing = false
                val name = who.getOrNull()
                if (name != null && !signedIn) onSignedIn(name)
            }
        }.start()
    }

    private fun onSignedIn(name: String) {
        signedIn = true
        CookieManager.getInstance().flush()
        val store = Store(this)
        val fresh = store.signedIn != 1
        store.signedIn = 1
        store.who = name
        val who = if (name.isNotEmpty()) "Signed in as $name." else "Signed in."
        if (fresh) {
            store.log("signed in to Ubeya" + if (name.isNotEmpty()) " as $name" else "")
            status.text = "$who Reading your shifts now. You can close this."
            CheckService.start(this, AlarmReceiver.REASON_SIGNED_IN)
        } else {
            status.text = "$who You can close this."
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        web.destroy()
        super.onDestroy()
    }

    @Deprecated("Back walks the site's history first.")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
