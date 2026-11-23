package com.droiddeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.droiddeck.launcher.frontend.RomPlatforms
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import com.droiddeck.launcher.runtime.AndroidEmulatorUpdates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.runtime.AndroidEmulators

@Composable
internal fun EmulatorIcon(e: Library.Emulator, size: Int) {
    when (val art = e.art) {
        is Int -> Image(painterResource(art), null, modifier = Modifier.size(size.dp))
        else -> AppIcon(art, e.name, size)
    }
}

internal fun wayLabel(label: String, installed: Boolean) = if (installed) "$label ✓" else label

@Composable
internal fun runnerName(e: Library.Emulator, r: Library.Runner): String = when (r) {
    Library.Runner.CONTAINER -> stringResource(R.string.emu_runner_droiddeck, e.name)
    Library.Runner.ANDROID -> stringResource(R.string.emu_runner_android, e.android?.name ?: e.name)
}

@Composable
internal fun InstallProgress(stage: String?, percent: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (stage != null && percent >= 0) stringResource(R.string.store_busy_percent_short, stage, percent) else stage ?: stringResource(R.string.store_starting),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
        )
        if (percent >= 0) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
    }
}

@Composable
internal fun AndroidEmulatorActions(e: Library.Emulator, s: FrontEndState, a: FrontEndActions, onAddRoms: () -> Unit) {
    val app = e.android ?: return
    val ctx = LocalContext.current
    val busyHere = s.androidEmuBusy == app.id
    val installing = stringResource(R.string.store_installing)
    val foreign = remember(app, e.androidInstalled) { e.androidInstalled && AndroidEmulators.installedFromCatalog(ctx, app) == false }
    if (e.androidInstalled) {
        val update = s.emuUpdates[e.id]
        Rise(3) {
            Actions {
                PrimaryButton(stringResource(R.string.content_open_named, app.name), main = true) { a.onAndroidEmulator(e) }
                SecondaryButton(stringResource(R.string.emu_rom_folder_add), onClick = onAddRoms)
                if (update != null) SecondaryButton(
                    if (busyHere) installing else stringResource(R.string.emu_update_to, update), enabled = s.androidEmuBusy == null,
                ) { a.onGetAndroidEmulator(e) }
                if (busyHere) SecondaryButton(stringResource(R.string.common_cancel)) { a.onCancelAndroidEmulatorDownload() }
                else SecondaryButton(stringResource(R.string.store_remove)) { a.onRemoveAndroidEmulator(e) }
            }
        }
    } else {
        val direct = app.apk != null
        val play = remember(app) { app.onPlay && AndroidEmulators.playStoreAvailable(ctx) }
        var choosing by remember { mutableStateOf(false) }
        val sourceTitle = stringResource(R.string.emu_install_source)
        Rise(3) {
            Actions {
                Box {
                    PrimaryButton(
                        if (busyHere) installing else stringResource(R.string.content_install_named, app.name),
                        enabled = s.androidEmuBusy == null, main = true,
                    ) {
                        when {
                            play && direct -> choosing = true
                            play || !direct -> a.onAndroidEmulatorStore(e)
                            else -> a.onGetAndroidEmulator(e)
                        }
                    }
                    AnchoredMenu(choosing, onDismiss = { choosing = false }, title = sourceTitle, compact = true, minWidth = 280.dp) { first ->
                        MenuItem(
                            stringResource(R.string.emu_source_play), checked = false,
                            detail = stringResource(R.string.emu_source_play_detail), focusRequester = first,
                        ) { choosing = false; a.onAndroidEmulatorStore(e) }
                        MenuItem(
                            stringResource(R.string.emu_source_direct), checked = false,
                            detail = stringResource(R.string.emu_source_direct_detail, app.directSource.orEmpty()),
                        ) { choosing = false; a.onGetAndroidEmulator(e) }
                    }
                }
                if (busyHere) SecondaryButton(stringResource(R.string.common_cancel)) { a.onCancelAndroidEmulatorDownload() }
                else ActionChip(
                    when {
                        play && direct -> stringResource(R.string.emu_source_choice)
                        play -> stringResource(R.string.emu_source_play)
                        direct -> app.directSource.orEmpty()
                        else -> stringResource(R.string.emu_open_store)
                    },
                    ok = false,
                )
            }
        }
    }
    if (busyHere) Rise(4) { InstallProgress(installing, s.androidEmuPercent) }
    Rise(4) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Note(when {
                foreign -> stringResource(R.string.emu_android_foreign, app.name)
                app.notes.isNotBlank() -> app.notes
                app.launch == null -> stringResource(R.string.emu_opens_itself, app.name, s.romsDir ?: stringResource(R.string.setup_tool_roms))
                else -> stringResource(R.string.emu_android_saves, app.name)
            })
        }
    }
}

