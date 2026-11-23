package com.droiddeck.launcher.runtime

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidEmulatorUpdatesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("android_emulator_updates", Context.MODE_PRIVATE)
    private val signer = "b".repeat(64)
    private val day = 24L * 3600 * 1000

    @Before fun clear() { prefs.edit().clear().commit() }

    private fun one(json: String) = AndroidEmulators.parse("""{"apps":[$json]}""")!!.single()

    private val github = one("""{"id":"gh","package":"p.gh","apk":{"github":"o/r","asset":"prod\\.apk$","signers":["$signer"]}}""")

    private fun releases(tag: String, digest: String) =
        """[{"tag_name":"$tag","assets":[{"name":"prod.apk","browser_download_url":"https://h/$tag/prod.apk","digest":"sha256:$digest"}]}]"""

    @Test fun aNewReleaseAfterADroidDeckInstallIsAnUpdateUntilSkipped() {
        AndroidEmulatorUpdates.recordInstalled(prefs, github, "a".repeat(64), 10)
        assertNull(AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.0")))

        AndroidEmulatorUpdates.check(prefs, listOf(github), { releases("v2.1", "c".repeat(64)) }, 1L) { AndroidEmulatorUpdates.Installed(10, "2.0") }
        assertEquals("2.1", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.0")))
        assertNull("updated elsewhere since", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(11, "2.1")))

        AndroidEmulatorUpdates.skip(prefs, github)
        assertNull(AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.0")))
        AndroidEmulatorUpdates.check(prefs, listOf(github), { releases("v2.2", "d".repeat(64)) }, 2L) { AndroidEmulatorUpdates.Installed(10, "2.0") }
        assertEquals("2.2", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.0")))

        AndroidEmulatorUpdates.recordInstalled(prefs, github, "d".repeat(64), 12)
        assertNull(AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(12, "2.2")))
    }

    @Test fun withoutARecordTheInstalledVersionNameMustMatchBeforeAnythingCounts() {
        AndroidEmulatorUpdates.check(prefs, listOf(github), { releases("v2.1", "c".repeat(64)) }, 1L) { AndroidEmulatorUpdates.Installed(10, "2.0") }
        assertEquals("an older build from elsewhere", "2.1", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.0")))

        AndroidEmulatorUpdates.check(prefs, listOf(github), { releases("v2.1", "c".repeat(64)) }, 2L) { AndroidEmulatorUpdates.Installed(10, "2.1-release") }
        assertNull("baseline taken from the matching release", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.1-release")))
        AndroidEmulatorUpdates.check(prefs, listOf(github), { releases("v2.2", "d".repeat(64)) }, 3L) { AndroidEmulatorUpdates.Installed(10, "2.1-release") }
        assertEquals("2.2", AndroidEmulatorUpdates.available(prefs, github, AndroidEmulatorUpdates.Installed(10, "2.1-release")))
    }

    @Test fun foreignBuildsAreNotChecked() {
        var asked = false
        AndroidEmulatorUpdates.check(prefs, listOf(github), { asked = true; null }, 1L) { null }
        assertFalse(asked)
        assertEquals(1L, prefs.getLong("checkedAt", 0L))
    }

    @Test fun aPinnedApkComparesVersionCodes() {
        val pinned = one("""{"id":"pin","package":"p.pin","apk":{"url":"https://h/releases/2609/app-2609.apk","signers":["$signer"],"versionCode":43092}}""")
        assertEquals("2609", AndroidEmulatorUpdates.available(prefs, pinned, AndroidEmulatorUpdates.Installed(43000, "5.0")))
        assertNull(AndroidEmulatorUpdates.available(prefs, pinned, AndroidEmulatorUpdates.Installed(43092, "5.0")))
        AndroidEmulatorUpdates.skip(prefs, pinned)
        assertNull(AndroidEmulatorUpdates.available(prefs, pinned, AndroidEmulatorUpdates.Installed(43000, "5.0")))
    }

    @Test fun theIntervalDecidesWhenTheNextCheckIsDue() {
        val start = 1_000L * day
        assertTrue(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.EVERY_START, start - 1, start + 5, start))
        assertFalse(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.EVERY_START, start + 1, start + 5, start))
        assertFalse(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.DAILY, start, start + day - 1, 0))
        assertTrue(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.DAILY, start, start + day, 0))
        assertFalse(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.WEEKLY, start, start + 6 * day, 0))
        assertTrue(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.WEEKLY, start, start + 7 * day, 0))
        assertFalse(AndroidEmulatorUpdates.due(AndroidEmulatorUpdates.NEVER, 0, start, 0))
    }

    @Test fun aNewerReleaseIsFoundForABuildInstalledElsewhere() {
        assertTrue(AndroidEmulatorUpdates.newer("0.3.51", "0.3.45"))
        assertFalse(AndroidEmulatorUpdates.newer("0.3.45", "0.3.45"))
        assertFalse(AndroidEmulatorUpdates.newer("0.3.44", "0.3.45"))
        assertTrue(AndroidEmulatorUpdates.newer("12910", "0.1-12870-g9fe085e7d"))
        assertFalse(AndroidEmulatorUpdates.newer("12870", "0.1-12870-g9fe085e7d"))
        assertFalse(AndroidEmulatorUpdates.newer("nightly", "2.0"))
        assertFalse(AndroidEmulatorUpdates.newer("2.1", "build 7"))
    }

    @Test fun releaseLabelsReadLikeVersions() {
        assertEquals("1.19.3", AndroidEmulatorInstaller.versionLabel("/files/1_19_3/ppsspp.apk"))
        assertEquals("9150", AndroidEmulatorInstaller.versionLabel("downloads/duckstation-9150.apk"))
        assertEquals("1.22.2", AndroidEmulatorInstaller.versionLabel("https://buildbot.libretro.com/stable/1.22.2/android/RetroArch.apk"))
        assertTrue(AndroidEmulatorUpdates.sameVersion("9150", "0.1-9150-gabc"))
        assertFalse(AndroidEmulatorUpdates.sameVersion("9151", "0.1-9150-gabc"))
    }
}
