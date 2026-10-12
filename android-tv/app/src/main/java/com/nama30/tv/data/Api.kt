package com.nama30.tv.data

import com.nama30.tv.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(msg: String, val retry: Boolean = false, val retryAfterMs: Long = 0) : Exception(msg)

/** 30nama RPC client: endpoint discovery, a rate-limit-friendly request queue, typed calls. */
object Api {
    private val DISCOVERY = listOf(
        "https://pub-ac69cf865ce145f8b4c1ed87dc2a9fb7.r2.dev/sdk-endpoint.json",
        "https://30update.s3.ir-thr-at1.arvanstorage.ir/sdk-endpoint.json",
    )
    private val FALLBACK = listOf("https://world.hana-api.com/api/v2", "https://ir.kazem-api.com/api/v2")
    // WEBTV key verified live (platform "WebTV"); ANDROIDTV key kept as fallback.
    private val KEYS = listOf("83hRzBabjDnuv9SmV2GFaftpxjhZSHXYe7UnBuX5DdANdpzPvXu46jvKZHPb97As", "VdapnkLG9EHsSmF8yeKcb4253RAuN6YX")
    private const val VERSION = "1.0.0"

    lateinit var http: OkHttpClient; private set
    var host: String? = null; private set
    private var key = KEYS[0]
    private val initLock = Mutex()

    fun setup(cacheDir: File) { if (!::http.isInitialized) http = Net.client(cacheDir) }

    suspend fun init() = initLock.withLock {
        if (host != null) return@withLock
        val hosts = LinkedHashSet<String>()
        Prefs.host?.let(hosts::add)
        hosts += discover()
        hosts += FALLBACK
        for (k in KEYS) {
            val ok = firstOk(hosts.toList()) { h -> raw(h, k, "test", emptyMap(), "").bool("success") }
            if (ok != null) { host = ok; key = k; Prefs.host = ok; return@withLock }
        }
        throw ApiException("no endpoint reachable", retry = true)
    }

