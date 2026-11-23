package com.droiddeck.launcher.frontend

import android.content.Context
import java.io.File

/**
 * Switch updates and DLC belong to their game: they leave the game list and show on the game's page. One whose
 * game is not in the ROM folders stays listed, so no file disappears.
 */
object SwitchAddons {
    class Addon(val file: File, val update: Boolean)

    private val known = HashMap<String, SwitchTitles.Id?>()

    private fun key(file: File) = "${file.path}|${file.length()}|${file.lastModified()}"

    private fun id(context: Context, file: File): SwitchTitles.Id? = synchronized(known) {
        val k = key(file)
        if (known.containsKey(k)) return known[k]
        return SwitchTitles.identify(context, file).also { known[k] = it }
    }

    fun group(context: Context, roms: List<Library.Rom>): List<Library.Rom> = group(roms) { id(context, it) }

    internal fun group(roms: List<Library.Rom>, identify: (File) -> SwitchTitles.Id?): List<Library.Rom> {
        val switch = roms.filter { it.system == "switch" || it.system == "switch2" }
        if (switch.isEmpty()) return roms
        val ids = switch.associateWith { identify(it.hostPath) }
        val bases = switch.mapNotNull { rom -> ids[rom]?.takeIf { it.kind == SwitchTitles.Kind.GAME }?.titleId?.let { (rom.system to it) to rom } }
            .distinctBy { it.first }.toMap()
        val addons = HashMap<Library.Rom, MutableList<Addon>>()
        val hidden = HashSet<Library.Rom>()
        for (rom in switch) {
            val id = ids[rom]?.takeIf { it.kind != SwitchTitles.Kind.GAME } ?: continue
            val base = bases[rom.system to id.baseId] ?: continue
            addons.getOrPut(base) { ArrayList() }.add(Addon(rom.hostPath, id.kind == SwitchTitles.Kind.UPDATE))
            hidden += rom
        }
        return roms.filter { it !in hidden }.map { rom ->
            val list = addons[rom] ?: return@map rom
            rom.with(addons = list.sortedWith(
                compareByDescending<Addon> { it.update }
                    .thenByDescending { if (it.update) SwitchTitles.updateVersion(it.file.name) else 0L }
                    .thenBy { it.file.name.lowercase() },
            ))
        }
    }
}
