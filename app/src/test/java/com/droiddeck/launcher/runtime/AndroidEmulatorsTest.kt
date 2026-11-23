package com.droiddeck.launcher.runtime

import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidEmulatorsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val sha = "a".repeat(64)
    private val signer = "b".repeat(64)

    private fun one(json: String) = AndroidEmulators.parse("""{"apps":[$json]}""")!!.single()

    @Test fun anApkNeedsASignerAndAnHttpsSource() {
        val pinned = one("""{"id":"x","package":"p.x","apk":{"url":"https://h/x.apk","sha256":"$sha","signerSha256":"$signer","versionCode":7}}""")
        assertEquals(7L, pinned.apk!!.versionCode)
        assertEquals(listOf(signer), pinned.apk!!.signers)
        assertNull(one("""{"id":"x","package":"p.x","apk":{"url":"http://h/x.apk","signers":["$signer"]}}""").apk)
        assertNull(one("""{"id":"x","package":"p.x","apk":{"url":"https://h/x.apk","sha256":"$sha"}}""").apk)
        assertNull(one("""{"id":"x","package":"p.x","apk":{"github":"o/r","signers":["$signer"]}}""").apk)
        assertNull(one("""{"id":"x","package":"p.x","apk":{"page":"http://h/","pattern":"x","signers":["$signer"]}}""").apk)
        val github = one("""{"id":"x","package":"p.x","apk":{"github":"o/r","asset":"\\.apk$","prerelease":true,"signers":["$signer","${"c".repeat(64)}"]}}""").apk!!
        assertEquals("o/r", github.github)
        assertTrue(github.prerelease)
        assertEquals(2, github.signers.size)
        assertNull(github.sha256)
    }

    @Test fun aGithubSourcePicksTheNewestMatchingStableAsset() {
        val apk = one("""{"id":"x","package":"p.x","apk":{"github":"o/r","asset":"prod\\.apk$","signers":["$signer"]}}""").apk!!
        val releases = """[
            {"tag_name":"nightly","prerelease":true,"assets":[{"name":"prod.apk","browser_download_url":"https://h/nightly/prod.apk"}]},
            {"tag_name":"2.0","prerelease":false,"assets":[
                {"name":"debug.apk","browser_download_url":"https://h/2.0/debug.apk"},
                {"name":"prod.apk","browser_download_url":"https://h/2.0/prod.apk","digest":"sha256:$sha","size":42}]},
            {"tag_name":"1.0","prerelease":false,"assets":[{"name":"prod.apk","browser_download_url":"https://h/1.0/prod.apk"}]}]"""
        var asked: String? = null
        val resolved = AndroidEmulatorInstaller.resolve(apk) { asked = it; releases }!!
        assertEquals("https://api.github.com/repos/o/r/releases?per_page=15", asked)
        assertEquals("https://h/2.0/prod.apk", resolved.url)
        assertEquals(sha, resolved.sha256)
        assertEquals(42L, resolved.size)
        assertNull(AndroidEmulatorInstaller.resolve(apk) { null })
    }

    @Test fun aPageSourcePicksTheHighestVersionAndResolvesRelativeLinks() {
        val apk = one("""{"id":"x","package":"p.x","apk":{"page":"https://h/download/","pattern":"/files/\\d+_\\d+(?:_\\d+)?/app\\.apk","signers":["$signer"]}}""").apk!!
        val html = """<a href="/files/1_9_9/app.apk"></a><a href="/files/1_20_4/app.apk"></a><a href="https://h/files/1_20/app.apk"></a>"""
        assertEquals("https://h/files/1_20_4/app.apk", AndroidEmulatorInstaller.resolve(apk) { html }!!.url)
        val relative = one("""{"id":"x","package":"p.x","apk":{"page":"https://h/testing/","pattern":"downloads/app-\\d+\\.apk","signers":["$signer"]}}""").apk!!
        assertEquals("https://h/testing/downloads/app-12273.apk",
            AndroidEmulatorInstaller.resolve(relative) { """href="downloads/app-12126.apk" href="downloads/app-12273.apk"""" }!!.url)
        assertTrue(AndroidEmulatorInstaller.compareVersions("1_20_4", "1_9_9") > 0)
    }

    @Test fun storeOnlyAndOpenOnlyEntriesParse() {
        val opener = one("""{"id":"opener","package":"org.example.opener","name":"Opener","store":"market://details?id=org.example.opener"}""")
        assertNull(opener.apk)
        assertNull(opener.launch)
        assertNull(opener.pairs)
        assertEquals("market://details?id=org.example.opener", opener.store)
        assertTrue(opener.onPlay)
        assertNull(one("""{"id":"x","package":"p.x","store":"intent://evil"}""").store)
        assertFalse(one("""{"id":"x","package":"p.x","store":"market://details?id=other"}""").onPlay)
        assertFalse(one("""{"id":"x","package":"p.x","store":"https://github.com/o/r/releases"}""").onPlay)
    }

    @Test fun theDirectSourceNamesWhereTheApkComesFrom() {
        assertEquals("GitHub (o/r)", one("""{"id":"x","package":"p.x","apk":{"github":"o/r","asset":"a","signers":["$signer"]}}""").directSource)
        assertEquals("ppsspp.org", one("""{"id":"x","package":"p.x","apk":{"page":"https://www.ppsspp.org/download/","pattern":"a","signers":["$signer"]}}""").directSource)
        assertEquals("dl.dolphin-emu.org", one("""{"id":"x","package":"p.x","apk":{"url":"https://dl.dolphin-emu.org/x.apk","signers":["$signer"]}}""").directSource)
        assertNull(one("""{"id":"x","package":"p.x"}""").directSource)
    }

    @Test fun entriesWithoutIdOrPackageAreSkipped() {
        val apps = AndroidEmulators.parse("""{"apps":[{"id":"a"},{"package":"p"},{"id":"ok","package":"p.ok"}]}""")!!
        assertEquals(listOf("ok"), apps.map { it.id })
    }

    @Test fun launchKeepsExtrasAndLowercasesCoreKeys() {
        val ra = one("""{"id":"ra","package":"com.retroarch","pairs":"retroarch","platforms":["GBA"],
            "launch":{"component":"com.retroarch/.X","uri":"file","extras":{"ROM":"{rom}"},"cores":{"GBA":"mgba"}}}""")
        val launch = ra.launch!!
        assertTrue(launch.fileUri)
        assertTrue(launch.clearTask)
        assertEquals(mapOf("ROM" to "{rom}"), launch.extras)
        assertEquals(mapOf("gba" to "mgba"), launch.cores)
        assertEquals(setOf("gba"), ra.platforms)
        assertEquals(setOf("gba"), one("""{"id":"old","package":"p.old","folders":["gba"]}""").platforms)
    }

    @Test fun theBundledCatalogParsesAndEveryCoreTemplateHasCores() {
        val apps = AndroidEmulators.parse(File("src/main/assets/android-emulators.json").readText())
        assertNotNull(apps)
        apps!!
        assertEquals(
            setOf("rpcs3", "armsx2", "dolphin", "duckstation", "melonds", "cemu", "ppsspp", "retroarch"),
            apps.mapNotNull { it.pairs }.toSet(),
        )
        apps.forEach { assertNotNull(it.id, it.apk) }
        for (a in apps) {
            val launch = a.launch ?: continue
            if (launch.extras.values.any { "{core}" in it }) assertTrue(a.id, a.platforms.all { it in launch.cores })
            assertTrue(a.id, a.platforms.all { com.droiddeck.launcher.frontend.RomPlatforms.byId(it) != null })
        }
    }

    @Test fun aLaunchMustTargetItsPackageAndPassTheRom() {
        assertNull(one("""{"id":"x","package":"p.x","launch":{"component":"p.other/.Game","data":true}}""").launch)
        assertNull(one("""{"id":"x","package":"p.x","launch":{"component":"p.x/.Game"}}""").launch)
    }

    @Test fun theChecksRejectAWrongPackageOrSigner() {
        val emu = one("""{"id":"x","package":"p.x","name":"X","apk":{"url":"https://h/x.apk","sha256":"$sha","signerSha256":"$signer","versionCode":7}}""")
        fun info(pkg: String, code: Long) = PackageInfo().apply { packageName = pkg; longVersionCode = code }
        assertNull(AndroidEmulatorInstaller.problem(app, emu, info("p.x", 7), listOf(signer)))
        assertNotNull(AndroidEmulatorInstaller.problem(app, emu, info("p.other", 7), listOf(signer)))
        assertNotNull(AndroidEmulatorInstaller.problem(app, emu, info("p.x", 6), listOf(signer)))
        assertNotNull(AndroidEmulatorInstaller.problem(app, emu, info("p.x", 7), listOf("c".repeat(64))))
        assertNull(AndroidEmulatorInstaller.problem(app, emu, info("p.x", 7), listOf(signer.uppercase())))
        assertNotNull(AndroidEmulatorInstaller.problem(app, emu, info("p.x", 7), emptyList()))
        assertNotNull(AndroidEmulatorInstaller.problem(app, emu, null, listOf(signer)))
    }

    @Test fun anUninstalledAppHasNoUpdate() {
        val emu = one("""{"id":"x","package":"p.not.installed","apk":{"url":"https://h/x.apk","sha256":"$sha","signerSha256":"$signer","versionCode":7}}""")
        assertNull(AndroidEmulatorUpdates.available(app, emu))
    }
}
