package com.droiddeck.launcher.frontend

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.text.Normalizer

/**
 * Switch and Switch 2 covers without keys or decryption: the title id comes from the file or folder name or the
 * NSP's plain file table, and the bundled titledb index (assets/switch-titles.tsv, MIT) maps it to eShop images.
 */
object SwitchTitles {
    class Title(val id: String, val name: String, val iconHash: String)

    class Index(val byId: Map<String, Title>, val byName: Map<String, Title>)

    private const val ASSET = "switch-titles.tsv"
    private const val HEADER_BYTES = 64 * 1024

    fun imageUrl(hash: String) = "https://img-eshop.cdn.nintendo.net/i/$hash.jpg"

    private val TID = Regex("(?<![0-9A-Fa-f])(0[14][0-9A-Fa-f]{14})(?![0-9A-Fa-f])")
    private val TICKET = Regex("^[0-9A-Fa-f]{32}\\.tik$")

    @Volatile private var loaded: Index? = null

    fun index(context: Context): Index? = loaded ?: synchronized(this) {
        loaded ?: runCatching { context.assets.open(ASSET).use(::parse) }.getOrNull()?.also { loaded = it }
    }

    fun parse(input: InputStream): Index {
        val byId = HashMap<String, Title>(24_000)
        val byName = HashMap<String, Title>(24_000)
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split('\t')
                if (p.size < 3 || p[2].length != 64) continue
                val t = Title(p[0].uppercase(), p[1], p[2])
                byId[t.id] = t
                byName.putIfAbsent(normalize(t.name), t)
            }
        }
        return Index(byId, byName)
    }

    /** Switch 2 ids (04...) only for Switch 2 games and Switch ids (01...) only for Switch games. */
    fun fitsPlatform(titleId: String, platformId: String): Boolean = when (platformId) {
        "switch2" -> titleId.startsWith("04")
        "switch" -> titleId.startsWith("01")
        else -> true
    }

    fun titleIdIn(text: String): String? = TID.find(text)?.groupValues?.get(1)?.uppercase()

    /** Updates (base | 0x800) and add-ons (base + 0x1000 + n) map to their game, as in Eden's GetBaseTitleID. */
    fun baseTitleId(tid: String): String {
        val v = tid.toULongOrNull(16) ?: return tid
        return (v and 0xFFFFFFFFFFFFE000uL).toString(16).uppercase().padStart(16, '0')
    }

    /** Content meta types as Eden names them: Application, Patch and AddOnContent. */
    enum class Kind { GAME, UPDATE, DLC }

    /** Eden's rules: base game = low 13 bits clear, update = base | 0x800, add-on content = base + 0x1000 + n. */
    fun kindOf(tid: String): Kind? {
        val low = (tid.toULongOrNull(16) ?: return null) and 0x1FFFuL
        return when (low) { 0uL -> Kind.GAME; 0x800uL -> Kind.UPDATE; else -> Kind.DLC }
    }

    private fun kindRank(tid: String): Int = kindOf(tid)?.ordinal ?: 9

    /** One file in an NSP's plain PFS0 table; [offset] counts from the start of the NSP. */
    class Entry(val name: String, val offset: Long, val size: Long)

    fun pfs0(header: ByteArray): List<Entry>? {
        if (header.size < 16 || String(header, 0, 4, Charsets.US_ASCII) != "PFS0") return null
        val count = le32(header, 4)
        val strSize = le32(header, 8)
        if (count !in 1..4096 || strSize !in 1..65536) return null
        val strStart = 16 + count * 24
        val dataStart = strStart + strSize
        if (dataStart > header.size) return null
        return (0 until count).map { i ->
            val e = 16 + i * 24
            val nameOff = le32(header, e + 16)
            val name = if (nameOff !in 0 until strSize) "" else
                String(header, strStart + nameOff, strSize - nameOff, Charsets.US_ASCII).substringBefore('\u0000')
            Entry(name, dataStart + le64(header, e), le64(header, e + 8))
        }
    }

    fun pfs0Names(header: ByteArray): List<String>? = pfs0(header)?.map { it.name }

    /** The NSP's .cnmt.xml, a plain copy of the content meta Eden reads from the encrypted .cnmt.nca. */
    fun cnmtXmlEntry(header: ByteArray): Entry? =
        pfs0(header)?.firstOrNull { it.name.endsWith(".cnmt.xml", ignoreCase = true) && it.size in 1..65536 }

    fun parseCnmtXml(xml: String): Pair<String, Kind>? {
        val id = Regex("<Id>\\s*0x([0-9A-Fa-f]{16})\\s*</Id>").find(xml)?.groupValues?.get(1)?.uppercase() ?: return null
        val kind = when (Regex("<Type>\\s*(\\w+)\\s*</Type>").find(xml)?.groupValues?.get(1)) {
            "Application" -> Kind.GAME
            "Patch" -> Kind.UPDATE
            "AddOnContent" -> Kind.DLC
            else -> kindOf(id) ?: return null
        }
        return id to kind
    }

    /** [titleId] may be null when only the base game is known (matched by name). */
    class Id(val titleId: String?, val kind: Kind, val baseId: String)

    private val UPDATE_NAME = Regex("(?i)\\[(upd|update)]|\\bupdate\\b|\\[v[1-9]\\d{3,}]|\\bv\\d+\\.\\d+(\\.\\d+)?\\b")
    private val DLC_NAME = Regex("(?i)\\bdlc\\b|\\[dlc]")

    /**
     * What a Switch file is, decided like Eden but without keys: the content meta first, then the tickets, then a
     * title id in the file or folder name, last the name in the title index (update and DLC words then decide).
     */
    fun identify(fileName: String, path: String, header: ByteArray?, cnmtXml: String?, index: () -> Index?): Id? {
        val candidates = buildList {
            cnmtXml?.let(::parseCnmtXml)?.let { add(it) }
            header?.let { h ->
                pfs0Names(h).orEmpty().filter { TICKET.matches(it) }.map { it.take(16).uppercase() }
                    .forEach { t -> kindOf(t)?.let { add(t to it) } }
            }
        }
        val fromContent = candidates.minByOrNull { it.second.ordinal }
        val tid = fromContent?.first ?: titleIdIn(fileName) ?: titleIdIn(path)
        if (tid != null) {
            val kind = fromContent?.second ?: kindOf(tid) ?: return null
            return Id(tid, kind, if (kind == Kind.GAME) tid else baseTitleId(tid))
        }
        val base = index()?.let { find(it, fileName, path, null, "") }?.id ?: return null
        val name = fileName.substringBeforeLast('.')
        val kind = when { DLC_NAME.containsMatchIn(name) -> Kind.DLC; UPDATE_NAME.containsMatchIn(name) -> Kind.UPDATE; else -> Kind.GAME }
        return Id(if (kind == Kind.GAME) base else null, kind, base)
    }

    fun identify(context: Context, file: File): Id? {
        val header = if (file.extension.equals("nsp", ignoreCase = true)) readHeader(file) else null
        val xml = header?.let(::cnmtXmlEntry)?.let { e ->
            val bytes = if (e.offset + e.size <= header.size) header.copyOfRange(e.offset.toInt(), (e.offset + e.size).toInt())
            else readAt(file, e.offset, e.size.toInt())
            bytes?.toString(Charsets.UTF_8)
        }
        return identify(file.name, file.parent.orEmpty(), header, xml) { index(context) }
    }

    /** Version of an update from its file name ("v2.0.7" or "[v458752]"), for sorting newest first. */
    fun updateVersion(fileName: String): Long {
        Regex("\\bv(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(fileName)?.let { m ->
            return m.groupValues[1].toLong() * 1_000_000 + m.groupValues[2].toLong() * 1_000 + (m.groupValues[3].toLongOrNull() ?: 0)
        }
        return Regex("\\[v(\\d+)]").find(fileName)?.groupValues?.get(1)?.toLongOrNull()?.let { it shr 16 } ?: 0L
    }

    /** "2.0.7", "v458752" or null, as an update's file name writes it. */
    fun updateVersionText(fileName: String): String? =
        Regex("\\bv(\\d+\\.\\d+(?:\\.\\d+)?)").find(fileName)?.groupValues?.get(1)
            ?: Regex("\\[v(\\d{4,})]").find(fileName)?.groupValues?.get(1)?.let { "v$it" }

    /** A readable DLC name: the file name without the game's title, release tags and site names. */
    fun dlcName(fileName: String, gameTitle: String): String {
        var t = cleanName(fileName.substringBeforeLast('.')).replace(Regex("(?<=[A-Za-z])\\.(?=[A-Za-z])"), " ")
        for (part in listOf(gameTitle, cleanName(gameTitle))) {
            if (part.isNotBlank()) t = t.replace(Regex("(?i)" + Regex.escape(part)), " ")
        }
        t = t.replace(Regex("(?i)\\b(dlc|add-?on)\\b"), " ").replace(Regex("^[\\s\\-:–]+|[\\s\\-:–]+$"), "").replace(Regex("\\s+"), " ")
        return t.ifBlank { cleanName(fileName.substringBeforeLast('.')) }
    }

    /** A ticket is named after its rights id, whose first 16 digits are the title id; the game beats an update or DLC. */
    fun titleIdFromNsp(header: ByteArray): String? =
        pfs0Names(header).orEmpty().filter { TICKET.matches(it) }.map { it.take(16).uppercase() }.minByOrNull(::kindRank)

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    fun cleanName(fileBase: String): String = fileBase
        .replace(Regex("\\[[^\\]]*]|\\([^)]*\\)"), " ")
        .replace(Regex("(?i)\\b[a-z0-9-]+\\.(com|net|org)\\b"), " ")
        .replace(Regex("(?i)\\bv\\d+(\\.\\d+)*\\b"), " ")
        .replace(Regex("(?i)\\b(update|upd|dlc|nsp|xci|eshop)\\b"), " ")
        .replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    /** The index entry for a game: by title id first (updates and DLC use their game), then by cleaned-up name. */
    fun find(index: Index, fileName: String, path: String, nspHeader: ByteArray?, platformId: String): Title? {
        val tid = titleIdIn(fileName) ?: titleIdIn(path) ?: nspHeader?.let(::titleIdFromNsp)
        if (tid != null) index.byId[baseTitleId(tid)]?.takeIf { fitsPlatform(it.id, platformId) }?.let { return it }
        val key = normalize(cleanName(fileName.substringBeforeLast('.')))
        return if (key.length >= 3) index.byName[key]?.takeIf { fitsPlatform(it.id, platformId) } else null
    }

    fun coverUrl(context: Context, rom: File, platformId: String): String? {
        val index = index(context) ?: return null
        val header = if (rom.extension.equals("nsp", ignoreCase = true)) readHeader(rom) else null
        return find(index, rom.name, rom.parent.orEmpty(), header, platformId)?.let { imageUrl(it.iconHash) }
    }

    private fun readAt(file: File, offset: Long, length: Int): ByteArray? = runCatching {
        RandomAccessFile(file, "r").use { f -> f.seek(offset); ByteArray(length).also { f.readFully(it) } }
    }.getOrNull()

    private fun readHeader(file: File): ByteArray? = runCatching {
        RandomAccessFile(file, "r").use { f ->
            ByteArray(minOf(HEADER_BYTES.toLong(), f.length()).toInt()).also { f.readFully(it) }
        }
    }.getOrNull()

    private fun le64(b: ByteArray, o: Int): Long = (le32(b, o).toLong() and 0xFFFFFFFFL) or (le32(b, o + 4).toLong() shl 32)

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
