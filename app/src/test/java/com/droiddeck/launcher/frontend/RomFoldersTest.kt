package com.droiddeck.launcher.frontend

import com.droiddeck.launcher.session.SessionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RomFoldersTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @get:Rule val tmp = TemporaryFolder()

    private fun file(root: File, rel: String) = File(root, rel).apply { parentFile!!.mkdirs(); writeText("x") }

    @Test fun theFirstFolderStaysRootRomsAndOthersGetTheirOwnMount() {
        val first = tmp.newFolder("ROMs")
        val inside = File(first, "psp").apply { mkdirs() }
        val sd = tmp.newFolder("sdcard-Roms")
        assertEquals("/root/ROMs", RomFolders.guestRoot(first, first))
        assertEquals("/root/ROMs/psp", RomFolders.guestRoot(first, inside))
        assertTrue(RomFolders.guestRoot(first, sd).startsWith("/mnt/roms/sdcard-Roms-"))
        assertEquals(RomFolders.guestRoot(first, sd), RomFolders.guestRoot(first, File(sd.path + "/")))

        val binds = RomFolders.binds(listOf(first, inside, sd, sd))
        assertEquals(listOf(first.path, sd.path), binds.map { it.first.path })
        assertEquals("/root/ROMs", binds.first().second)
        assertTrue(RomFolders.binds(emptyList()).isEmpty())
    }

    @Test fun everyEmulatorReadsAllFoldersForBothWays() {
        val internal = tmp.newFolder("internal")
        val sd = tmp.newFolder("sd")
        file(internal, "psp/Patapon.iso")
        file(sd, "psp/games/Daxter.cso")
        file(sd, "Zelda.gba")
        SessionPrefs.setRomsDirs(app, listOf(internal.path, sd.path))
        assertEquals(internal.path, SessionPrefs.romsDir(app))

        val emus = Library.emulators(app, { true }).associateBy { it.id }
        val games = emus.getValue("ppsspp").games.associateBy { it.name }
        assertEquals(setOf("Patapon", "Daxter"), games.keys)
        assertEquals("/root/ROMs/psp/Patapon.iso", games.getValue("Patapon").guestPath)
        assertEquals(RomFolders.guestRoot(internal, sd) + "/psp/games/Daxter.cso", games.getValue("Daxter").guestPath)
        assertEquals(File(sd, "psp/games/Daxter.cso").path, games.getValue("Daxter").hostPath.path)
        assertTrue(emus.getValue("armsx2").games.isEmpty())

        assertEquals(listOf(internal.path, sd.path), RomFolders.binds(app).map { it.first.path })
    }

    @Test fun theListKeepsOrderDropsBlanksAndDuplicatesAndCanBeEmptied() {
        SessionPrefs.setRomsDirs(app, listOf(" /x ", "/y", "", "/x"))
        assertEquals(listOf("/x", "/y"), SessionPrefs.romsDirs(app))
        assertEquals("/x", SessionPrefs.romsDir(app))
        SessionPrefs.setRomsDirs(app, emptyList())
        assertTrue(SessionPrefs.romsDirs(app).isEmpty())
        assertEquals("", SessionPrefs.romsDir(app))
    }
}
