package com.droiddeck.launcher.runtime

import android.content.Context
import android.content.SharedPreferences
import com.droiddeck.launcher.session.SessionPrefs

/**
 * Newer APKs for emulator apps DroidDeck installed from their direct source. Play and other
 * foreign builds are left to their own store.
 */
object AndroidEmulatorUpdates {
    const val EVERY_START = "start"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val NEVER = "never"
    val intervals = listOf(EVERY_START, DAILY, WEEKLY, NEVER)

    private const val DAY = 24L * 3600 * 1000
    private val processStart = System.currentTimeMillis()

    class Installed(val code: Long, val name: String)

    private fun prefs(context: Context) = context.getSharedPreferences("android_emulator_updates", Context.MODE_PRIVATE)

    fun checkedAt(context: Context): Long = prefs(context).getLong("checkedAt", 0L)

    fun due(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        due(SessionPrefs.emuUpdateInterval(context), checkedAt(context), now, processStart)

    internal fun due(interval: String, last: Long, now: Long, started: Long): Boolean = when (interval) {
        NEVER -> false
        EVERY_START -> last < started
        WEEKLY -> last > now || now - last >= 7 * DAY
        else -> last > now || now - last >= DAY
    }

    fun check(context: Context, apps: List<AndroidEmulators.App>, fetch: (String) -> String?) =
        check(prefs(context), apps, fetch, System.currentTimeMillis()) { app ->
            if (AndroidEmulators.installedFromCatalog(context, app) != true) null else installed(context, app.pkg)
        }

    internal fun check(
        p: SharedPreferences, apps: List<AndroidEmulators.App>, fetch: (String) -> String?, now: Long,
        installed: (AndroidEmulators.App) -> Installed?,
    ) {
        val edit = p.edit()
        for (app in apps) {
            val apk = app.apk ?: continue
            val have = installed(app) ?: continue
            val found = AndroidEmulatorInstaller.resolve(apk, fetch) ?: continue
            edit.putString("latest.${app.id}", found.version).putString("label.${app.id}", found.label)
            val known = p.getString("installed.${app.id}", null) != null && p.getLong("installedCode.${app.id}", -1L) == have.code
            if (!known && sameVersion(found.label, have.name)) {
                edit.putString("installed.${app.id}", found.version).putLong("installedCode.${app.id}", have.code)
            }
        }
        edit.remove("snoozed").putLong("checkedAt", now).apply()
    }

    /** The label of a newer APK than the one installed, unless the user skipped it. */
    fun available(context: Context, app: AndroidEmulators.App): String? {
        val have = installed(context, app.pkg) ?: return null
        if (AndroidEmulators.installedFromCatalog(context, app) != true) return null
        return available(prefs(context), app, have)
    }

    /**
     * A build DroidDeck installed is compared by its download; one installed elsewhere (another store, the
     * website, an older DroidDeck) by its versionName against the newest release's version.
     */
    internal fun available(p: SharedPreferences, app: AndroidEmulators.App, have: Installed): String? {
        val apk = app.apk ?: return null
        if (apk.url != null && apk.versionCode > 0) {
            if (apk.versionCode <= have.code || p.getString("skip.${app.id}", null) == pinnedVersion(apk)) return null
            return AndroidEmulatorInstaller.versionLabel(apk.url)
        }
        val latest = p.getString("latest.${app.id}", null) ?: return null
        if (latest == p.getString("skip.${app.id}", null)) return null
        val label = p.getString("label.${app.id}", null) ?: latest
        val had = p.getString("installed.${app.id}", null)
        if (had != null && p.getLong("installedCode.${app.id}", -1L) == have.code) return label.takeIf { latest != had }
        return label.takeIf { newer(it, have.name) }
    }

    /**
     * Whether release [label] is newer than [versionName]: dotted versions compare part by part ("0.3.51" over
     * "0.3.45"); a plain build number compares with the versionName's number of the same length ("12910" over
     * "0.1-12870-g9fe085e7d"). Anything else is not claimed as an update.
     */
    internal fun newer(label: String, versionName: String): Boolean {
        if (sameVersion(label, versionName)) return false
        val dotted = Regex("\\d+(?:\\.\\d+)+")
        val want = dotted.find(label)?.value
        if (want != null) {
            val have = dotted.find(versionName)?.value ?: return false
            val a = want.split('.').map { it.toBigInteger() }
            val b = have.split('.').map { it.toBigInteger() }
            for (i in 0 until maxOf(a.size, b.size)) {
                val c = (a.getOrNull(i) ?: java.math.BigInteger.ZERO).compareTo(b.getOrNull(i) ?: java.math.BigInteger.ZERO)
                if (c != 0) return c > 0
            }
            return false
        }
        val build = Regex("\\d+").findAll(label).map { it.value.trimStart('0') }.filter { it.isNotEmpty() }.maxByOrNull { it.length } ?: return false
        val same = Regex("\\d+").findAll(versionName).map { it.value.trimStart('0') }.filter { it.length == build.length }.toList()
        return same.isNotEmpty() && same.all { it.toBigInteger() < build.toBigInteger() }
    }

    fun asks(context: Context, app: AndroidEmulators.App): Boolean =
        app.id !in prefs(context).getStringSet("snoozed", emptySet()).orEmpty()

    fun snooze(context: Context, app: AndroidEmulators.App) {
        val p = prefs(context)
        p.edit().putStringSet("snoozed", p.getStringSet("snoozed", emptySet()).orEmpty() + app.id).apply()
    }

    fun skip(context: Context, app: AndroidEmulators.App) = skip(prefs(context), app)

    internal fun skip(p: SharedPreferences, app: AndroidEmulators.App) {
        val apk = app.apk ?: return
        val version = if (apk.url != null && apk.versionCode > 0) pinnedVersion(apk) else p.getString("latest.${app.id}", null) ?: return
        p.edit().putString("skip.${app.id}", version).apply()
    }

    fun recordInstalled(context: Context, app: AndroidEmulators.App, version: String) {
        val code = AndroidEmulators.installedVersion(context, app.pkg) ?: return
        recordInstalled(prefs(context), app, version, code)
    }

    internal fun recordInstalled(p: SharedPreferences, app: AndroidEmulators.App, version: String, code: Long) {
        p.edit().putString("installed.${app.id}", version).putLong("installedCode.${app.id}", code)
            .putString("latest.${app.id}", version).remove("skip.${app.id}").apply()
    }

    internal fun pinnedVersion(apk: AndroidEmulators.Apk) = "code:${apk.versionCode}"

    private fun installed(context: Context, pkg: String): Installed? = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        Installed(code, info.versionName.orEmpty())
    }.getOrNull()

    /** Whether a release label's numbers appear, in order and adjacent, in the installed versionName. */
    internal fun sameVersion(label: String, versionName: String): Boolean {
        val want = numbers(label).ifEmpty { return false }
        val have = numbers(versionName)
        return (0..have.size - want.size).any { start -> want.indices.all { have[start + it] == want[it] } }
    }

    private fun numbers(s: String) = Regex("\\d+").findAll(s).map { it.value.trimStart('0').ifEmpty { "0" } }.toList()
}
