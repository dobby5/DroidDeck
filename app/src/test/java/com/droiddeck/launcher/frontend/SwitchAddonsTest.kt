package com.droiddeck.launcher.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SwitchAddonsTest {
    private fun rom(name: String, system: String = "switch") = Library.Rom(name.substringBeforeLast('.'), File("/roms/$name"), "/root/ROMs/$name", "", system = system)

    private fun ids(name: String): SwitchTitles.Id? {
        val tid = SwitchTitles.titleIdIn(name) ?: return null
        val kind = SwitchTitles.kindOf(tid)!!
        return SwitchTitles.Id(tid, kind, if (kind == SwitchTitles.Kind.GAME) tid else SwitchTitles.baseTitleId(tid))
    }

    @Test fun updatesAndDlcMoveOntoTheirGameNewestUpdateFirst() {
        val game = rom("Zelda [0100000000010000].nsp")
        val roms = listOf(
            game,
            rom("Zelda [0100000000010800][v131072].nsp"),
            rom("Zelda [0100000000010800][v262144].nsp"),
            rom("Zelda Expansion [0100000000011001].nsp"),
            rom("Mario.gba", "gba"),
        )
        val grouped = SwitchAddons.group(roms) { ids(it.name) }
        assertEquals(listOf("Zelda [0100000000010000]", "Mario"), grouped.map { it.name })
        val addons = grouped.first().addons
        assertEquals(listOf(true, true, false), addons.map { it.update })
        assertEquals("Zelda [0100000000010800][v262144].nsp", addons.first().file.name)
    }

    @Test fun anAddonWithoutItsGameStaysListed() {
        val grouped = SwitchAddons.group(listOf(rom("Lonely [0100000000020800][v65536].nsp"))) { ids(it.name) }
        assertEquals(1, grouped.size)
        assertEquals(0, grouped.first().addons.size)
    }

    @Test fun contentMetaDecidesTheKind() {
        val xml = "<ContentMeta><Type>AddOnContent</Type><Id>0x0100000000011002</Id></ContentMeta>"
        assertEquals("0100000000011002" to SwitchTitles.Kind.DLC, SwitchTitles.parseCnmtXml(xml))
        val id = SwitchTitles.identify("pack.nsp", "/roms", null, xml) { null }!!
        assertEquals("0100000000010000", id.baseId)
        assertNull(SwitchTitles.identify("unknown.nsp", "/roms", null, null) { null })
    }

    @Test fun addonLabelsReadWell() {
        assertEquals("2.0.7", SwitchTitles.updateVersionText("Game v2.0.7 Update.nsp"))
        assertEquals("v262144", SwitchTitles.updateVersionText("Game [0100000000010800][v262144].nsp"))
        assertEquals("Happy Home Paradise", SwitchTitles.dlcName("Animal Crossing - Happy Home Paradise [0100620012D6F001][v0].nsp", "Animal Crossing"))
    }
}
