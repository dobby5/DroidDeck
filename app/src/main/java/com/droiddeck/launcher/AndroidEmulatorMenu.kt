package com.droiddeck.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.frontend.AndroidEmulatorLaunch
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.runtime.AndroidEmulatorInstaller
import com.droiddeck.launcher.runtime.AndroidEmulatorUpdates
import com.droiddeck.launcher.runtime.AndroidEmulators
import com.droiddeck.launcher.ui.DualScreenPanel
import com.droiddeck.launcher.update.SelfInstaller

internal class AndroidEmulatorMenu(private val activity: Activity, private val ui: Handler, private val changed: () -> Unit) {
    var busy by mutableStateOf<String?>(null)
    var percent by mutableIntStateOf(-1)
    private var pending: AndroidEmulators.App? = null
    @Volatile private var session = -1
    @Volatile private var installing: String? = null
    @Volatile private var cancellation: Downloader.Cancellation? = null

    fun get(e: Library.Emulator) { e.android?.let(::get) }

    fun store(e: Library.Emulator) { e.android?.let(::openStore) }

    private fun get(app: AndroidEmulators.App) {
        if (busy != null) return
        if (app.apk == null) { openStore(app); return }
        if (!SelfInstaller.canInstall(activity)) {
            pending = app
            if (runCatching { activity.startActivity(SelfInstaller.permissionIntent(activity)) }.isFailure) { pending = null; openStore(app) }
            return
        }
        busy = app.id
        percent = 0
        session = -1
        val cancel = Downloader.Cancellation()
        cancellation = cancel
        Thread({
            runCatching {
                val (file, source) = AndroidEmulatorInstaller.download(activity, app, cancel) { p -> ui.post { if (!cancel.asBoolean) percent = p } }
                if (cancel.asBoolean) throw java.util.concurrent.CancellationException()
                installing = source.version
                ui.post { if (!cancel.asBoolean) percent = -1 }
                AndroidEmulatorInstaller.install(activity, app, file) { id -> session = id }
                if (cancel.asBoolean) abandon()
            }.onFailure { e -> ui.post { if (!cancel.asBoolean) { busy = null; failed(app, e.message) } } }
        }, "android-emu-install").start()
    }

    /** Stops a running download, or drops the install session that waits for the user's confirmation. */
    fun cancel() {
        if (busy == null) return
        cancellation?.cancel()
        abandon()
        busy = null
        percent = -1
    }

    private fun abandon() {
        val id = session
        session = -1
        if (id >= 0) runCatching { activity.packageManager.packageInstaller.abandonSession(id) }
    }

    private fun openStore(app: AndroidEmulators.App) {
        val tried = AndroidEmulatorLaunch.storeIntent(app)?.let { runCatching { activity.startActivity(it) }.isSuccess } == true
        if (tried) return
        if (runCatching { activity.startActivity(AndroidEmulatorLaunch.storeFallback(app)) }.isFailure) {
            toast(activity.getString(R.string.android_emu_store_failed, app.name))
        }
    }

    fun open(e: Library.Emulator) {
        val app = e.android ?: return
        start(AndroidEmulatorLaunch.openIntent(activity, app), app.name)
    }

    fun launch(e: Library.Emulator, rom: Library.Rom) {
        val app = e.android ?: return
        val intent = AndroidEmulatorLaunch.romIntent(activity, app, rom.hostPath, rom.system)
        if (intent == null) { toast(activity.getString(R.string.android_emu_rom_failed, app.name)); return }
        start(intent, app.name)
    }

    fun skipUpdate(e: Library.Emulator) { e.android?.let { AndroidEmulatorUpdates.skip(activity, it) } }

    fun postponeUpdate(e: Library.Emulator) { e.android?.let { AndroidEmulatorUpdates.snooze(activity, it) } }

    fun remove(e: Library.Emulator) {
        val app = e.android ?: return
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun start(intent: Intent?, label: String) {
        DualScreenPanel.yieldTo(activity.taskId, true)
        if (intent == null || runCatching { activity.startActivity(intent) }.isFailure) {
            DualScreenPanel.yieldTo(activity.taskId, false)
            toast(activity.getString(R.string.android_app_open_failed_primary, label))
        }
    }

    private fun failed(app: AndroidEmulators.App, message: String?) {
        toast(message?.let { activity.getString(R.string.android_emu_install_failed_with, app.name, it) }
            ?: activity.getString(R.string.android_emu_install_failed, app.name))
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    fun onResume() {
        pending?.let { app -> if (SelfInstaller.canInstall(activity)) { pending = null; get(app) } }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1).let { it < 0 || it != session }) return
            val app = AndroidEmulators.cached(activity).firstOrNull { it.id == busy }
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    val safe = confirm?.let { SelfInstaller.safeConfirmIntent(activity, it) }
                    if (safe == null || runCatching { activity.startActivity(safe) }.isFailure) {
                        busy = null
                        app?.let { failed(it, null) }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    busy = null
                    val version = installing
                    if (app != null && version != null) AndroidEmulatorUpdates.recordInstalled(activity, app, version)
                    changed()
                }
                else -> {
                    busy = null
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when {
                        status == PackageInstaller.STATUS_FAILURE_ABORTED || app == null -> Unit
                        status == PackageInstaller.STATUS_FAILURE_CONFLICT || message?.contains("UPDATE_INCOMPATIBLE") == true ->
                            toast(activity.getString(R.string.android_emu_other_signer, app.name))
                        else -> failed(app, message)
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter(AndroidEmulatorInstaller.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else activity.registerReceiver(receiver, filter)
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
    }
}
