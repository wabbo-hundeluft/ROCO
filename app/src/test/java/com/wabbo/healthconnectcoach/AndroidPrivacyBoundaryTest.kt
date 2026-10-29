package com.wabbo.healthconnectcoach

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPrivacyBoundaryTest {
    @Test
    fun optionalAiHasInternetAccessButDisallowsCleartext() {
        val manifest = readAppFile("src/main/AndroidManifest.xml")
        val buildFile = readAppFile("build.gradle.kts")
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(buildFile.contains("okhttp"))
        assertFalse(buildFile.contains("retrofit"))
        assertFalse(buildFile.contains("ktor-client"))
    }

    @Test
    fun backupAndDeviceTransferAreDisabledAndAllAppDomainsAreExcluded() {
        val manifest = readAppFile("src/main/AndroidManifest.xml")
        val legacyRules = readAppFile("src/main/res/xml/backup_rules.xml")
        val extractionRules = readAppFile("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        val domains = listOf("root", "file", "database", "sharedpref", "external")
        domains.forEach { domain ->
            assertTrue("Missing legacy exclusion for $domain", legacyRules.contains("domain=\"$domain\""))
            assertTrue("Missing cloud/device exclusion for $domain", extractionRules.contains("domain=\"$domain\""))
        }
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
    }

    @Test
    fun manifestDeclaresOnlyTheStandard38AndSeparateHistoryRight() {
        val manifest = readAppFile("src/main/AndroidManifest.xml")
        val declared = Regex("""<uses-permission android:name="([^"]+)"\s*/>""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .filter { it.startsWith("android.permission.health.") }
            .toSet()
        assertEquals(
            HealthConnectSetup.standardPermissions + HealthConnectSetup.medicalPermissions + HealthConnectSetup.historyPermission,
            declared,
        )
        assertEquals(38, HealthConnectSetup.standardPermissions.size)
        assertEquals(12, HealthConnectSetup.medicalPermissions.size)
        assertEquals(51, declared.size)
        assertFalse(manifest.contains("READ_HEALTH_DATA_IN_BACKGROUND"))
        assertFalse(manifest.contains("READ_EXERCISE_ROUTE"))
        assertFalse(manifest.contains("READ_EXERCISE_ROUTES"))
        assertFalse(declared.any { it.contains("WRITE_") })
        assertTrue(manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun aiOnlyUsesFixedHttpsProvidersAndDoesNotPutKeysInUrls() {
        val network = readAppFile("src/main/java/com/wabbo/healthconnectcoach/AiCoach.kt")
        assertTrue(network.contains("https://generativelanguage.googleapis.com/"))
        assertTrue(network.contains("provider.endpoint"))
        val urls=CoachProvider.entries.map {java.net.URI(it.endpoint)}
        assertEquals(setOf("api.groq.com","api.mistral.ai","openrouter.ai","generativelanguage.googleapis.com"),urls.map {it.host}.toSet())
        urls.forEach {assertEquals("https",it.scheme);assertNull(it.query);assertNull(it.userInfo);assertNull(it.fragment)}
        val catalog=readAppFile("src/main/java/com/wabbo/healthconnectcoach/AiModelCatalog.kt")
        assertFalse(catalog.contains("?key="));assertTrue(catalog.contains("setRequestProperty"))
        assertTrue(network.contains("instanceFollowRedirects = false"))
        assertFalse(network.contains("?key="))
        assertTrue(network.contains("config.shareHealthSummary"))
        assertTrue(network.contains("require(key.isNotBlank())"))
        val main = readAppFile("src/main/java/com/wabbo/healthconnectcoach/MainActivity.kt")
        assertTrue(main.contains("ProductExperience("))
        assertFalse(main.contains("TodayRedesignScreen("))
    }

    private fun readAppFile(relativePath: String): String {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
        check(file != null) { "Could not locate $relativePath from ${workingDirectory.absolutePath}." }
        return file.readText()
    }
}
