package com.droiddeck.launcher.frontend

import com.droiddeck.launcher.R
import com.droiddeck.launcher.runtime.AndroidEmulators
import com.droiddeck.launcher.session.SessionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class LibraryAndroidEmulatorsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @get:Rule val tmp = TemporaryFolder()

    private val catalog = AndroidEmulators.parse("""{"apps":[
        {"id":"dolphin-android","package":"org.dolphinemu.dolphinemu","name":"Dolphin","pairs":"dolphin",
         "launch":{"component":"org.dolphinemu.dolphinemu/.ui.main.MainActivity","extras":{"AutoStartFile":"{rom}"}}},
        {"id":"retroarch-android","package":"com.retroarch","name":"RetroArch","pairs":"retroarch","platforms":["gba"],
         "launch":{"component":"com.retroarch/.X","uri":"file","extras":{"ROM":"{rom}"}}},
        {"id":"opener","package":"org.example.opener","name":"Opener"},
        {"id":"alternate","package":"org.example.alternate","name":"Alternate","platforms":["gc"],
         "launch":{"component":"org.example.alternate/.Game","data":true,"extensions":["rvz"]}}
    ]}""")!!

    private fun roms(): File = tmp.newFolder("ROMs").also { root ->
        File(root, "gc").mkdirs(); File(root, "gc/Metroid.rvz").writeText("x")
        File(root, "gba").mkdirs(); File(root, "gba/Zelda.gba").writeText("x"); File(root, "gba/Old.iso").writeText("x")
        SessionPrefs.setRomsDirs(app, listOf(root.absolutePath))
    }

    private fun scan(installed: Set<String>, container: Set<String> = emptySet()) =
        Library.emulators(app, { it in container }, catalog, androidInstalled = { it in installed }, androidIcon = { "icon:$it" }).associateBy { it.id }

    @Test fun aPairedAppJoinsItsEmulatorAndAddsTheAndroidWay() {
        roms()
        val dolphin = scan(setOf("org.dolphinemu.dolphinemu")).getValue("dolphin")
        assertEquals("dolphin-android", dolphin.android?.id)
        assertTrue(dolphin.ready)
        assertFalse(dolphin.installed)
        assertEquals(listOf(Library.Runner.ANDROID), dolphin.runners())
        assertEquals(listOf("Metroid"), dolphin.games.map { it.name })
        assertEquals("gc", dolphin.games.single().system)

        val both = scan(setOf("org.dolphinemu.dolphinemu"), container = setOf("dolphin")).getValue("dolphin")
        assertEquals(listOf(Library.Runner.CONTAINER, Library.Runner.ANDROID), both.runners())
        assertEquals(Library.Runner.ANDROID, both.runner("ANDROID"))
        assertEquals(Library.Runner.CONTAINER, both.runner(null))
    }

    @Test fun retroArchListsItsGamesButStartsThemOnlyWithTheAndroidApp() {
        roms()
        val without = scan(emptySet(), container = setOf("emulators")).getValue("retroarch")
        assertEquals(listOf("Zelda"), without.games.map { it.name })
        assertTrue(without.runners().isEmpty())
        val ra = scan(setOf("com.retroarch"), container = setOf("emulators")).getValue("retroarch")
        assertEquals(listOf("Zelda"), ra.games.map { it.name })
        assertEquals(listOf(Library.Runner.ANDROID), ra.runners())
    }

    @Test fun anAppsSystemFolderIsNoLongerScannedByOtherEmulators() {
        roms()
        val dolphin = scan(emptySet(), container = setOf("dolphin")).getValue("dolphin")
        assertFalse(dolphin.games.any { it.name == "Old" })
    }

    @Test fun aStandaloneAppWithoutADirectLaunchOnlyOpens() {
        roms()
        val entries = scan(setOf("org.example.opener", "org.example.alternate"))
        val opener = entries.getValue("opener")
        assertTrue(opener.ready)
        assertTrue(opener.games.isEmpty())
        assertTrue(opener.runners().isEmpty())
        val alternate = entries.getValue("alternate")
        assertFalse(alternate.hasContainer)
    }

    @Test fun aStandaloneAppWithADirectLaunchListsItsGames() {
        roms()
        val entries = scan(setOf("org.example.opener", "org.example.alternate"))
        val alternate = entries.getValue("alternate")
        assertFalse(alternate.hasContainer)
        assertTrue(alternate.ready)
        assertEquals(listOf("Metroid"), alternate.games.map { it.name })
        assertEquals(listOf(Library.Runner.ANDROID), alternate.runners())
        assertEquals("icon:org.example.alternate", alternate.art)
        assertNull(scan(emptySet()).getValue("alternate").art)
        assertEquals(R.drawable.emu_dolphin, scan(setOf("org.dolphinemu.dolphinemu")).getValue("dolphin").art)
    }

    @Test fun theRomPagePrefersTheSameRunnableEmulatorAsTheLibrary() {
        val path = File(roms(), "gc/Metroid.rvz").absolutePath
        val emulators = scan(setOf("org.example.alternate")).values.toList()
        val tile = com.droiddeck.launcher.ui.romEntries(emulators).first { it.rom?.hostPath?.absolutePath == path }
        val candidates = com.droiddeck.launcher.ui.romCandidates(emulators, path)
        assertEquals("alternate", tile.emulator?.id)
        assertEquals(listOf("alternate", "dolphin"), candidates.map { it.first.id })
    }

    @Test fun theLibraryListsEveryRomOnceAndGroupsByPlatform() {
        roms()
        val emus = Library.emulators(app, { it == "dolphin" }, catalog, androidInstalled = { it == "com.retroarch" })
        val entries = com.droiddeck.launcher.ui.romEntries(emus)
        assertEquals(setOf("Metroid", "Zelda"), entries.map { it.title }.toSet())
        assertTrue(entries.all { it.source == Library.SOURCE_EMULATORS && it.installed && it.key.startsWith("app:rom:") })
        assertEquals("retroarch", entries.single { it.title == "Zelda" }.emulator?.id)
        val pc = com.droiddeck.launcher.ui.LibraryEntry("app:440", Library.SOURCE_STEAM, "TF2", null, null, true)
        val groups = com.droiddeck.launcher.ui.platformGroups(entries + pc, "PC")
        assertEquals(listOf("PC", "Game Boy Advance", "GameCube"), groups.map { it.first })

        val none = com.droiddeck.launcher.ui.romEntries(Library.emulators(app, { false }, catalog))
        val metroid = none.single { it.title == "Metroid" }
        assertTrue(metroid.installed)
        assertTrue(metroid.emulator!!.runners().isEmpty())
    }

    @Test fun theChosenWayIsRemembered() {
        assertNull(SessionPrefs.emuRunner(app, "dolphin"))
        SessionPrefs.setEmuRunner(app, "dolphin", Library.Runner.ANDROID.name)
        assertEquals("ANDROID", SessionPrefs.emuRunner(app, "dolphin"))
        SessionPrefs.setEmuRunner(app, "dolphin", null)
        assertNull(SessionPrefs.emuRunner(app, "dolphin"))
    }
}
