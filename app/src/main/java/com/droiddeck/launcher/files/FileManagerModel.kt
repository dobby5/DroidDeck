package com.droiddeck.launcher.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.StringRes
import com.droiddeck.launcher.R
import java.io.File

enum class ConflictChoice { OVERWRITE, MERGE, KEEP_BOTH, SKIP }

internal fun comparatorFor(sortBy: String, desc: Boolean): Comparator<File> {
    val inner: Comparator<File> = when (sortBy) {
        "date" -> compareBy { it.lastModified() }
        "size" -> compareBy { if (it.isDirectory) -1L else it.length() }
        else -> compareBy { it.name.lowercase() }
    }
    val directed = if (desc) inner.reversed() else inner
    return compareBy<File> { if (it.isDirectory) 0 else 1 }.then(directed)
}

internal val IMAGE_THUMB_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

internal fun isWithin(child: File, ancestor: File): Boolean {
    val c = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
    val a = runCatching { ancestor.canonicalPath }.getOrDefault(ancestor.absolutePath)
    return c == a || c.startsWith(a + File.separator)
}