/** Setup's list of the ROMs folders every emulator shares. A link from an emulator's page scrolls here and focuses Add. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RomFoldersGroup(s: FrontEndState, a: FrontEndActions) {
    val add = remember { androidx.compose.ui.focus.FocusRequester() }
    val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(SetupTabs.openRoms) {
        if (!SetupTabs.openRoms) return@LaunchedEffect
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        runCatching { reveal.bringIntoView() }
        runCatching { add.requestFocus() }
        SetupTabs.openRoms = false
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal)) {
        SettingsGroup(stringResource(R.string.emu_rom_folders), detail = stringResource(R.string.roms_folders_detail)) {
            if (s.romsDirs.isEmpty()) SettingsRow(stringResource(R.string.roms_folders_empty), null) {}
            s.romsDirs.forEachIndexed { i, path ->
                val reachable = remember(path) { java.io.File(path).let { it.isDirectory && it.canRead() } }
                SettingsRow(
                    path,
                    when {
                        !reachable -> stringResource(R.string.roms_folder_unreachable)
                        else -> stringResource(R.string.roms_folder_primary, com.droiddeck.launcher.frontend.RomFolders.guestRoot(java.io.File(s.romsDirs.first()), java.io.File(path)))
                    },
                ) { SecondaryButton(stringResource(R.string.store_remove), compact = true) { a.onRemoveRomsDir(path) } }
            }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                SetupTabs.returnTo?.let {
                    Text(
                        stringResource(R.string.hop_back, SetupTabs.returnLabel.orEmpty()),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    stringResource(R.string.emu_rom_folder_add),
                    modifier = Modifier.focusRequester(add),
                    onClick = a.onAddRomsDir,
                )
            }
        }
    }
}

enum class EmuUpdateChoice { UPDATE, SKIP, LATER }

/** Starts a ROM; on the Android way a found APK update is offered first. Put the modifier on the start button. */
@Composable
internal fun rememberRomStart(e: Library.Emulator, s: FrontEndState, a: FrontEndActions): Pair<Modifier, (Library.Rom, Library.Runner) -> Unit> {
    val pal = LocalPalette.current
    var at by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val version = s.emuUpdates[e.id]
    val ask = version != null && e.id in s.emuUpdateAsk
    val title = stringResource(R.string.emu_update_ask_title, e.android?.name ?: e.name)
    val body = stringResource(R.string.emu_update_ask_body, version.orEmpty())
    val update = stringResource(R.string.emu_update_now)
    val skip = stringResource(R.string.emu_update_skip)
    val later = stringResource(R.string.emu_update_later)
    val anchor = Modifier.onGloballyPositioned { at = it.boundsInRoot() }
    return anchor to { g, runner ->
        val from = at
        if (runner != Library.Runner.ANDROID || !ask || from == null) a.onRom(g, runner)
        else Steps.ask(StepAsk(
            anchor = from, side = StepSide.Right, accent = pal.signal, pillCorner = 12.dp, items = 3, maxWidth = 360.dp,
            handle = { Icon(Icons.Filled.Download, null, tint = pal.signal, modifier = Modifier.size(18.dp)) },
        ) {
            StepTitle(title)
            Text(
                body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.stepItem(1).padding(start = 4.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().stepItem(2)) {
                StepChoice(update, icon = Icons.Filled.Download, enabled = open, modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                    Steps.fold(); a.onEmuUpdate(e, g, EmuUpdateChoice.UPDATE)
                }
                StepChoice(skip, enabled = open, modifier = Modifier.fillMaxWidth()) { Steps.fold(); a.onEmuUpdate(e, g, EmuUpdateChoice.SKIP) }
                StepChoice(later, enabled = open, modifier = Modifier.fillMaxWidth()) { Steps.fold(); a.onEmuUpdate(e, g, EmuUpdateChoice.LATER) }
            }
        })
    }
}

@Composable
internal fun EmulatorUpdatesGroup(host: MenuHost, s: FrontEndState, a: FrontEndActions) {
    val labels = mapOf(
        AndroidEmulatorUpdates.EVERY_START to stringResource(R.string.emu_updates_every_start),
        AndroidEmulatorUpdates.DAILY to stringResource(R.string.emu_updates_daily),
        AndroidEmulatorUpdates.WEEKLY to stringResource(R.string.emu_updates_weekly),
        AndroidEmulatorUpdates.NEVER to stringResource(R.string.emu_updates_never),
    )
    SettingsGroup(stringResource(R.string.emu_updates_group), detail = stringResource(R.string.emu_updates_detail)) {
        ChoiceRow(
            host, "emu-update-interval", stringResource(R.string.emu_updates_interval), null,
            AndroidEmulatorUpdates.intervals.map { it to labels.getValue(it) },
            s.emuUpdateInterval, onPick = a.onEmuUpdateInterval,
        )
        val justNow = stringResource(R.string.emu_updates_just_now)
        val last = s.emuUpdateCheckedAt.takeIf { it > 0 }?.let { at ->
            val now = System.currentTimeMillis()
            if (now - at < android.text.format.DateUtils.MINUTE_IN_MILLIS) justNow
            else android.text.format.DateUtils.getRelativeTimeSpanString(at, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)
                .toString().replaceFirstChar { it.lowercase() }
        }
        ActionRow(
            stringResource(R.string.emu_updates_check),
            when {
                s.emuUpdates.isNotEmpty() -> pluralStringResource(R.plurals.emu_updates_found, s.emuUpdates.size, s.emuUpdates.size)
                last != null -> stringResource(R.string.emu_updates_last, last)
                else -> null
            },
            stringResource(if (s.emuUpdateChecking) R.string.emu_updates_checking else R.string.emu_updates_check_now),
            onClick = { if (!s.emuUpdateChecking) a.onCheckEmuUpdates() },
        )
    }
}

internal const val LIBRARY_ROM_PREFIX = "app:rom:"

internal fun romKey(rom: Library.Rom) = LIBRARY_ROM_PREFIX + rom.hostPath.absolutePath

internal fun romPlatformName(rom: Library.Rom, e: Library.Emulator?): String =
    RomPlatforms.byId(rom.system)?.name ?: e?.system.orEmpty()
