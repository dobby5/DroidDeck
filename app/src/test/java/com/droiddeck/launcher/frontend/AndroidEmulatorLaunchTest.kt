package com.droiddeck.launcher.frontend

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.droiddeck.launcher.runtime.AndroidEmulators
import com.droiddeck.launcher.session.SessionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidEmulatorLaunchTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rom = File("/storage/emulated/0/ROMs/gba/Game.gba")
    private val content = Uri.parse("content://com.droiddeck.launcher.roms/primary/ROMs/gba/Game.gba")

    private fun app(launch: String?): AndroidEmulators.App {
        val pkg = launch?.let { JSONObject(it).getString("component").substringBefore('/') } ?: "p.x"
        return AndroidEmulators.parse("""{"apps":[{"id":"x","package":"$pkg"${launch?.let { ",\"launch\":$it" } ?: ""}}]}""")!!.single()
    }

    private fun intent(launch: String?, system: String = "gba") = AndroidEmulatorLaunch.romIntent(app(launch), rom, system) { content }

    @Test fun aFileTemplateGetsThePathAndTheSystemsCore() {
        val i = intent("""{"component":"com.retroarch/com.retroarch.X","uri":"file",
            "extras":{"ROM":"{rom}","LIBRETRO":"/cores/{core}_libretro_android.so"},"cores":{"gba":"mgba"}}""")!!
        assertEquals("com.retroarch/com.retroarch.X", i.component!!.flattenToString())
        assertEquals(rom.absolutePath, i.getStringExtra("ROM"))
        assertEquals("/cores/mgba_libretro_android.so", i.getStringExtra("LIBRETRO"))
        assertNull(i.data)
        assertEquals(0, i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNotEquals(0, i.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    @Test fun aMissingCoreMeansNoDirectLaunch() {
        assertNull(intent("""{"component":"a/.B","uri":"file","extras":{"ROM":"{rom}","L":"{core}"},"cores":{"gba":"mgba"}}""", system = "snes"))
    }

    @Test fun theLaunchCanRestrictFileExtensions() {
        assertNull(intent("""{"component":"a/.B","data":true,"extensions":["nup"]}"""))
        assertEquals(content, intent("""{"component":"a/.B","data":true,"extensions":["GBA"]}""")!!.data)
    }

    @Test fun aContentTemplateGrantsReadOnTheUri() {
        val i = intent("""{"component":"org.dolphinemu.dolphinemu/.ui.main.MainActivity","action":"android.intent.action.VIEW","extras":{"AutoStartFile":"{rom}"}}""")!!
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals(content.toString(), i.getStringExtra("AutoStartFile"))
        assertEquals(content, i.clipData!!.getItemAt(0).uri)
        assertNotEquals(0, i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, i.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun dataAlwaysUsesTheContentUri() {
        val i = intent("""{"component":"org.ppsspp.ppsspp/.PpssppActivity","action":"android.intent.action.VIEW","uri":"file","data":true}""")!!
        assertEquals(content, i.data)
    }

    @Test fun aDocumentLaunchSharesOnlyAFileInsideTheConfiguredRomFolders() {
        val context = RuntimeEnvironment.getApplication()
        val folder = tmp.newFolder("roms")
        val game = File(folder, "Dreamcast game.cdi").apply { writeText("rom") }
        val outside = tmp.newFile("other.cdi")
        SessionPrefs.setRomsDirs(context, listOf(folder.path))
        val flycast = app("""{"component":"com.flycast.emulator/.MainActivity","uri":"document","data":true}""")

        val intent = AndroidEmulatorLaunch.romIntent(context, flycast, game, "dreamcast")!!
        assertEquals(game.canonicalPath, DocumentsContract.getDocumentId(intent.data!!))
        assertEquals(intent.data, intent.clipData!!.getItemAt(0).uri)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        assertNull(AndroidEmulatorLaunch.romIntent(context, flycast, outside, "dreamcast"))
        assertThrows(FileNotFoundException::class.java) { RomDocumentProvider.uri(context, folder) }
    }

    @Test fun openOnlyAppsHaveNoRomIntent() {
        assertNull(intent(null))
    }
}
