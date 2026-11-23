package com.droiddeck.launcher.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.runtime.LinuxRuntime
import com.droiddeck.launcher.sync.Syncthing
import java.io.File

internal class SyncLocation(val root: File, val label: String, val icon: ImageVector)

internal fun containerHome(context: Context) = File(LinuxRuntime.rootDir(context), "root")

internal fun syncLocations(context: Context): List<SyncLocation> {
    val volumes = pickVolumes(context)
    val roots = Syncthing.storageRoots(context)
    val shared = roots.drop(1).mapIndexed { i, root ->
        val volume = volumes.firstOrNull { root.path.startsWith(it.root.path + "/") }
        if (i == 0) SyncLocation(root, context.getString(R.string.sync_location_shared), Icons.Outlined.Smartphone)
        else SyncLocation(root, context.getString(R.string.sync_location_card, volume?.label ?: root.path), Icons.Outlined.SdCard)
    }
    val home = containerHome(context).takeIf { it.isDirectory }?.let { SyncLocation(it, context.getString(R.string.sync_location_home), Icons.Outlined.Home) }
    return listOfNotNull(home, SyncLocation(Syncthing.folderRoot(context), context.getString(R.string.sync_location_sync), Icons.Outlined.FolderSpecial)) +
        shared + SyncLocation(roots.first(), context.getString(R.string.sync_location_app), Icons.Outlined.Folder)
}

@Composable
internal fun SyncFolderPicker(title: String, suggestedName: String?, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val ctx = LocalContext.current
    val places = remember { syncLocations(ctx).map { com.droiddeck.launcher.files.ExplorerPlace(it.label, it.root, it.icon) } }
    val guard = remember { Syncthing.guard(ctx) }
    com.droiddeck.launcher.files.ExplorerDialog(
        title, onDismiss, pickDirectory = true,
        initialDir = containerHome(ctx).takeIf { it.isDirectory } ?: Syncthing.folderRoot(ctx).also { it.mkdirs() },
        places = places, browsable = { !it.isDirectory || (it.canRead() && guard.browsable(it)) }, pickAllowed = { guard.allows(it.path) },
        suggestedFolderName = suggestedName, onPick = onPick,
    )
}
