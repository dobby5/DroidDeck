package com.droiddeck.launcher.frontend

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.droiddeck.launcher.runtime.AndroidEmulators
import java.io.File

class RomFileProvider : FileProvider()

object AndroidEmulatorLaunch {
    private const val ARGOSY = "com.nendo.argosy"
    private const val DROID_PLAY = "org.nxnative.android"
    private const val DROID_PLAY_GAME_PREFIX = "org.nxnative.game.t"
    private val switchTitleId = Regex("(?i)\\b(?:01|04)[0-9a-f]{14}\\b")

    fun authority(context: Context) = "${context.packageName}.roms"

    fun openIntent(context: Context, app: AndroidEmulators.App): Intent? =
        context.packageManager.getLaunchIntentForPackage(app.pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun romIntent(context: Context, app: AndroidEmulators.App, rom: File, system: String): Intent? =
        romIntent(app, rom, system) { file ->
            if (app.launch?.documentUri == true) RomDocumentProvider.uri(context, file)
            else FileProvider.getUriForFile(context, authority(context), file)
        }

    internal fun romIntent(app: AndroidEmulators.App, rom: File, system: String, contentUri: (File) -> Uri): Intent? {
        val launch = app.launch ?: return null
        if (!launch.supports(system, rom.extension)) return null
        val component = ComponentName.unflattenFromString(launch.component) ?: return null
        val core = launch.cores[system.lowercase()]
        val needsContent = launch.data || !launch.fileUri
        val uri = if (needsContent) runCatching { contentUri(rom) }.getOrNull() ?: return null else null
        val romValue = if (launch.fileUri) rom.absolutePath else uri.toString()
        return Intent(launch.action ?: Intent.ACTION_MAIN).setComponent(component).apply {
            if (launch.data) data = uri
            launch.extras.forEach { (k, v) -> putExtra(k, v.replace("{rom}", romValue).replace("{core}", core.orEmpty())) }
            if (uri != null) {
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch.clearTask) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun storeIntent(app: AndroidEmulators.App): Intent? =
        app.store?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?.apply { if (app.onPlay) setPackage(AndroidEmulators.PLAY_STORE) }

    fun storeFallback(app: AndroidEmulators.App): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(app.playUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isIntegrationPackage(pkg: String): Boolean =
        pkg == ARGOSY || pkg == DROID_PLAY || pkg.startsWith(DROID_PLAY_GAME_PREFIX)

    fun argosyIntent(context: Context, rom: File): Intent? {
        val uri = Uri.Builder().scheme("argosy").authority("launch").appendQueryParameter("path", rom.absolutePath).build()
        return Intent(Intent.ACTION_VIEW, uri).setPackage(ARGOSY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun droidPlayIntent(context: Context, rom: Library.Rom): Intent? {
        if (rom.system != "switch" && rom.system != "switch2") return null
        val titleId = switchTitleId.findAll(rom.hostPath.nameWithoutExtension)
            .map { it.value.lowercase() }.distinct().singleOrNull() ?: return null
        val pm = context.packageManager
        pm.getLaunchIntentForPackage("$DROID_PLAY_GAME_PREFIX$titleId")?.let { return it }
        return pm.getLaunchIntentForPackage(DROID_PLAY)?.takeIf { it.component?.className == "$DROID_PLAY.Launcher" }
            ?.putExtra("game", titleId.uppercase())?.putExtra("play", true)
    }
}
