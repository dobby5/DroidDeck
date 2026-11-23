package com.droiddeck.launcher.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.update.SelfInstaller
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AndroidEmulators {
    private const val TAG = "AndroidEmulators"
    const val CATALOG_URL = "https://raw.githubusercontent.com/Droid-Deck/DroidDeck-Components/main/android-emulators.json"
    private const val CACHE = "android-emulators.json"
    private const val ASSET = "android-emulators.json"
    private const val FETCH_EVERY_MS = 6L * 3600 * 1000

    class Apk(
        val url: String?, val github: String?, val page: String?, val pattern: Regex?, val prerelease: Boolean,
        val sha256: String?, val size: Long, val versionCode: Long, val signers: List<String>,
    )

    class Launch(
        val component: String, val action: String?, val fileUri: Boolean, val data: Boolean,
        val extras: Map<String, String>, val cores: Map<String, String>, val clearTask: Boolean,
        val extensions: Set<String> = emptySet(), val documentUri: Boolean = false,
    ) {
        fun supports(system: String, extension: String): Boolean =
            (extensions.isEmpty() || extension.lowercase() in extensions) &&
                (system.lowercase() in cores || extras.values.none { "{core}" in it })
    }

    class App(
        val id: String, val pkg: String, val name: String, val pairs: String?, val system: String,
        val platforms: Set<String>, val icon: String?,
        val apk: Apk?, val store: String?, val launch: Launch?, val notes: String,
    ) {
        val playUrl: String get() = "https://play.google.com/store/apps/details?id=$pkg"
        val onPlay: Boolean get() = store == "market://details?id=$pkg"
        val directSource: String? get() = apk?.let { a ->
            a.github?.let { "GitHub ($it)" } ?: (a.url ?: a.page)?.let { runCatching { java.net.URL(it).host.removePrefix("www.") }.getOrNull() }
        }
    }

    const val PLAY_STORE = "com.android.vending"

    fun playStoreAvailable(context: Context): Boolean =
        runCatching { context.packageManager.getApplicationInfo(PLAY_STORE, 0).enabled }.getOrDefault(false)

    @Volatile private var lastFetch = 0L
    @Volatile private var known: List<App>? = null

    fun parse(body: String): List<App>? = try {
        val arr = JSONObject(body).getJSONArray("apps")
        (0 until arr.length()).mapNotNull { i -> app(arr.getJSONObject(i)) }
    } catch (e: Exception) {
        Log.w(TAG, "catalog: $e"); null
    }

    private fun app(o: JSONObject): App? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val pkg = o.optString("package").takeIf { it.isNotBlank() } ?: return null
        return App(
            id, pkg, o.optString("name", pkg), o.optString("pairs").takeIf { it.isNotBlank() && it != "null" },
            o.optString("system", ""), strings(o.optJSONArray("platforms") ?: o.optJSONArray("folders")).map { it.lowercase() }.toSet(),
            o.optString("icon").takeIf { it.startsWith("https://") },
            o.optJSONObject("apk")?.let(::apk), o.optString("store").takeIf { it.startsWith("market://") || it.startsWith("https://") },
            o.optJSONObject("launch")?.let { launch(it, pkg) }, o.optString("notes", ""),
        )
    }

    private val HEX64 = Regex("[0-9a-fA-F]{64}")
    private val REPO = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

    private fun apk(o: JSONObject): Apk? {
        val signers = (strings(o.optJSONArray("signers")) + listOfNotNull(o.optString("signerSha256").takeIf { it.isNotBlank() }))
            .filter { HEX64.matches(it) }.map { it.lowercase() }.distinct()
        if (signers.isEmpty()) return null
        val url = o.optString("url").takeIf { it.startsWith("https://") }
        val github = o.optString("github").takeIf { REPO.matches(it) }
        val page = o.optString("page").takeIf { it.startsWith("https://") }
        val pattern = (if (github != null) o.optString("asset") else o.optString("pattern"))
            .takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
        val sha = o.optString("sha256").takeIf { HEX64.matches(it) }?.lowercase()
        if (url == null && (github == null && page == null || pattern == null)) return null
        return Apk(
            url, github, page, pattern, o.optBoolean("prerelease", false),
            sha, o.optLong("size", 0L), o.optLong("versionCode", 0L), signers,
        )
    }

    private fun launch(o: JSONObject, pkg: String): Launch? {
        val component = o.optString("component")
        val target = android.content.ComponentName.unflattenFromString(component) ?: return null
        if (target.packageName != pkg) return null
        val data = o.optBoolean("data", false)
        val extras = stringMap(o.optJSONObject("extras"))
        if (!data && extras.values.none { "{rom}" in it }) return null
        val uri = o.optString("uri", "content")
        return Launch(
            component, o.optString("action").takeIf { it.isNotBlank() }, uri == "file",
            data, extras, stringMap(o.optJSONObject("cores")).mapKeys { it.key.lowercase() },
            o.optBoolean("clearTask", true), strings(o.optJSONArray("extensions")).map { it.lowercase() }.toSet(), uri == "document",
        )
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { i -> a.optString(i) }.filter { it.isNotBlank() }
    }

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().associateWith { k -> o.optString(k) }
    }

    fun fetch(context: Context, force: Boolean = false): List<App> {
        val now = System.currentTimeMillis()
        known?.let { if (!force && now - lastFetch < FETCH_EVERY_MS) return it }
        lastFetch = now
        Downloader.downloadString(CATALOG_URL)?.let { body ->
            parse(body)?.let { apps ->
                runCatching { File(context.filesDir, CACHE).writeText(body) }
                known = apps
                return apps
            }
        }
        return cached(context)
    }

    fun cached(context: Context): List<App> {
        known?.let { return it }
        val apps = runCatching { parse(File(context.filesDir, CACHE).readText()) }.getOrNull()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrNull()
            ?: emptyList()
        known = apps
        return apps
    }

    fun installedVersion(context: Context, pkg: String): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    fun installed(context: Context, pkg: String): Boolean = installedVersion(context, pkg) != null

    fun installedFromCatalog(context: Context, app: App): Boolean? {
        val signers = app.apk?.signers ?: return null
        val info = runCatching {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            context.packageManager.getPackageInfo(app.pkg, flags)
        }.getOrNull() ?: return null
        return SelfInstaller.archiveSignerDigests(info).any { it.lowercase() in signers }
    }

    fun catalogPackage(packageName: String): Boolean = known?.any { it.pkg == packageName } == true

    internal fun forgetForTest() { known = null; lastFetch = 0L }
}
