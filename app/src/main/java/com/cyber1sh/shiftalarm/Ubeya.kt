package com.cyber1sh.shiftalarm

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to Ubeya the way the crew web app does (read from its bundle, main.a7b7260b.js):
 * - the session is cookies, set when you sign in on crew.ubeya.com inside [LoginActivity];
 *   this class reads them from the WebView's cookie store and writes back any it is sent;
 * - every API call carries `X-App-Name: Ubeya`;
 * - on a 401 the web app calls `POST auth.api.ubeya.com/user/refresh` once and retries.
 */
class Ubeya(private val context: Context) {

    class SignedOut : Exception("signed out of Ubeya")

    class Http(val code: Int) : IOException("Ubeya answered $code")

    private class Reply(val code: Int, val body: String)

    private val cookies: CookieManager = CookieManager.getInstance()

    private val agent: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("Mozilla/5.0 (Linux; Android)")
    }

    /** Names (never values) of cookies the server set during this client's calls, for the history. */
    val cookieNotes = ArrayList<String>()

    /** The raw answer of GET /bookings: the signed-in crew member's booked shifts. */
    fun bookingsJson(): String = getRefreshing("$API/bookings")

    /** The raw answer of GET /locations: the venues, which name a booking without a project name. */
    fun locationsJson(): String = getRefreshing("$API/locations")

    /**
     * Who is signed in, or null when nobody is. [refresh] lets it renew an expired session first;
     * the sign-in screen polls without it so it does not hammer the refresh endpoint.
     */
    fun whoAmI(refresh: Boolean): String? {
        val url = "$API/auth/me?appBuild=11"
        val body =
            if (refresh) {
                try {
                    getRefreshing(url)
                } catch (e: SignedOut) {
                    return null
                }
            } else {
                val r = send("GET", url)
                if (r.code == 401 || r.code == 403) return null
                if (r.code !in 200..299) throw Http(r.code)
                r.body
            }
        return nameIn(body) ?: ""
    }

    private fun getRefreshing(url: String): String {
        var reply = send("GET", url)
        if (reply.code == 401) {
            val renewed = send("POST", "$AUTH/user/refresh")
            if (renewed.code in 200..299) reply = send("GET", url)
        }
        if (reply.code == 401 || reply.code == 403) throw SignedOut()
        if (reply.code !in 200..299) throw Http(reply.code)
        return reply.body
    }

    private fun send(method: String, url: String): Reply {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("X-App-Name", "Ubeya")
            conn.setRequestProperty("Origin", WEB)
            conn.setRequestProperty("Referer", "$WEB/")
            conn.setRequestProperty("User-Agent", agent)
            cookies.getCookie(url)?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Cookie", it) }
            if (method == "POST") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.outputStream.close()
            }
            val code = conn.responseCode
            keepCookies(url, conn)
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Reply(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun keepCookies(url: String, conn: HttpURLConnection) {
        var kept = false
        for ((key, values) in conn.headerFields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (value in values) {
                cookies.setCookie(url, value)
                kept = true
                // The name and its lifetime attributes only; the value is the session itself.
                val parts = value.split(';').map { it.trim() }
                val name = parts.firstOrNull()?.substringBefore('=').orEmpty()
                val life = parts.drop(1).filter { it.startsWith("expires", true) || it.startsWith("max-age", true) }
                cookieNotes += (listOf(name) + life).joinToString("; ")
            }
        }
        if (kept) cookies.flush()
    }

    /** A first name from /auth/me's answer, wherever this version of the API keeps it. */
    private fun nameIn(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val candidates = listOfNotNull(root, root.optJSONObject("data"), root.optJSONObject("user"), root.optJSONObject("data")?.optJSONObject("user"))
        for (o in candidates) {
            for (key in listOf("firstName", "first_name", "name", "fullName")) {
                val v = if (o.isNull(key)) "" else o.optString(key).trim()
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }

    companion object {
        const val WEB = "https://crew.ubeya.com"
        const val API = "https://crew.api.ubeya.com"
        const val AUTH = "https://auth.api.ubeya.com"
    }
}
