package com.droiddeck.launcher.files

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.setContent
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droiddeck.launcher.R
import com.droiddeck.launcher.ui.LocalPalette
import com.droiddeck.launcher.ui.controllerConfirm
import com.droiddeck.launcher.ui.glideBorder
import com.droiddeck.launcher.ui.rememberHot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An extra place a caller offers in the explorer's quick access, e.g. Syncthing's folders. */
class ExplorerPlace(val label: String, val dir: File, val icon: ImageVector)

internal class SideItem(
    val label: String, val icon: ImageVector, val selected: Boolean, val dir: File? = null,
    val usage: Pair<Long, Long>? = null, val onClick: () -> Unit,
)

/** What the folder cards say about a folder: subfolders, files and the ones that look like games. */
internal class FolderCounts(val folders: Int, val files: Int, val games: Int)

/** The three steps of "card and row size". */
internal class ExplorerSize(val gridMin: Dp, val art: Dp, val icon: Dp, val rowIcon: Dp, val rowPad: Dp) {
    companion object {
        val steps = listOf(
            ExplorerSize(112.dp, 64.dp, 32.dp, 22.dp, 6.dp),
            ExplorerSize(150.dp, 86.dp, 44.dp, 30.dp, 10.dp),
            ExplorerSize(210.dp, 120.dp, 60.dp, 40.dp, 14.dp),
        )
    }
}

internal fun countFolder(dir: File, showHidden: Boolean, isGame: (File) -> Boolean): FolderCounts {
    val inner = dir.listFiles().orEmpty().filter { showHidden || !it.name.startsWith(".") }
    return FolderCounts(inner.count { it.isDirectory }, inner.count { it.isFile }, inner.count { it.isFile && isGame(it) })
}

@Composable
internal fun folderDetail(c: FolderCounts?): String {
    if (c == null) return " "
    return listOfNotNull(
        stringResource(R.string.fp_folders, c.folders).takeIf { c.folders > 0 },
        stringResource(R.string.fp_games, c.games).takeIf { c.games > 0 },
        stringResource(R.string.fp_files, c.files).takeIf { c.games == 0 && c.files > 0 },
    ).joinToString(" · ").ifEmpty { "—" }
}

@Composable
internal fun SideHeading(text: String) {
    Text(
        com.droiddeck.launcher.ui.headingUppercase(text), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
    )
}

/** A storage card with its free space, or a slimmer row for quick access and favorites. */
@Composable
internal fun SideEntry(item: SideItem) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (item.selected) pal.signal.copy(alpha = 0.16f) else colors.surface)
            .glideBorder(hot, shape, pal.signal, pal.line)
            .hoverable(src).clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = item.onClick)
            .controllerConfirm(onClick = item.onClick)
            .padding(horizontal = 14.dp, vertical = if (item.usage != null) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(item.icon, null, tint = if (item.selected || hot) pal.signal else colors.onSurfaceVariant, modifier = Modifier.size(if (item.usage != null) 22.dp else 18.dp))
            Text(
                item.label, fontSize = if (item.usage != null) 15.sp else 14.sp, fontWeight = if (item.usage != null) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        item.usage?.let { (free, total) ->
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = pal.signal, trackColor = pal.line,
                )
                Text(
                    stringResource(R.string.fp_free_of, com.droiddeck.launcher.stores.formatBytes(free), com.droiddeck.launcher.stores.formatBytes(total)),
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PathPill(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) pal.signal.copy(alpha = 0.22f) else colors.surface)
            .glideBorder(hot, CircleShape, pal.signal, pal.line)
            .hoverable(src).clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
            .controllerConfirm(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected || hot) pal.signal else colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
            color = if (selected || hot) colors.onBackground else colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Breadcrumbs(crumbs: List<Pair<String, File>>, onOpen: (File) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        crumbs.forEachIndexed { i, (label, f) ->
            if (i > 0) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            PathPill(label, selected = i == crumbs.lastIndex) { onOpen(f) }
        }
    }
}

/** A round tool button in the header; [active] marks a switched-on state such as a favorite or the search. */
@Composable
internal fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src) && enabled
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (active) pal.signal.copy(alpha = 0.22f) else colors.surface)
            .glideBorder(hot, CircleShape, pal.signal, pal.line)
            .then(
                if (enabled) Modifier.hoverable(src).clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
                    .controllerConfirm(onClick = onClick) else Modifier,
            )
            .semantics { contentDescription = label },
    ) {
        Icon(icon, null, tint = if (active || hot) pal.signal else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** Folder, picture thumbnail, a Windows program's own icon, or a plain file. */
@Composable
internal fun FileVisual(file: File, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val isDir = remember(file) { file.isDirectory }
    val name = file.name.lowercase()
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (!isDir && name.endsWith(".exe")) LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }?.let { exeIcon = it.asImageBitmap() }
    }
    when {
        isDir -> Icon(Icons.Filled.Folder, null, tint = pal.signal, modifier = Modifier.size(size))
        exeIcon != null -> Image(exeIcon!!, null, modifier = Modifier.size(size))
        name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".msi") || name.endsWith(".sh") ->
            Icon(painterResource(R.drawable.icon_menu_container), null, tint = colors.onSurface, modifier = Modifier.size(size))
        file.extension.lowercase() in IMAGE_THUMB_EXTS -> AsyncImage(
            model = file, contentDescription = null, contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile), error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 6)),
        )
        else -> Icon(Icons.Filled.InsertDriveFile, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(size))
    }
}

