package com.droiddeck.launcher.runtime

import com.droiddeck.launcher.frontend.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DesktopEmulatorsTest {
    private val text = File("src/main/assets/desktop-emulators.json").readText()

    @Test fun everyContainerEmulatorIsBundledWithAPinnedFile() {
        val entries = DesktopCatalog.parse(text, "4096")
        val ids = entries.map { it.id }
        assertEquals(ids.distinct(), ids)
        for (id in listOf("rpcs3", "armsx2", "dolphin", "duckstation", "melonds", "cemu", "ppsspp", "retroarch")) {
            val pkg = Library.packageId(id)
            assertTrue("$id -> $pkg", pkg in ids)
        }
        entries.forEach { e ->
            assertTrue(e.id, e.url.startsWith("https://"))
            assertTrue(e.id, e.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(e.id, e.size > 0)
        }
    }

    @Test fun sixteenKPagesGetTheirOwnBuild() {
        val small = DesktopCatalog.parse(text, "4096").first { it.id == "armsx2" }
        val large = DesktopCatalog.parse(text, "16384").first { it.id == "armsx2" }
        assertTrue(small.url != large.url)
        assertTrue(large.url.endsWith("armsx2-16k.AppImage"))
    }

    @Test fun bundledEntriesWinOverTheRemoteCatalog() {
        val bundled = DesktopCatalog.parse(text, "4096")
        val remote = DesktopCatalog.parse(
            """{"packages":[{"id":"desktop","name":"Desktop","version":"r1","kind":"tar","url":"https://h/d","sha256":"${"a".repeat(64)}"},
               {"id":"cemu","name":"Old Cemu","version":"0","kind":"appimage","url":"https://h/c","sha256":"${"b".repeat(64)}"}]}""",
            "4096",
        )
        val merged = DesktopCatalog.withBundled(bundled, remote)
        assertEquals("Cemu (Wii U)", merged.first { it.id == "cemu" }.name)
        assertTrue(merged.any { it.id == "desktop" })
    }
}
