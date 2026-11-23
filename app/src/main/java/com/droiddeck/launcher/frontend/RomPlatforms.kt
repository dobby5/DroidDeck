package com.droiddeck.launcher.frontend

/**
 * Game systems for the ROMs folder, after EmuStore's Platforms (E:\EmuStore, data/catalog/Platforms.kt).
 * Extensions several systems use (.iso, .bin, .chd, .cue, …) only count together with a folder hint.
 */
object RomPlatforms {
    class Platform(
        val id: String, val name: String, val short: String,
        val uniqueExtensions: Set<String>, val sharedExtensions: Set<String>, val folderHints: Set<String>,
        val libretro: List<String> = emptyList(),
    )

    val all: List<Platform> = listOf(
        Platform("nes", "Nintendo Entertainment System", "NES", setOf("nes", "fds", "unf"), emptySet(), setOf("nes", "famicom", "fds", "nintendoentertainmentsystem"), listOf("Nintendo_-_Nintendo_Entertainment_System")),
        Platform("snes", "Super Nintendo", "SNES", setOf("sfc", "smc"), emptySet(), setOf("snes", "sfc", "superfamicom", "supernintendo"), listOf("Nintendo_-_Super_Nintendo_Entertainment_System")),
        Platform("gb", "Game Boy", "GB", setOf("gb"), emptySet(), setOf("gb", "gameboy"), listOf("Nintendo_-_Game_Boy")),
        Platform("gbc", "Game Boy Color", "GBC", setOf("gbc"), emptySet(), setOf("gbc", "gameboycolor"), listOf("Nintendo_-_Game_Boy_Color")),
        Platform("gba", "Game Boy Advance", "GBA", setOf("gba"), emptySet(), setOf("gba", "gameboyadvance"), listOf("Nintendo_-_Game_Boy_Advance")),
        Platform("n64", "Nintendo 64", "N64", setOf("n64", "z64", "v64"), emptySet(), setOf("n64", "nintendo64"), listOf("Nintendo_-_Nintendo_64")),
        Platform("nds", "Nintendo DS", "DS", setOf("nds", "dsi", "srl", "ids"), emptySet(), setOf("nds", "ds", "ndsi", "nintendods")),
        Platform("3ds", "Nintendo 3DS", "3DS", setOf("3ds", "3dsx", "cci", "cxi", "zcci", "zcxi", "azahar"), emptySet(), setOf("3ds", "n3ds", "nintendo3ds")),
        Platform("gc", "GameCube", "GC", setOf("gcm"), setOf("iso", "rvz", "gcz", "wia", "ciso", "dol"), setOf("gc", "gamecube", "ngc")),
        Platform("wii", "Wii", "Wii", setOf("wbfs", "wad"), setOf("iso", "rvz", "gcz", "wia", "ciso", "dol"), setOf("wii")),
        Platform("wiiu", "Wii U", "Wii U", setOf("wua", "wux", "wud", "rpx"), emptySet(), setOf("wiiu")),
        Platform("switch", "Nintendo Switch", "Switch", setOf("nsp", "xci", "nro", "nca"), emptySet(), setOf("switch", "ns", "nintendoswitch")),
        Platform("switch2", "Nintendo Switch 2", "Switch 2", setOf("nup"), setOf("nsp", "xci", "nro", "nca"), setOf("switch2", "ns2", "nintendoswitch2")),
        Platform("psx", "PlayStation", "PS1", setOf("ecm"), setOf("cue", "bin", "chd", "pbp", "iso", "img", "m3u"), setOf("psx", "ps1", "psone", "playstation")),
        Platform("ps2", "PlayStation 2", "PS2", emptySet(), setOf("iso", "chd", "cso", "bin", "gz"), setOf("ps2", "playstation2")),
        Platform("ps3", "PlayStation 3", "PS3", emptySet(), setOf("iso"), setOf("ps3", "playstation3")),
        Platform("psp", "PlayStation Portable", "PSP", emptySet(), setOf("iso", "cso", "pbp", "chd"), setOf("psp", "playstationportable")),
        Platform("dreamcast", "Dreamcast", "DC", setOf("cdi", "gdi"), setOf("chd", "cue"), setOf("dreamcast", "dc", "segadreamcast")),
        Platform("xbox360", "Xbox 360", "360", setOf("xex", "zar"), setOf("iso"), setOf("xbox360", "x360", "xb360")),
        Platform("megadrive", "Mega Drive / Genesis", "MD", setOf("md", "gen", "smd"), setOf("bin"), setOf("megadrive", "genesis", "md"), listOf("Sega_-_Mega_Drive_-_Genesis")),
        Platform("mastersystem", "Master System", "SMS", setOf("sms"), emptySet(), setOf("mastersystem", "sms"), listOf("Sega_-_Master_System_-_Mark_III")),
        Platform("gamegear", "Game Gear", "GG", setOf("gg"), emptySet(), setOf("gamegear", "gg"), listOf("Sega_-_Game_Gear")),
        Platform("pcengine", "PC Engine / TurboGrafx-16", "PCE", setOf("pce"), emptySet(), setOf("pcengine", "tg16", "turbografx16", "turbografx"), listOf("NEC_-_PC_Engine_-_TurboGrafx_16")),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String?): Platform? = id?.let(byId::get)

    val folderHints: Set<String> = all.flatMap { it.folderHints }.toSet()

    /**
     * The nearest folder with a hint decides; short hints must be a whole word, long ones (6+ characters) may be part of
     * the name, and the longest hint wins ("gameboyadvance" beats "gameboy"). Without a hint only an extension that
     * belongs to exactly one system counts. Null when unsure.
     */
    fun detect(fileName: String, parentFolders: List<String>): Platform? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        for (folder in parentFolders.asReversed()) {
            val lower = folder.lowercase()
            val compact = lower.replace(Regex("[^a-z0-9]"), "")
            val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
            val hinted = all.mapNotNull { p ->
                p.folderHints.filter { h -> h == compact || h in tokens || (h.length >= 6 && compact.contains(h)) }.maxOfOrNull { it.length }?.let { p to it }
            }.sortedByDescending { it.second }.map { it.first }
            hinted.firstOrNull { ext in it.uniqueExtensions || ext in it.sharedExtensions }?.let { return it }
        }
        return all.filter { ext in it.uniqueExtensions }.singleOrNull()
    }

    /** The system a folder names, for game folders such as a PS3 disc dump. */
    fun hinted(parentFolders: List<String>): Platform? = parentFolders.asReversed().firstNotNullOfOrNull { folder ->
        val compact = folder.lowercase().replace(Regex("[^a-z0-9]"), "")
        all.firstOrNull { compact in it.folderHints }
    }

    val knownExtensions: Set<String> = all.flatMap { it.uniqueExtensions + it.sharedExtensions }.toSet()
}
