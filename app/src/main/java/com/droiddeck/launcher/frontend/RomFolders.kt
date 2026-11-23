package com.droiddeck.launcher.frontend

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File
import java.security.MessageDigest

/**
 * The ROMs folders every emulator shares, and where the Linux session sees them. The first is /root/ROMs, as before;
 * a later folder inside it appears below it, and any other folder (on the SD card, say) gets its own mount under /mnt/roms.
 */
object RomFolders {
    const val GUEST_ROOT = "/root/ROMs"
    const val EXTRA_ROOT = "/mnt/roms"

    fun all(context: Context): List<File> = SessionPrefs.romsDirs(context).map(::File)

    fun guestRoot(primary: File?, folder: File): String {
        val inside = primary?.let { inside(it, folder) }
        if (inside != null) return if (inside.isEmpty()) GUEST_ROOT else "$GUEST_ROOT/$inside"
        return "$EXTRA_ROOT/${key(folder)}"
    }

    /** The binds a session needs so every ROMs folder is reachable: host folder to guest path. */
    fun binds(context: Context): List<Pair<File, String>> = binds(all(context))

    internal fun binds(folders: List<File>): List<Pair<File, String>> {
        val primary = folders.firstOrNull() ?: return emptyList()
        val out = arrayListOf(primary to GUEST_ROOT)
        for (f in folders.drop(1).distinctBy { it.absoluteFile.normalize().path }) {
            if (inside(primary, f) != null) continue
            out += f to guestRoot(primary, f)
        }
        return out.distinctBy { it.second }
    }

    private fun inside(parent: File, child: File): String? {
        val p = parent.absoluteFile.normalize().toPath()
        val c = child.absoluteFile.normalize().toPath()
        return if (c.startsWith(p)) p.relativize(c).toString().replace(File.separatorChar, '/') else null
    }

    internal fun key(folder: File): String {
        val path = folder.absoluteFile.normalize().path
        val name = folder.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "roms" }
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
        return "$name-$hash"
    }
}
