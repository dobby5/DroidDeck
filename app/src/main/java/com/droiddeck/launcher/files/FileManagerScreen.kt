package com.droiddeck.launcher.files

import androidx.compose.foundation.shape.CircleShape
import com.droiddeck.launcher.ui.focusGlide
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.droiddeck.launcher.ui.lazyGridItem
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import com.droiddeck.launcher.ui.GlideDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.droiddeck.launcher.R
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.session.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.droiddeck.launcher.ui.glass
import com.droiddeck.launcher.ui.PrimaryButton
import com.droiddeck.launcher.ui.SecondaryButton
import androidx.compose.foundation.layout.Arrangement
import java.io.File
import java.util.Date

private enum class FileManagerNavigation { EXIT, RETURN_TO_START, BACK, UP }

private const val HISTORY_LIMIT = 50

internal fun canNavigateUp(currentDir: File, currentRoot: File): Boolean =
    currentDir != currentRoot && currentDir.parentFile?.exists() == true

// Back leaves after a drive hop instead of replaying a stack of hops one press at a time.
internal fun backExitsAtRootHop(currentDir: File, currentRoot: File, previous: Pair<File, File>): Boolean =
    currentDir == currentRoot && previous.first == previous.second

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    pickMode: Boolean = false,
    pickDirMode: Boolean = false,
    pickExtensions: List<String> = emptyList(),
    initialDir: File? = null,
    pickerTitle: String? = null,
    onPick: ((File) -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    places: List<ExplorerPlace> = emptyList(),
    browsable: (File) -> Boolean = { true },
    pickAllowed: (File) -> Boolean = { true },
    suggestedFolderName: String? = null,
    onAddAll: ((File, Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lowerExts = remember(pickExtensions) { pickExtensions.map { it.lowercase() } }
    fun matchesPickExt(file: File): Boolean {
        if (lowerExts.isEmpty()) return true
        val name = file.name.lowercase()
        return lowerExts.any { name.endsWith(".$it") }
    }

    val pickPrefs = remember { context.getSharedPreferences("file_manager", android.content.Context.MODE_PRIVATE) }
    val browsePrefs = pickPrefs
    val rootDir = remember(initialDir) {
        initialDir?.takeIf { it.isDirectory } ?: File("/storage/emulated/0")
    }
    val launchDir = remember(initialDir, pickMode) {
        initialDir?.takeIf { !pickMode }
    }
    var currentDir by remember { mutableStateOf(rootDir) }
    val initialRoot = remember(rootDir) {
        val abs = rootDir.absolutePath
        places.filter { isWithin(rootDir, it.dir) }.maxByOrNull { it.dir.absolutePath.length }?.let { return@remember it.dir }
        val internal = "/storage/emulated/0"
        val vol = when {
            abs == internal || abs.startsWith("$internal/") -> File(internal)
            abs.startsWith("/storage/") -> {
                val name = abs.removePrefix("/storage/").substringBefore('/')
                if (name.isNotEmpty() && name != "emulated" && name != "self") File("/storage/$name") else rootDir
            }
            else -> {
                val appFiles = runCatching { context.filesDir.canonicalPath }.getOrNull()
                generateSequence(rootDir) { it.parentFile }
                    .firstOrNull { appFiles != null && runCatching { it.canonicalPath }.getOrNull() == appFiles }
                    ?: rootDir
            }
        }
        vol
    }
    var currentRoot by remember { mutableStateOf(initialRoot) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var selectedEntry by remember { mutableStateOf<File?>(null) }
    var showMenuFor by remember { mutableStateOf<File?>(null) }
    var clipboardFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingConflict by remember { mutableStateOf<File?>(null) }
    var conflictChoice by remember { mutableStateOf<ConflictChoice?>(null) }
    var conflictApplyToAll by remember { mutableStateOf(false) }
    var operationJob by remember { mutableStateOf<Job?>(null) }
    var cancelledJob by remember { mutableStateOf<Job?>(null) }
    var pendingNavigation by remember { mutableStateOf<FileManagerNavigation?>(null) }
    var cancellingNavigation by remember { mutableStateOf(false) }
    var pendingBulkDelete by remember { mutableStateOf<List<File>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val visibleEntries = if (searchQuery.isBlank()) entries
    else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    var showSearch by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(browsePrefs.getString("fmSortBy", "name") ?: "name") }
    var sortDesc by remember { mutableStateOf(browsePrefs.getBoolean("fmSortDesc", false)) }
    var showHidden by remember { mutableStateOf(browsePrefs.getBoolean("fmShowHidden", true)) }
    var showSortMenu by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(browsePrefs.getBoolean("fmGridView", true)) }
    var itemSize by remember { mutableIntStateOf(browsePrefs.getInt("fmItemSize", 1)) }
    var counts by remember { mutableStateOf<Map<String, FolderCounts>>(emptyMap()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var propertiesTarget by remember { mutableStateOf<File?>(null) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf("") }
    var operationDeterminate by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val pullState = rememberPullToRefreshState()

    var favTick by remember { mutableIntStateOf(0) }

    var listingJob by remember { mutableStateOf<Job?>(null) }
    val firstItem = remember { FocusRequester() }
    val chooseFocus = remember { FocusRequester() }
    val upFocus = remember { FocusRequester() }
    val itemFocus = remember { HashMap<String, FocusRequester>() }
    val history = remember { mutableStateListOf<Pair<File, File>>() }
    var shownRoot by remember { mutableStateOf(initialRoot) }

    /** [land] moves the focus once the listing is in: to that entry's path, or to the first entry when blank. */
    fun loadDirectory(dir: File, resetScroll: Boolean = true, record: Boolean = true, land: String? = null) {
        if (cancellingNavigation) return
        if (record && dir != currentDir) {
            history.add(currentDir to shownRoot)
            if (history.size > HISTORY_LIMIT) history.removeAt(0)
        }
        currentDir = dir
        shownRoot = currentRoot
        if (pickMode) pickPrefs.edit().putString("lastFilePickerDir", dir.absolutePath).apply()
        listingJob?.cancel()
        listingJob = scope.launch {
            val list = withContext(Dispatchers.IO) {
                dir.listFiles()?.toList()
                    ?.filter { if (pickDirMode) it.isDirectory else !pickMode || it.isDirectory || matchesPickExt(it) }
                    ?.filter { (showHidden || !it.name.startsWith(".")) && browsable(it) }
                    ?.sortedWith(comparatorFor(sortBy, sortDesc)) ?: emptyList()
            }
            if (currentDir != dir) return@launch
            entries = list
            if (land != null) {
                val index = list.indexOfFirst { it.absolutePath == land }.coerceAtLeast(0)
                repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                if (index > 0) { if (gridView) gridState.scrollToItem(index) else listState.scrollToItem(index) }
                else if (resetScroll) { if (gridView) gridState.scrollToItem(0) else listState.scrollToItem(0) }
                val target = list.getOrNull(index)?.absolutePath
                com.droiddeck.launcher.ui.focusSoon {
                    when {
                        target != null -> itemFocus[target] ?: FocusRequester.Default
                        pickDirMode && dir.canRead() && pickAllowed(dir) -> chooseFocus
                        else -> upFocus
                    }
                }
            }
            val hidden = showHidden
            counts = withContext(Dispatchers.IO) {
                list.filter { it.isDirectory }.take(400).associate { it.absolutePath to countFolder(it, hidden, ::isGameFile) }
            }
            if (resetScroll && land == null) { if (gridView) gridState.scrollToItem(0) else listState.scrollToItem(0) }
        }
    }

    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            loadDirectory(currentDir, resetScroll = false)
            pullState.endRefresh()
        }
    }

    fun openDrive(dir: File) {
        if (cancellingNavigation) return
        if (!dir.exists()) dir.mkdirs()
        currentRoot = dir
        loadDirectory(dir)
    }

    fun returnToStart() {
        if (cancellingNavigation) return
        val dir = launchDir ?: return
        if (!dir.isDirectory) {
            Toast.makeText(context, R.string.fm_start_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        showSearch = false
        searchQuery = ""
        currentRoot = initialRoot
        loadDirectory(dir)
    }

    LaunchedEffect(Unit) { loadDirectory(rootDir) }

    val canGoUp = currentDir != currentRoot && currentDir.parentFile?.exists() == true
    fun isOperationActive() = isOperationRunning || operationJob?.isCompleted == false || cancelledJob?.isCompleted == false
    val operationActive = isOperationActive()

    fun navigate(action: FileManagerNavigation) {
        if (cancellingNavigation) return
        when (action) {
            FileManagerNavigation.EXIT -> onExit?.invoke()
            FileManagerNavigation.RETURN_TO_START -> returnToStart()
            FileManagerNavigation.BACK -> {
                run {
                    while (history.isNotEmpty() && !history.last().first.isDirectory) history.removeAt(history.lastIndex)
                    val previous = history.lastOrNull()
                    if (previous != null && backExitsAtRootHop(currentDir, currentRoot, previous)) onExit?.invoke()
                    else if (previous != null) {
                        history.removeLastOrNull()
                        currentRoot = previous.second
                        loadDirectory(previous.first, record = false, land = currentDir.absolutePath)
                    } else if (canNavigateUp(currentDir, currentRoot)) currentDir.parentFile?.let { loadDirectory(it, record = false, land = currentDir.absolutePath) }
                    else onExit?.invoke()
                }
            }
            FileManagerNavigation.UP -> {
                if (canNavigateUp(currentDir, currentRoot)) currentDir.parentFile?.let { loadDirectory(it, land = currentDir.absolutePath) }
                else onExit?.invoke()
            }
        }
    }

    fun requestNavigation(action: FileManagerNavigation) {
        if (cancellingNavigation) return
        if (isOperationActive()) pendingNavigation = action else navigate(action)
    }

    BackHandler(enabled = operationActive || cancellingNavigation || currentDir != currentRoot || history.isNotEmpty() || onExit != null) {
        requestNavigation(FileManagerNavigation.BACK)
    }

    if (pendingNavigation != null) {
        OutlinedAlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text(stringResource(R.string.fm_cancel_operation_title)) },
            text = { Text(stringResource(R.string.fm_cancel_operation_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingNavigation ?: return@TextButton
                    pendingNavigation = null
                    val job = operationJob?.takeUnless(Job::isCompleted)
                        ?: cancelledJob?.takeUnless(Job::isCompleted)
                    if (job == null) {
                        navigate(action)
                    } else {
                        cancellingNavigation = true
                        cancelledJob = job
                        job.cancel()
                        scope.launch {
                            job.join()
                            if (operationJob === job) operationJob = null
                            if (cancelledJob === job) cancelledJob = null
                            pendingConflict = null
                            isOperationRunning = false
                            operationDeterminate = false
                            cancellingNavigation = false
                            if (action != FileManagerNavigation.EXIT) loadDirectory(currentDir, resetScroll = false)
                            navigate(action)
                        }
                    }
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.fm_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNavigation = null }, modifier = Modifier.focusGlide(CircleShape)) {
                    Text(stringResource(R.string.fm_decline))
                }
            },
        )
    }

    fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!java.nio.file.Files.exists(candidate.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (java.nio.file.Files.exists(candidate.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        return candidate
    }

    fun performDelete(file: File) {
        if (isOperationActive()) return
        operationJob = scope.launch {
            isOperationRunning = true
            operationLabel = context.getString(R.string.fm_op_deleting)
            val job = coroutineContext[Job]
            val ok = withContext(Dispatchers.IO) { FileOps.delete(file) { job?.isActive == false } }
            isOperationRunning = false
            if (operationJob === job) operationJob = null
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, R.string.fm_delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun askConflict(file: File): ConflictChoice? {
        pendingConflict = file
        conflictChoice = null
        while (pendingConflict != null && conflictChoice == null) kotlinx.coroutines.delay(50)
        return conflictChoice
    }

    fun performPaste() {
        val sources = clipboardFiles
        if (sources.isEmpty() || isOperationActive()) return
        val dstDir = currentDir
        val cut = isCutOperation

        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = true
            operationLabel = context.getString(if (cut) R.string.fm_op_moving else R.string.fm_op_copying)
            isOperationRunning = true
            val job = coroutineContext[Job]

            var applyToAll: ConflictChoice? = null
            var failed = 0
            var skipped = 0
            var done = 0

            for (src in sources) {
                if (src.isDirectory && isWithin(dstDir, src)) {
                    failed++
                    continue
                }
                if (cut && src.parentFile?.absolutePath == dstDir.absolutePath) {
                    skipped++
                    continue
                }

                var dst = File(dstDir, src.name)
                if (java.nio.file.Files.exists(dst.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    val choice = applyToAll ?: askConflict(src)?.also {
                        if (conflictApplyToAll) applyToAll = it
                    } ?: run { skipped++; null } ?: continue
                    when (choice) {
                        ConflictChoice.OVERWRITE, ConflictChoice.MERGE -> Unit
                        ConflictChoice.KEEP_BOTH -> dst = uniqueDestination(dstDir, src.name)
                        ConflictChoice.SKIP -> { skipped++; continue }
                    }
                }

                operationLabel = if (sources.size > 1) {
                    context.getString(if (cut) R.string.fm_op_moving_batch else R.string.fm_op_copying_batch, done + 1, sources.size, src.name)
                } else {
                    context.getString(if (cut) R.string.fm_op_moving_item else R.string.fm_op_copying_item, src.name)
                }
                var lastPct = -1
                val onProgress = FileOps.ProgressCallback { copied, total ->
                    job?.ensureActive()
                    val pct = if (total > 0) ((copied * 100) / total).toInt() else 100
                    if (pct != lastPct) {
                        lastPct = pct
                        operationProgress = pct / 100f
                    }
                }
                val target = dst
                val ok = withContext(Dispatchers.IO) {
                    if (cut) FileOps.moveWithProgress(src, target, onProgress) { job?.isActive == false }
                    else FileOps.copyWithProgress(src, target, onProgress) { job?.isActive == false }
                }
                if (ok) done++ else failed++
            }

            isOperationRunning = false
            operationDeterminate = false
            if (operationJob === job) operationJob = null
            clipboardFiles = emptyList()
            isCutOperation = false
            selectionMode = false
            selectedPaths = emptySet()
            loadDirectory(currentDir, resetScroll = false)

            val message = when {
                failed > 0 -> context.getString(R.string.fm_paste_failed, done, failed)
                skipped > 0 -> context.getString(R.string.fm_paste_skipped, done, skipped)
                sources.size > 1 -> context.resources.getQuantityString(if (cut) R.plurals.fm_paste_moved else R.plurals.fm_paste_copied, done, done)
                else -> null
            }
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun performRename(file: File, newName: String) {
        if (isOperationActive()) return
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            Toast.makeText(context, context.getString(R.string.fm_already_exists, newName), Toast.LENGTH_SHORT).show()
            return
        }
        operationJob = scope.launch {
            val job = coroutineContext[Job]
            isOperationRunning = true
            operationLabel = context.getString(R.string.fm_op_renaming)
            val ok = withContext(Dispatchers.IO) { file.renameTo(target) }
            isOperationRunning = false
            if (operationJob === job) operationJob = null
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, R.string.fm_rename_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun createFolder(parent: File, name: String) {
        if (isOperationActive()) return
        val target = File(parent, name)
        if (target.exists()) {
            Toast.makeText(context, context.getString(R.string.fm_already_exists, name), Toast.LENGTH_SHORT).show()
            return
        }
        operationJob = scope.launch {
            val job = coroutineContext[Job]
            isOperationRunning = true
            operationLabel = context.getString(R.string.fm_op_creating_folder)
            val ok = withContext(Dispatchers.IO) { target.mkdirs() }
            isOperationRunning = false
            if (operationJob === job) operationJob = null
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, R.string.fm_create_folder_failed, Toast.LENGTH_SHORT).show()
        }
    }

    var storageTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drives = remember(storageTick) { StorageRoots.list(context) }
    var accessDrive by remember { mutableStateOf<String?>(null) }
    fun unreadable(drive: StorageRoot) {
        if (AllFilesAccess.missing()) accessDrive = drive.label
        else Toast.makeText(context, context.getString(R.string.fm_drive_not_readable, drive.label), Toast.LENGTH_SHORT).show()
    }
    accessDrive?.let { label ->
        OutlinedAlertDialog(
            onDismissRequest = { accessDrive = null },
            title = { Text(stringResource(R.string.fm_drive_access_title, label)) },
            text = { Text(stringResource(R.string.fm_drive_access_text)) },
            confirmButton = {
                TextButton(onClick = {
                    accessDrive = null
                    AllFilesAccess.open(context)
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.upd_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { accessDrive = null }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    val linuxRoot = remember(storageTick) {
        com.droiddeck.launcher.runtime.LinuxRuntime.rootDir(context).takeIf { File(it, "usr").isDirectory }
    }


    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf(suggestedFolderName.orEmpty()) }
        OutlinedAlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.fm_new_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.fm_folder_name)) },
                    singleLine = true,
                    modifier = Modifier.focusGlide(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    if (folderName.isNotBlank()) createFolder(currentDir, folderName)
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.fm_create)) }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf(renameTarget?.name ?: "") }
        OutlinedAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.fm_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.fm_new_name)) },
                    singleLine = true,
                    modifier = Modifier.focusGlide(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = renameTarget
                    renameTarget = null
                    if (file != null && newName.isNotBlank()) performRename(file, newName)
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.fm_rename)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    propertiesTarget?.let { file ->
        FilePropertiesDialog(
            file = file,
            onDismiss = { propertiesTarget = null },
            onChanged = { loadDirectory(currentDir, resetScroll = false) },
        )
    }

    if (selectedEntry != null && selectedEntry != showMenuFor) {
        val file = selectedEntry ?: return
        OutlinedAlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text(stringResource(R.string.fm_delete_title)) },
            text = { Text(stringResource(R.string.fm_delete_text, file.name)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedEntry = null
                    performDelete(file)
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { selectedEntry = null }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (pendingBulkDelete.isNotEmpty()) {
        val victims = pendingBulkDelete
        OutlinedAlertDialog(
            onDismissRequest = { pendingBulkDelete = emptyList() },
            title = { Text(pluralStringResource(R.plurals.fm_delete_items_title, victims.size, victims.size)) },
            text = {
                Column {
                    Text(stringResource(R.string.fm_cannot_undo))
                    Spacer(Modifier.height(6.dp))
                    victims.take(5).forEach {
                        Text("• ${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (victims.size > 5) {
                        Text(
                            pluralStringResource(R.plurals.fm_and_more, victims.size - 5, victims.size - 5),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isOperationActive()) return@TextButton
                    pendingBulkDelete = emptyList()
                    selectionMode = false
                    selectedPaths = emptySet()
                    operationJob = scope.launch {
                        isOperationRunning = true
                        val job = coroutineContext[Job]
                        var failed = 0
                        victims.forEachIndexed { i, f ->
                            operationLabel = context.getString(R.string.fm_op_deleting_batch, i + 1, victims.size, f.name)
                            if (!withContext(Dispatchers.IO) { FileOps.delete(f) { job?.isActive == false } }) failed++
                        }
                        isOperationRunning = false
                        if (operationJob === job) operationJob = null
                        loadDirectory(currentDir, resetScroll = false)
                        if (failed > 0) {
                            Toast.makeText(context, context.resources.getQuantityString(R.plurals.fm_delete_some_failed, failed, failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = emptyList() }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    pendingConflict?.let { conflict ->
        val isDir = conflict.isDirectory
        OutlinedAlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text(stringResource(R.string.fm_already_exists, conflict.name)) },
            text = {
                Column {
                    Text(
                        if (isDir) stringResource(R.string.fm_conflict_merge_hint)
                        else stringResource(R.string.fm_conflict_overwrite_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (clipboardFiles.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = conflictApplyToAll,
                                onCheckedChange = { conflictApplyToAll = it },
                                modifier = Modifier.focusGlide(),
                            )
                            Text(stringResource(R.string.fm_conflict_apply_all), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        (if (isDir) ConflictChoice.MERGE else ConflictChoice.OVERWRITE) to
                            (if (isDir) stringResource(R.string.fm_conflict_merge) else stringResource(R.string.fm_conflict_overwrite)),
                        ConflictChoice.KEEP_BOTH to stringResource(R.string.fm_conflict_keep_both),
                        ConflictChoice.SKIP to stringResource(R.string.fm_conflict_skip),
                    ).forEach { (choice, label) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth().focusGlide(CircleShape),
                            onClick = { conflictChoice = choice; pendingConflict = null },
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conflictChoice = ConflictChoice.SKIP; pendingConflict = null }, modifier = Modifier.focusGlide(CircleShape)) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val narrow = LocalConfiguration.current.screenWidthDp < 700
    val pal = com.droiddeck.launcher.ui.LocalPalette.current
    val colors = MaterialTheme.colorScheme
    val size = ExplorerSize.steps[itemSize.coerceIn(0, ExplorerSize.steps.lastIndex)]
    val isGame: (File) -> Boolean = { if (lowerExts.isNotEmpty()) matchesPickExt(it) else isGameFile(it) }

    fun toggleFavorite(file: File) {
        val now = FavoritesStore.toggle(context, file.absolutePath)
        favTick++
        Toast.makeText(context, context.getString(if (now) R.string.fm_favorite_added else R.string.fm_favorite_removed, file.name), Toast.LENGTH_SHORT).show()
    }

    fun toggleSelected(file: File) {
        selectedPaths = if (file.absolutePath in selectedPaths) selectedPaths - file.absolutePath else selectedPaths + file.absolutePath
    }

    fun open(file: File) {
        when {
            selectionMode -> toggleSelected(file)
            file.isDirectory -> loadDirectory(file, land = "")
            pickMode && !pickDirMode && matchesPickExt(file) -> {
                pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                onPick?.invoke(file)
            }
        }
    }

    val internalDir = File("/storage/emulated/0")
    val usage = remember(storageTick, drives) { (listOf(internalDir) + drives.map { it.dir }).associate { it.absolutePath to volumeUsage(it) } }
    fun here(dir: File) = currentRoot.absolutePath == dir.absolutePath
    val storageItems = buildList {
        add(SideItem(stringResource(R.string.fm_drive_internal), Icons.Filled.Smartphone, here(internalDir), internalDir, usage[internalDir.absolutePath]) { openDrive(internalDir) })
        drives.filter { it.removable }.forEach { d ->
            add(SideItem(d.label, Icons.Filled.SdStorage, here(d.dir), d.dir, usage[d.dir.absolutePath]) { if (d.readable) openDrive(d.dir) else unreadable(d) })
        }
        linuxRoot?.let { add(SideItem(stringResource(R.string.fm_drive_linux), Icons.Filled.Terminal, here(it), it) { openDrive(it) }) }
    }
    val quickItems = buildList {
        places.forEach { pl -> add(SideItem(pl.label, pl.icon, here(pl.dir), pl.dir) { openDrive(pl.dir) }) }
        File("/storage/emulated/0/Download").takeIf { it.isDirectory }?.let { add(SideItem(stringResource(R.string.fm_rail_downloads), Icons.Filled.Download, here(it), it) { openDrive(it) }) }
        SessionPrefs.romsDirs(context).map(::File).filter { it.isDirectory }.forEach { d ->
            add(SideItem(if (SessionPrefs.romsDirs(context).size > 1) "${stringResource(R.string.fm_rail_roms)} · ${d.name}" else stringResource(R.string.fm_rail_roms), Icons.Filled.SportsEsports, here(d), d) { openDrive(d) })
        }
        com.droiddeck.launcher.runtime.LinuxRuntime.logDir(context).takeIf { it.isDirectory }?.let { add(SideItem(stringResource(R.string.fm_rail_session_logs), Icons.Filled.Description, here(it), it) { openDrive(it) }) }
        File("/storage/emulated/0/Pictures").takeIf { it.isDirectory }?.let { add(SideItem(stringResource(R.string.fm_rail_pictures), Icons.Filled.Image, here(it), it) { openDrive(it) }) }
    }.distinctBy { it.dir?.absolutePath }
    val favItems = remember(favTick) { FavoritesStore.list(context).map(::File).filter { it.exists() } }
        .map { d -> SideItem(d.name, Icons.Filled.Star, here(d), d) { openDrive(d) } }
    val sideItems = storageItems + quickItems + favItems
    val rootLabel = sideItems.firstOrNull { it.dir?.absolutePath == currentRoot.absolutePath }?.label ?: currentRoot.name.ifEmpty { currentRoot.path }
    val crumbs = remember(currentDir, currentRoot, rootLabel) {
        val out = ArrayList<Pair<String, File>>()
        var f: File? = currentDir
        while (f != null) {
            if (f.absolutePath == currentRoot.absolutePath) { out += rootLabel to f; break }
            out += f.name.ifEmpty { f.path } to f
            f = f.parentFile
        }
        out.reversed()
    }
    val hereName = crumbs.lastOrNull()?.first ?: currentDir.name
    val title = pickerTitle ?: stringResource(R.string.fm_rail_title)

    LaunchedEffect(Unit) { com.droiddeck.launcher.ui.focusWithinFrames({ false }) { if (pickDirMode) chooseFocus else firstItem } }

    val itemMenu: @Composable (File) -> Unit = { file ->
        if (showMenuFor == file) {
            val isFav = remember(file.absolutePath, favTick) { FavoritesStore.isFavorite(context, file.absolutePath) }
            FileActionMenu(
                true, file, isFav, onDismiss = { showMenuFor = null },
                onSelect = { selectionMode = true; selectedPaths = selectedPaths + file.absolutePath },
                onProperties = { propertiesTarget = file }, onToggleFavorite = { toggleFavorite(file) },
                onRename = { renameTarget = file },
                onCopy = { clipboardFiles = listOf(file); isCutOperation = false },
                onCut = { clipboardFiles = listOf(file); isCutOperation = true },
                onDelete = { selectedEntry = file },
            )
        }
    }
    @Composable
    fun detailOf(file: File): String =
        if (file.isDirectory) folderDetail(counts[file.absolutePath])
        else com.droiddeck.launcher.stores.formatBytes(file.length())

    Box(
        Modifier.fillMaxSize().background(colors.background)
            .background(Brush.verticalGradient(listOf(pal.signal.copy(alpha = 0.10f), colors.background.copy(alpha = 0f), colors.background))),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = if (narrow) 16.dp else 32.dp, vertical = if (narrow) 14.dp else 24.dp)) {
            if (!narrow) {
                Column(
                    Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.droiddeck.launcher.ui.Eyebrow(title)
                    SideHeading(stringResource(R.string.fp_storage))
                    storageItems.forEach { SideEntry(it) }
                    if (quickItems.isNotEmpty()) {
                        SideHeading(stringResource(R.string.fm_quick_access))
                        quickItems.forEach { SideEntry(it) }
                    }
                    if (favItems.isNotEmpty()) {
                        SideHeading(stringResource(R.string.fm_favorites))
                        favItems.forEach { SideEntry(it) }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (narrow) {
                    com.droiddeck.launcher.ui.Eyebrow(title)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sideItems.forEach { PathPill(it.label, it.selected, it.icon, it.onClick) }
                    }
                }
                val tools: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(Icons.Filled.CreateNewFolder, stringResource(R.string.fm_new_folder), enabled = currentDir.canWrite()) { showNewFolderDialog = true }
                        val fav = remember(currentDir.absolutePath, favTick) { FavoritesStore.isFavorite(context, currentDir.absolutePath) }
                        ToolButton(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(if (fav) R.string.fm_favorite_remove else R.string.fm_favorite_add), active = fav) { toggleFavorite(currentDir) }
                        ToolButton(Icons.Filled.Search, stringResource(R.string.fm_search), active = showSearch) { showSearch = !showSearch; if (!showSearch) searchQuery = "" }
                        var viewMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolButton(if (gridView) Icons.Filled.GridView else Icons.Filled.ViewAgenda, stringResource(R.string.fm_view), active = viewMenu) { viewMenu = true }
                            com.droiddeck.launcher.ui.AnchoredMenu(viewMenu, onDismiss = { viewMenu = false }, title = stringResource(R.string.fm_view), compact = true, minWidth = 220.dp) { first ->
                                listOf(true to R.string.fm_grid_view, false to R.string.fm_list_view).forEachIndexed { i, (grid, label) ->
                                    com.droiddeck.launcher.ui.MenuItem(stringResource(label), checked = gridView == grid, focusRequester = if (i == 0) first else null) {
                                        gridView = grid
                                        browsePrefs.edit().putBoolean("fmGridView", gridView).apply()
                                        viewMenu = false
                                    }
                                }
                                listOf(R.string.fm_size_small, R.string.fm_size_medium, R.string.fm_size_large).forEachIndexed { i, label ->
                                    com.droiddeck.launcher.ui.MenuItem(stringResource(label), checked = itemSize == i) {
                                        itemSize = i
                                        browsePrefs.edit().putInt("fmItemSize", itemSize).apply()
                                        viewMenu = false
                                    }
                                }
                            }
                        }
                        Box {
                            ToolButton(Icons.Filled.Sort, stringResource(R.string.fm_sort), active = showSortMenu) { showSortMenu = true }
                            com.droiddeck.launcher.ui.AnchoredMenu(showSortMenu, onDismiss = { showSortMenu = false }, title = stringResource(R.string.fm_sort), compact = true, minWidth = 240.dp) { first ->
                                listOf("name" to R.string.fm_sort_name, "date" to R.string.fm_sort_date, "size" to R.string.fm_prop_size).forEachIndexed { i, (key, label) ->
                                    com.droiddeck.launcher.ui.MenuItem(
                                        stringResource(label) + if (sortBy == key) (if (sortDesc) "  ↓" else "  ↑") else "", checked = sortBy == key,
                                        focusRequester = if (i == 0) first else null,
                                    ) {
                                        if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = false }
                                        browsePrefs.edit().putString("fmSortBy", sortBy).putBoolean("fmSortDesc", sortDesc).apply()
                                        showSortMenu = false
                                        loadDirectory(currentDir, resetScroll = false)
                                    }
                                }
                                com.droiddeck.launcher.ui.MenuItem(stringResource(R.string.fm_show_hidden), checked = showHidden) {
                                    showHidden = !showHidden
                                    browsePrefs.edit().putBoolean("fmShowHidden", showHidden).apply()
                                    showSortMenu = false
                                    loadDirectory(currentDir, resetScroll = false)
                                }
                                com.droiddeck.launcher.ui.MenuItem(stringResource(R.string.fm_select), checked = selectionMode) {
                                    selectionMode = !selectionMode
                                    if (!selectionMode) selectedPaths = emptySet()
                                    showSortMenu = false
                                }
                            }
                        }
                    }
                }
                if (narrow) Box(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) { tools() }
                else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { tools() }
                Breadcrumbs(crumbs, { loadDirectory(it) }, Modifier.fillMaxWidth().padding(top = 12.dp))
                val folderCount = entries.count { it.isDirectory }
                val gameCount = entries.count { it.isFile && isGame(it) }
                val fileCount = entries.count { it.isFile }
                val free = remember(currentDir.absolutePath, entries) { runCatching { currentDir.usableSpace }.getOrDefault(0L) }
                Text(
                    if (!currentDir.canRead()) stringResource(R.string.fp_unreadable)
                    else listOfNotNull(
                        stringResource(R.string.fp_folders, folderCount),
                        stringResource(R.string.fp_games, gameCount).takeIf { gameCount > 0 },
                        stringResource(R.string.fp_files, fileCount).takeIf { gameCount == 0 && fileCount > 0 },
                        stringResource(R.string.fm_free_space, com.droiddeck.launcher.stores.formatBytes(free)).takeIf { free > 0 },
                    ).joinToString(" · "),
                    fontSize = 14.sp, color = if (currentDir.canRead()) colors.onSurfaceVariant else colors.error,
                    modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.fm_filter_folder), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).focusGlide(RoundedCornerShape(30.dp)),
                    )
                }
                val bar: @Composable (@Composable () -> Unit) -> Unit = { content ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).glass(RoundedCornerShape(30.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) { CompositionLocalProvider(com.droiddeck.launcher.ui.LocalPillButtons provides true) { content() } }
                }
                if (selectionMode) bar {
                    Text(stringResource(R.string.fm_selected_count, selectedPaths.size), color = colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val allSelected = visibleEntries.isNotEmpty() && visibleEntries.all { it.absolutePath in selectedPaths }
                    val anySelected = visibleEntries.any { it.absolutePath in selectedPaths }
                    SecondaryButton(stringResource(if (allSelected) R.string.fm_select_none else R.string.fm_select_all), compact = true) {
                        val shown = visibleEntries.map { it.absolutePath }
                        selectedPaths = if (allSelected) selectedPaths - shown.toSet() else selectedPaths + shown
                    }
                    SecondaryButton(stringResource(R.string.fm_copy), enabled = anySelected, compact = true) {
                        clipboardFiles = visibleEntries.filter { it.absolutePath in selectedPaths }; isCutOperation = false
                        selectionMode = false; selectedPaths = emptySet()
                    }
                    SecondaryButton(stringResource(R.string.fm_cut), enabled = anySelected, compact = true) {
                        clipboardFiles = visibleEntries.filter { it.absolutePath in selectedPaths }; isCutOperation = true
                        selectionMode = false; selectedPaths = emptySet()
                    }
                    SecondaryButton(stringResource(R.string.common_delete), enabled = anySelected, compact = true) {
                        pendingBulkDelete = visibleEntries.filter { it.absolutePath in selectedPaths }
                    }
                    SecondaryButton(stringResource(R.string.fm_done), compact = true) { selectionMode = false; selectedPaths = emptySet() }
                }
                if (clipboardFiles.isNotEmpty()) bar {
                    Icon(Icons.Filled.ContentPaste, null, tint = pal.signal, modifier = Modifier.size(18.dp))
                    val what = if (clipboardFiles.size == 1) clipboardFiles.first().name
                    else pluralStringResource(R.plurals.fm_items, clipboardFiles.size, clipboardFiles.size)
                    Text(
                        if (isCutOperation) stringResource(R.string.fm_paste_move_here, what) else stringResource(R.string.fm_paste_here, what),
                        color = colors.onBackground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    PrimaryButton(stringResource(R.string.fm_paste), enabled = !isOperationRunning, compact = true) { performPaste() }
                    SecondaryButton(stringResource(R.string.common_cancel), compact = true) { clipboardFiles = emptyList(); isCutOperation = false }
                }
                if (isOperationRunning) bar {
                    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pct = if (operationDeterminate) "  ${(operationProgress * 100).toInt()}%" else ""
                        Text("$operationLabel$pct", color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (operationDeterminate) LinearProgressIndicator(progress = { operationProgress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = pal.signal, trackColor = pal.line)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), color = pal.signal, trackColor = pal.line)
                    }
                    if (operationJob != null) SecondaryButton(stringResource(R.string.common_cancel), compact = true) {
                        cancelledJob = operationJob
                        operationJob?.cancel()
                        operationJob = null
                        isOperationRunning = false
                        operationDeterminate = false
                        loadDirectory(currentDir, resetScroll = false)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(pullState.nestedScrollConnection)) {
                    val shown = visibleEntries
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxWidth()) { com.droiddeck.launcher.ui.Note(stringResource(R.string.fm_empty_directory)) }
                    } else if (gridView) {
                        val gridNav = com.droiddeck.launcher.ui.rememberLazyGridNav(gridState, 12.dp)
                        LazyVerticalGrid(
                            GridCells.Adaptive(size.gridMin), Modifier.fillMaxSize(), state = gridState,
                            contentPadding = PaddingValues(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(shown, key = { _, f -> f.absolutePath }) { i, file ->
                                val own = remember { FocusRequester() }
                                val req = if (i == 0) firstItem else own
                                TrackFocus(itemFocus, file.absolutePath, req)
                                ExplorerCard(
                                    file, detailOf(file), size, file.absolutePath in selectedPaths, selectionMode, !file.canRead(),
                                    null, onOpen = { open(file) }, onMenu = { showMenuFor = file },
                                    modifier = Modifier.lazyGridItem(gridNav, i, shown.size, req),
                                ) { itemMenu(file) }
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(shown, key = { _, f -> f.absolutePath }) { i, file ->
                                val own = remember { FocusRequester() }
                                val req = if (i == 0) firstItem else own
                                TrackFocus(itemFocus, file.absolutePath, req)
                                val date = remember(file.absolutePath) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(Date(file.lastModified())) }
                                ExplorerRow(
                                    file, detailOf(file), date, size, file.absolutePath in selectedPaths, selectionMode, !file.canRead(),
                                    req, onOpen = { open(file) }, onMenu = { showMenuFor = file },
                                ) { itemMenu(file) }
                            }
                        }
                    }
                    if (pullState.verticalOffset > 0.5f || pullState.isRefreshing) {
                        PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).glass(RoundedCornerShape(30.dp)).padding(6.dp),
                ) {
                    CompositionLocalProvider(com.droiddeck.launcher.ui.LocalPillButtons provides true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            if (pickDirMode) PrimaryButton(
                                stringResource(R.string.fp_choose_named, hereName), enabled = currentDir.canRead() && pickAllowed(currentDir), main = true,
                                icon = Icons.Filled.Check, modifier = Modifier.focusRequester(chooseFocus),
                            ) {
                                pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                                onPick?.invoke(currentDir)
                            }
                            if (onAddAll != null && entries.any { it.isDirectory && it.canRead() }) {
                                PrimaryButton(stringResource(R.string.games_add_all), main = !pickDirMode, icon = Icons.Filled.Add) { onAddAll(currentDir, showHidden) }
                            }
                            if (canGoUp) SecondaryButton(stringResource(R.string.fm_up_one_level), enabled = !cancellingNavigation, modifier = Modifier.focusRequester(upFocus)) { requestNavigation(FileManagerNavigation.UP) }
                            if (launchDir != null) SecondaryButton(stringResource(R.string.fm_return_to_start), enabled = !cancellingNavigation) { requestNavigation(FileManagerNavigation.RETURN_TO_START) }
                            if (onExit != null) SecondaryButton(stringResource(if (pickMode) R.string.common_cancel else R.string.common_close), enabled = !cancellingNavigation) {
                                requestNavigation(FileManagerNavigation.EXIT)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A file that counts as a game in folder summaries: one of the ROM formats DroidDeck recognises. */
internal fun isGameFile(file: File): Boolean = file.extension.lowercase() in com.droiddeck.launcher.frontend.RomPlatforms.knownExtensions

@Composable
private fun TrackFocus(map: HashMap<String, FocusRequester>, path: String, requester: FocusRequester) {
    DisposableEffect(path, requester) {
        map[path] = requester
        onDispose { if (map[path] === requester) map.remove(path) }
    }
}
