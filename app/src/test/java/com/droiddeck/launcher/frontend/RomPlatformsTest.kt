package com.droiddeck.launcher.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RomPlatformsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun id(file: String, vararg folders: String) = RomPlatforms.detect(file, folders.toList())?.id

    @Test fun aUniqueExtensionNeedsNoFolder() {
        assertEquals("gba", id("Zelda.gba"))
        assertEquals("wii", id("Mario Kart.wbfs"))
        assertEquals("nds", id("Pokemon.NDS"))
    }

    @Test fun aSharedExtensionIsNeverGuessed() {
        assertNull(id("Game.iso"))
        assertNull(id("Game.chd", "Misc"))
        assertNull(id("Game.rvz"))
    }

    @Test fun theNearestFolderHintDecides() {
        assertEquals("ps2", id("Game.iso", "ps2"))
        assertEquals("psp", id("Game.iso", "ps2", "psp"))
        assertEquals("gc", id("Metroid.rvz", "GameCube"))
        assertEquals("wii", id("Metroid.rvz", "Wii"))
        assertEquals("gba", id("Zelda.gba", "GameBoyAdvanceTOSEC"))
        assertEquals("gba", id("Zelda.gba", "psp-collection"))
        assertEquals("psp", id("Game.cso", "psp-cso-collection"))
    }

    @Test fun aHintThatDoesNotTakeTheExtensionFallsThrough() {
        assertNull(id("Old.iso", "gba"))
        assertEquals("snes", id("Zelda.sfc", "ps2"))
    }

    @Test fun theScanListsEachGameOnceWithItsSystem() {
        val root = tmp.newFolder("ROMs")
        fun file(rel: String, text: String = "x") = File(root, rel).apply { parentFile!!.mkdirs(); writeText(text) }
        file("ps2/God of War.iso")
        file("psp/Patapon.iso")
        file("Loose.iso")
        file("gba/Zelda (Europe).gba")
        file("psx/Crash.cue", "FILE \"Crash.bin\" BINARY\n")
        file("psx/Crash.bin")
        file("ps2/bios/scph.bin")
        File(root, "ps3/Demon's Souls/PS3_GAME").mkdirs()
        val games = Library.scan(root).associate { it.name to it.system }
        assertEquals(
            mapOf("God of War" to "ps2", "Patapon" to "psp", "Zelda" to "gba", "Crash" to "psx", "Demon's Souls" to "ps3"),
            games,
        )
        assertTrue(Library.scan(root).all { it.guestPath.startsWith("/root/ROMs/") })
    }
}