/** Opens the context menu with Y or the menu key, as a long press does on touch. */
private fun Modifier.menuKey(onMenu: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    val code = e.nativeKeyEvent.keyCode
    if (code != KeyEvent.KEYCODE_BUTTON_Y && code != KeyEvent.KEYCODE_MENU) false
    else { if (e.nativeKeyEvent.action == KeyEvent.ACTION_UP) onMenu(); true }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExplorerCard(
    file: File, detail: String, size: ExplorerSize, selected: Boolean, selecting: Boolean, dimmed: Boolean,
    focus: FocusRequester?, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier, menu: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val shape = RoundedCornerShape(16.dp)
    Box {
        Column(
            modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).clip(shape)
                .alpha(if (dimmed) 0.45f else 1f)
                .background(if (selected) pal.signal.copy(alpha = 0.18f) else if (hot) pal.signal.copy(alpha = 0.10f) else colors.surface)
                .glideBorder(hot, shape, pal.signal, if (selected) pal.signal else pal.line)
                .hoverable(src).combinedClickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onOpen, onLongClick = onMenu)
                .controllerConfirm(onClick = onOpen).menuKey(onMenu),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(size.art).background(
                    Brush.linearGradient(listOf(pal.signal.copy(alpha = if (hot) 0.30f else 0.18f), colors.surfaceVariant)),
                ),
            ) {
                FileVisual(file, size.icon)
                if (selecting) Icon(
                    Icons.Filled.CheckCircle, null, tint = if (selected) pal.signal else colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        menu()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExplorerRow(
    file: File, detail: String, trailing: String?, size: ExplorerSize, selected: Boolean, selecting: Boolean, dimmed: Boolean,
    focus: FocusRequester?, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier, menu: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).clip(shape)
                .alpha(if (dimmed) 0.45f else 1f)
                .background(if (selected) pal.signal.copy(alpha = 0.18f) else if (hot) pal.signal.copy(alpha = 0.10f) else colors.surface)
                .glideBorder(hot, shape, pal.signal, if (selected) pal.signal else pal.line)
                .hoverable(src).combinedClickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onOpen, onLongClick = onMenu)
                .controllerConfirm(onClick = onOpen).menuKey(onMenu)
                .padding(horizontal = 14.dp, vertical = size.rowPad),
        ) {
            if (selecting) Icon(Icons.Filled.CheckCircle, null, tint = if (selected) pal.signal else colors.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            FileVisual(file, size.rowIcon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Text(trailing, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
        }
        menu()
    }
}

/** The actions on one file or folder, in the launcher's anchored menu. */
@Composable
internal fun FileActionMenu(
    open: Boolean, file: File, isFavorite: Boolean, onDismiss: () -> Unit,
    onSelect: () -> Unit, onProperties: () -> Unit, onToggleFavorite: () -> Unit,
    onRename: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onDelete: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    com.droiddeck.launcher.ui.AnchoredMenu(open, onDismiss = onDismiss, title = file.name, compact = true, minWidth = 240.dp) { first ->
        val item: @Composable (Int, () -> Unit, FocusRequester?) -> Unit = { label, action, f ->
            com.droiddeck.launcher.ui.MenuItem(stringResource(label), checked = false, focusRequester = f) { onDismiss(); action() }
        }
        val opens = onOpen != null && !file.isDirectory
        if (opens) item(R.string.fm_edit_open, onOpen!!, first)
        item(R.string.fm_select, onSelect, if (opens) null else first)
        item(R.string.fm_properties, onProperties, null)
        if (file.isDirectory) item(if (isFavorite) R.string.fm_favorite_remove else R.string.fm_favorite_add, onToggleFavorite, null)
        item(R.string.fm_rename, onRename, null)
        item(R.string.fm_copy, onCopy, null)
        item(R.string.fm_cut, onCut, null)
        item(R.string.common_delete, onDelete, null)
    }
}

/** Free and total bytes of the volume [dir] is on, read once per card. */
internal fun volumeUsage(dir: File): Pair<Long, Long> = runCatching { dir.freeSpace to dir.totalSpace }.getOrDefault(0L to 0L)

/** The explorer over the launcher, for pickers that answer in place instead of through an activity result. */
@Composable
internal fun ExplorerDialog(
    title: String, onDismiss: () -> Unit, pickDirectory: Boolean = false, extensions: List<String> = emptyList(),
    initialDir: File? = null, places: List<ExplorerPlace> = emptyList(), browsable: (File) -> Boolean = { true },
    pickAllowed: (File) -> Boolean = { true }, suggestedFolderName: String? = null,
    onAddAll: ((File, Boolean) -> Unit)? = null, onPick: (File) -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        com.droiddeck.launcher.ui.FocusGlideHost(Modifier.fillMaxSize()) {
            FileManagerScreen(
                pickMode = true, pickDirMode = pickDirectory, pickExtensions = extensions, initialDir = initialDir, pickerTitle = title,
                onPick = { onPick(it); onDismiss() }, onExit = onDismiss, places = places, browsable = browsable, pickAllowed = pickAllowed,
                suggestedFolderName = suggestedFolderName, onAddAll = onAddAll?.let { add -> { dir, hidden -> add(dir, hidden); onDismiss() } },
            )
        }
    }
}

/** The explorer as an activity's whole window, edge to edge when the launcher runs full screen. */
internal fun androidx.activity.ComponentActivity.showExplorer(content: @Composable () -> Unit) {
    if (com.droiddeck.launcher.session.SessionPrefs.launcherFullscreen(this)) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    setContent {
        com.droiddeck.launcher.ui.DroidDeckTheme(appScale = com.droiddeck.launcher.core.AppUiPrefs.scale(this)) {
            com.droiddeck.launcher.ui.FocusGlideHost(
                Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).then(if (com.droiddeck.launcher.session.SessionPrefs.launcherFullscreen(this)) Modifier else Modifier.systemBarsPadding()),
            ) { content() }
        }
    }
}
