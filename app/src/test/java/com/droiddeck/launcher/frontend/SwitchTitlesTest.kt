package com.droiddeck.launcher.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SwitchTitlesTest {
    private val hash = "a".repeat(64)
    private val index = SwitchTitles.parse(
        """
        # comment
        0100000000010000	Super Mario Odyssey™	$hash
        0100F7E00C70E000	Pokémon™ Shining Pearl	${"b".repeat(64)}
        0400000000010000	Mario Kart World	${"c".repeat(64)}
        """.trimIndent().byteInputStream(),
    )

    private fun nsp(vararg names: String): ByteArray {
        val strings = names.joinToString("") { it + "\u0000" }.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        out.write("PFS0".toByteArray()); le32(names.size); le32(strings.size); le32(0)
        var nameOffset = 0
        for (n in names) { repeat(4) { le32(0) }; le32(nameOffset); le32(0); nameOffset += n.length + 1 }
        out.write(strings)
        return out.toByteArray()
    }

    @Test fun aTitleIdInTheNameOrFolderWinsAndUpdatesUseTheirGame() {
        assertEquals("0100000000010000", SwitchTitles.find(index, "Odyssey [0100000000010800][v1].nsp", "/roms", null, "switch")!!.id)
        assertEquals("0100000000010000", SwitchTitles.find(index, "game.xci", "/roms/0100000000010000", null, "switch")!!.id)
    }

    @Test fun theNspTicketNamesTheGame() {
        val header = nsp("0100f7e00c70e0000000000000000004.tik", "0100f7e00c70f8000000000000000004.tik", "x.nca")
        assertEquals("0100F7E00C70E000", SwitchTitles.titleIdFromNsp(header))
        assertEquals("Pokémon™ Shining Pearl", SwitchTitles.find(index, "pearl.nsp", "/roms", header, "switch")!!.name)
        assertNull(SwitchTitles.titleIdFromNsp("NOPE".toByteArray()))
    }

    @Test fun aCleanedNameMatchesWithoutAnId() {
        assertEquals("0100F7E00C70E000", SwitchTitles.find(index, "Pokemon Shining Pearl (EU) [v0] nsp.xci", "/roms", null, "switch")!!.id)
    }

    @Test fun oneGenerationsPictureNeverGoesOnTheOther() {
        assertNull(SwitchTitles.find(index, "Mario Kart World.xci", "/roms", null, "switch"))
        assertEquals("0400000000010000", SwitchTitles.find(index, "Mario Kart World.nup", "/roms", null, "switch2")!!.id)
    }

    @Test fun theBundledIndexParses() {
        val bundled = File("src/main/assets/switch-titles.tsv").inputStream().use(SwitchTitles::parse)
        assertEquals("Super Mario Odyssey™", bundled.byId.getValue("0100000000010000").name)
        assertEquals("https://img-eshop.cdn.nintendo.net/i/$hash.jpg", SwitchTitles.imageUrl(hash))
    }
}
