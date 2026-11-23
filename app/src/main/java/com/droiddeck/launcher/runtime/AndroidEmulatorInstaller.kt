package com.droiddeck.launcher.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.core.FileUtils
import com.droiddeck.launcher.core.Hashes
import com.droiddeck.launcher.update.SelfInstaller
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.URL

object AndroidEmulatorInstaller {
    const val ACTION_STATUS = "com.droiddeck.launcher.ANDROID_EMULATOR_STATUS"

    /** [version] identifies the build for update checks; [label] is what the user reads. */
    class Resolved(val url: String, val sha256: String?, val size: Long, val version: String, val label: String)

    internal fun versionLabel(s: String): String =
        Regex("\\d+(?:[._]\\d+)*").findAll(s.substringAfter("://").substringAfter('/')).map { it.value }
            .maxByOrNull { it.length }?.replace('_', '.') ?: s.substringAfterLast('/')

    internal fun resolve(apk: AndroidEmulators.Apk, fetch: (String) -> String?): Resolved? {
        apk.url?.let {
            val version = if (apk.versionCode > 0) AndroidEmulatorUpdates.pinnedVersion(apk) else it
            return Resolved(it, apk.sha256, apk.size, version, versionLabel(it))
        }
        val pattern = apk.pattern ?: return null
        apk.github?.let { repo ->
            val releases = fetch("https://api.github.com/repos/$repo/releases?per_page=15")?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft") || (release.optBoolean("prerelease") && !apk.prerelease)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (!pattern.containsMatchIn(asset.optString("name"))) continue
                    val url = asset.optString("browser_download_url").takeIf { it.startsWith("https://") } ?: continue
                    val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }?.lowercase()
                    val tag = release.optString("tag_name").ifBlank { versionLabel(url) }
                    val version = digest ?: "$tag@${asset.optString("updated_at")}"
                    return Resolved(url, digest, asset.optLong("size", 0L), version, tag.removePrefix("v"))
                }
            }
            return null
        }
        val page = apk.page ?: return null
        val html = fetch(page) ?: return null
        val best = pattern.findAll(html).map { it.value }.distinct().maxWithOrNull(::compareVersions) ?: return null
        val url = runCatching { URL(URL(page), best).toString() }.getOrNull()?.takeIf { it.startsWith("https://") } ?: return null
        return Resolved(url, null, 0L, url, versionLabel(best))
    }

    internal fun compareVersions(a: String, b: String): Int {
        val na = Regex("\\d+").findAll(a).map { it.value.toBigInteger() }.toList()
        val nb = Regex("\\d+").findAll(b).map { it.value.toBigInteger() }.toList()
        for (i in 0 until maxOf(na.size, nb.size)) {
            val c = (na.getOrNull(i) ?: 0.toBigInteger()).compareTo(nb.getOrNull(i) ?: 0.toBigInteger())
            if (c != 0) return c
        }
        return a.compareTo(b)
    }

    fun download(
        context: Context, app: AndroidEmulators.App, cancel: Downloader.Cancellation, progress: (Int) -> Unit,
    ): Pair<File, Resolved> {
        val apk = app.apk ?: throw IOException(context.getString(R.string.android_emu_no_release, app.name))
        val source = resolve(apk) { Downloader.downloadString(it, cancel) }
            ?: throw if (cancel.asBoolean) java.util.concurrent.CancellationException() else IOException(context.getString(R.string.android_emu_no_release, app.name))
        val dir = File(context.cacheDir, "emu-apks").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name != "${app.id}.apk") FileUtils.delete(it) }
        val target = File(dir, "${app.id}.apk")
        FileUtils.delete(target)
        val ok = Downloader.downloadFile(source.url, target, false, source.sha256, cancel) { f -> progress(if (f < 0) -1 else (f * 100).toInt()) }
        if (cancel.asBoolean) { FileUtils.delete(target); throw java.util.concurrent.CancellationException() }
        if (!ok) throw IOException(context.getString(R.string.selfinst_download_failed))
        try {
            if (source.size > 0 && target.length() != source.size) throw IOException(context.getString(R.string.selfinst_size_mismatch))
            if (source.sha256 != null && !source.sha256.equals(Hashes.sha256(target), ignoreCase = true)) {
                throw IOException(context.getString(R.string.selfinst_checksum_mismatch))
            }
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNATURES
            val info = context.packageManager.getPackageArchiveInfo(target.absolutePath, flags)
            problem(context, app, info, info?.let(SelfInstaller::archiveSignerDigests).orEmpty())?.let { throw IOException(it) }
        } catch (e: Exception) {
            FileUtils.delete(target)
            throw e
        }
        return target to source
    }

    internal fun problem(context: Context, app: AndroidEmulators.App, info: PackageInfo?, digests: List<String>): String? {
        val apk = app.apk ?: return context.getString(R.string.android_emu_no_release, app.name)
        if (info == null) return context.getString(R.string.selfinst_unreadable)
        if (info.packageName != app.pkg) return context.getString(R.string.selfinst_apk_wrong_package, info.packageName, app.pkg)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (apk.url != null && apk.versionCode > 0 && code != apk.versionCode) {
            return context.getString(R.string.selfinst_version_mismatch, code, apk.versionCode)
        }
        if (digests.isEmpty()) return context.getString(R.string.selfinst_no_signing)
        if (digests.none { it.lowercase() in apk.signers }) return context.getString(R.string.android_emu_wrong_signer, app.name)
        return null
    }

    fun install(context: Context, app: AndroidEmulators.App, apk: File, onSession: (Int) -> Unit) =
        SelfInstaller.install(context, apk, app.pkg, ACTION_STATUS, onSession)
}