    private suspend fun discover(): List<String> = coroutineScope {
        val jobs = DISCOVERY.map { u ->
            async {
                withTimeoutOrNull(6000) {
                    runCatching {
                        val body = get("$u?t=${System.currentTimeMillis()}")
                        JSONArray(body).mapObj { it.str("host") }
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        // first non-empty answer wins
        var result: List<String> = emptyList()
        val pending = jobs.toMutableList()
        while (pending.isNotEmpty() && result.isEmpty()) {
            val (job, v) = select { pending.forEach { j -> j.onAwait { j to it } } }
            pending.remove(job); if (v != null) result = v
        }
        pending.forEach { it.cancel() }
        result
    }

    private suspend fun firstOk(hosts: List<String>, test: suspend (String) -> Boolean): String? = coroutineScope {
        val jobs = hosts.map { h -> async { if (withTimeoutOrNull(8000) { runCatching { test(h) }.getOrDefault(false) } == true) h else null } }
        var found: String? = null
        val pending = jobs.toMutableList()
        while (pending.isNotEmpty() && found == null) {
            val (job, v) = select { pending.forEach { j -> j.onAwait { j to it } } }
            pending.remove(job); found = v
        }
        pending.forEach { it.cancel() }
        found
    }

    /* ---------- transport ---------- */

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!c.isCancelled) c.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) = c.resume(response)
        })
        c.invokeOnCancellation { runCatching { cancel() } }
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", Net.UA).build()).await().use { it.body?.string() ?: "" }
    }

    private suspend fun raw(host: String, key: String, action: String, params: Map<String, Any?>, token: String): JSONObject = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { params.forEach { (k, v) -> if (v != null) add(k, v.toString()) } }.build()
        val req = Request.Builder().url("$host/action/$action").post(form)
            .header("User-Agent", Net.UA)
            .header("c-api-key", key).header("c-token", token)
            .header("c-version-number", VERSION).header("c-language", Prefs.lang)
            .build()
        val r = try { http.newCall(req).await() } catch (e: IOException) { throw ApiException(e.message ?: "network", retry = true) }
        r.use {
            val txt = it.body?.string() ?: ""
            try { JSONObject(txt) } catch (_: Exception) {
                if (it.code == 204) return@withContext JSONObject().put("success", true)
                val ra = it.header("retry-after")?.toDoubleOrNull()?.coerceAtMost(10.0)?.times(1000)?.toLong() ?: 0
                throw ApiException("${it.code} bad json from $action", retry = true, retryAfterMs = ra)
            }
        }
    }

    /* ---------- queue: Cloudflare rate-limits (429 / "error code: 1015"), so max 2 in flight, small spacing, backoff ---------- */

    private val gate = Semaphore(2)
    private val spacing = Mutex()
    @Volatile private var lastStart = 0L
    @Volatile private var pausedUntil = 0L
    private val NO_RETRY = setOf("observer", "logout", "qrlogin_login")
    private val BACKOFF = longArrayOf(1000, 3000)

    /** Envelope call: returns the JSON envelope ({success,msg,result}). */
    suspend fun call(action: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        if (host == null) init()
        var attempt = 0
        while (true) {
            try {
                val j = gate.withPermit {
                    spacing.withLock {
                        val wait = maxOf(pausedUntil, lastStart + 100) - System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                        lastStart = System.currentTimeMillis()
                    }
                    raw(host!!, key, action, params, Prefs.token ?: "")
                }
                if (!j.bool("success") && Regex("login required", RegexOption.IGNORE_CASE).containsMatchIn(j.str("msg") ?: "") && Prefs.loggedIn) {
                    Prefs.clearSession()
                }
                return j
            } catch (e: ApiException) {
                if (!e.retry || action in NO_RETRY || attempt >= BACKOFF.size) throw e
                val d = maxOf(BACKOFF[attempt], e.retryAfterMs)
                pausedUntil = maxOf(pausedUntil, System.currentTimeMillis() + d)
                delay(d); attempt++
            }
        }
    }

    /** Result call: throws with the server message on !success. */
    suspend fun result(action: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        val j = call(action, params)
        if (!j.bool("success")) throw ApiException(j.str("msg") ?: "error")
        return j.optJSONObject("result") ?: JSONObject()
    }

    /* ---------- typed calls ---------- */

    private fun posts(r: JSONObject?) = (r.arr("posts") ?: r.arr("items") ?: r.arr("list")).mapObj { Post.from(it) }

    private var homeMem: Pair<Long, Home>? = null
    suspend fun home(force: Boolean = false): Home {
        homeMem?.let { (t, h) -> if (!force && System.currentTimeMillis() - t < 90_000) return h }
        return try {
            val r = result("home_stream")
            Prefs.homeCache = r.toString()
            Home.from(r).also { homeMem = System.currentTimeMillis() to it }
        } catch (e: Exception) {
            Prefs.homeCache?.let { Home.from(JSONObject(it)) } ?: throw e   // last good Home instead of an error
        }
    }
    fun dropHome() { homeMem = null }

    /** Everything that holds server text in the previous language (c-language header). */
    fun dropLangCaches() {
        dropHome(); Prefs.homeCache = null; menuMem = null
        synchronized(singleCache) { singleCache.clear() }
        synchronized(streamCache) { streamCache.clear() }
    }

    suspend fun continueWatching(): List<Post> = runCatching { posts(result("continue_watching")) }.getOrDefault(emptyList())

    /* small LRU caches so returning from the player / Back shows Details instantly */
    private fun <V> lru(n: Int) = object : LinkedHashMap<Long, V>(16, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, V>?) = size > n }
    private val singleCache = lru<Post>(30)
    private val streamCache = lru<Stream>(10)
    fun cachedSingle(id: Long) = synchronized(singleCache) { singleCache[id] }
    fun cachedStream(id: Long) = synchronized(streamCache) { streamCache[id] }

    suspend fun single(id: Long): Post = (Post.from(result("single", mapOf("post_id" to id))) ?: throw ApiException("bad post"))
        .also { synchronized(singleCache) { singleCache[id] = it } }

    suspend fun stream(id: Long, free: Boolean): Stream? = streamUncached(id, free)?.also { synchronized(streamCache) { streamCache[id] = it } }

    private suspend fun streamUncached(id: Long, free: Boolean): Stream? {
        val j = runCatching { call("stream", mapOf("post_id" to id, "free_stream" to if (free) 1 else 0)) }.getOrNull() ?: return null
        if (!j.bool("success")) return null
        val r = j.optJSONObject("result") ?: return null
        val items = r.arr("hls").mapObj { StreamItem.from(it) }
            .sortedWith(compareBy({ it.season }, { it.episodeNum }))
        val cw = r.obj("continue_watching")
        val resume = cw?.str("file_id")?.let { Resume(it, cw.num("time") ?: 0.0) }
        return Stream(items, resume)
    }

    /** Server-side "no match" / "query too short" come back as !success: that's an empty result, not an error. */
    suspend fun search(q: String): List<Post> {
        val j = call("search", mapOf("query" to q, "streamonly" to 0, "page" to 1))
        return if (j.bool("success")) posts(j.optJSONObject("result")) else emptyList()
    }

    private var menuMem: Menu? = null
    suspend fun menu(): Menu = menuMem ?: result("menu").let { r ->
        fun g(a: JSONArray?) = a.mapObj { o ->
            val im = o.obj("image"); val u = im.obj("webp").let { it.str("full") ?: it.str("thumb") } ?: im.obj("jpg").let { it.str("full") ?: it.str("thumb") }
            Genre(o.str("name") ?: return@mapObj null, o.str("slug") ?: return@mapObj null, o.int("count"), u)
        }
        Menu(g(r.arr("categories")), g(r.arr("genres"))).also { menuMem = it }
    }

    suspend fun archive(slug: String, page: Int, sort: String): Page {
        val r = result("archive", mapOf("slugs" to slug, "streamonly" to 1, "page" to page, "orderby" to sort, "order" to "desc"))
        return Page(posts(r), r.int("page") ?: page, r.int("pages") ?: 1, r.int("total") ?: 0)
    }

    suspend fun qrCode(): Pair<String, String> {
        val r = result("qrlogin_code")
        val code = r.str("code") ?: throw ApiException("no code")
        return code to (r.str("url") ?: "https://30nama.com/auth/$code")
    }

    /** Returns true once the code was confirmed on the phone and the session is stored. */
    suspend fun qrPoll(code: String): Boolean {
        val j = runCatching { call("qrlogin_login", mapOf("auth_code" to code)) }.getOrNull() ?: return false
        if (!j.bool("success")) return false
        val r = j.optJSONObject("result")
        val u = userObj(r)
        val token = u?.str("user_session") ?: r.str("token") ?: r.str("user_session") ?: return false
        Prefs.setSession(token, u)
        refreshUser()
        dropHome()
        return true
    }

    private fun userObj(r: JSONObject?): JSONObject? = when {
        r?.str("user_session") != null -> r
        r.obj("user")?.str("user_session") != null -> r.obj("user")
        else -> null
    }

    suspend fun refreshUser() {
        val j = runCatching { call("user") }.getOrNull() ?: return
        if (!j.bool("success")) return
        val r = j.optJSONObject("result") ?: return
        val u = userObj(r) ?: r.obj("user") ?: r
        val token = u.str("user_session") ?: Prefs.token ?: return
        Prefs.setSession(token, u)
    }

    suspend fun logout() {
        runCatching { call("logout") }
        Prefs.clearSession(); dropHome()
    }

    suspend fun observer(postId: Long, item: StreamItem, time: Double, duration: Double) {
        if (!Prefs.loggedIn) return
        runCatching {
            call("observer", mapOf(
                "post_id" to postId, "file_id" to item.fileId, "user_id" to (Prefs.user?.id ?: ""),
                "time" to time.toLong(), "duration" to duration.toLong(), "token" to item.token,
            ))
        }
    }

    suspend fun subtitle(url: String): String = get(url)
}

data class Home(val heroes: List<Post>, val rows: List<Pair<String, List<Post>>>) {
    companion object {
        fun from(r: JSONObject): Home {
            fun ps(k: String) = r.obj(k).arr("posts").mapObj { Post.from(it) }
            val rows = listOf("new_releases", "suggested", "top_10", "movies", "series", "anime")
                .map { k -> k to ps(k).let { if (k == "top_10") it.take(10) else it } }
                .filter { it.second.isNotEmpty() }
            return Home(ps("hero_header").take(8), rows)
        }
    }
}
