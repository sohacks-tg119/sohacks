package com.hackerman.sohacksrev2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScooterCommandCatalogTest {
    @Test
    fun catalog_containsKnownProfilesFromGuide() {
        val ids = ScooterCommandCatalog.models.map { it.id }.toSet()

        assertTrue(ids.containsAll(listOf("so1", "so2_air", "so3", "so4", "so4_5_1", "so4_5_2", "somytier", "so4ul", "so6")))
        assertNotNull(ScooterCommandCatalog.findModel("s04_pro_gen2"))
        assertNotNull(ScooterCommandCatalog.findModel("s04_pro_gen3"))
        assertEquals(14, ScooterCommandCatalog.models.size)
    }

    @Test
    fun findModel_fallsBackToDefaultForUnknownId() {
        assertEquals(ScooterCommandCatalog.defaultModel, ScooterCommandCatalog.findModel("missing"))
        assertEquals(ScooterCommandCatalog.defaultModel, ScooterCommandCatalog.findModel(null))
    }

    @Test
    fun speedCommand_returnsKnownStaticS04Commands() {
        val model = ScooterCommandCatalog.findModel("so4")

        assertEquals(ProtocolFamily.D7_SO4_V1, model.protocolFamily)
        assertEquals("D707A900005000", model.speedCommand(8))
        assertEquals("D707A90000C878", model.speedCommand(20))
        assertEquals("D707A900012CDD", model.speedCommand(30))
        assertEquals("D707A900028A3C", model.speedCommand(65))
        assertNull(model.speedCommand(66))
    }

    @Test
    fun s04ProGen2_usesUnencryptedSo4ProParser() {
        val model = ScooterCommandCatalog.findModel("s04_pro_gen2")

        assertEquals("D706A30000A9", model.commands.eco!!.resolve())
        assertEquals("D706A30001AA", model.commands.normal!!.resolve())
        assertEquals("D706A30002AB", model.commands.sport!!.resolve())
        assertEquals("D706A00001A7", model.commands.lock!!.resolve())
        assertEquals("D706A00000A6", model.commands.unlock!!.resolve())
        assertNull(model.commands.startupCommand)
        assertNull(model.bleProfile.txAesKey)
        assertNull(model.bleProfile.rxAesKey)
        assertEquals(ProtocolFamily.SO4_PRO, model.protocolFamily)
    }

    @Test
    fun s04ProGen3_usesV52ButtonMapping() {
        listOf("s04_pro_gen3").forEach { modelId ->
            val model = ScooterCommandCatalog.findModel(modelId)

            assertEquals("D706A30000A9", model.commands.eco!!.resolve())
            assertEquals("D706A30001AA", model.commands.normal!!.resolve())
            assertEquals("D706A30002AB", model.commands.sport!!.resolve())
            assertEquals("D706A30003AC", model.commands.dev!!.resolve())
            assertEquals("D706A00001A7", model.commands.lock!!.resolve())
            assertEquals("D706A00000A6", model.commands.unlock!!.resolve())
            assertEquals("D707A90000C878", model.speedCommand(20))
            assertEquals(ProtocolFamily.D7_SO4_V51_PLUS, model.protocolFamily)
        }
    }

    @Test
    fun legacyTestProfile_keepsOldMixedMapping() {
        val model = ScooterCommandCatalog.findModel("s04_pro_legacy_test")

        assertEquals("D707A45A000005", model.commands.eco!!.resolve())
        assertEquals("D707A0000101A9", model.commands.lock!!.resolve())
        assertEquals("D707A0000301AB", model.commands.unlock!!.resolve())
        assertEquals("D707A90000C878", model.speedCommand(20))
    }

    @Test
    fun speedCommand_usesByteChecksumsForS04v52Family() {
        val model = ScooterCommandCatalog.findModel("so4_5_2")

        assertEquals(ProtocolFamily.D7_SO4_V51_PLUS, model.protocolFamily)
        assertEquals("D707A900005000", model.speedCommand(8))
        assertEquals("D707A90000C878", model.speedCommand(20))
        assertEquals("D707A900012CDD", model.speedCommand(30))
        assertEquals("D707A900028A3C", model.speedCommand(65))
    }

    @Test
    fun v52Profiles_encryptWritesButDoNotDecryptNotify() {
        listOf("s04_pro_gen3", "so4_5_2", "somytier").forEach { modelId ->
            val profile = ScooterCommandCatalog.findModel(modelId).bleProfile

            assertEquals("30572F52364B3F473050415811632D2B", profile.txAesKey)
            assertNull(profile.rxAesKey)
        }
    }

    @Test
    fun twoByteProfiles_useSharedAesKeyForTxAndRx() {
        listOf("so4ul", "so6").forEach { modelId ->
            val profile = ScooterCommandCatalog.findModel(modelId).bleProfile

            assertEquals("20572F52364B3F473050415811632D2B", profile.txAesKey)
            assertEquals("20572F52364B3F473050415811632D2B", profile.rxAesKey)
        }
    }

    @Test
    fun dynamicD7Commands_resolveWithLiveSecret() {
        val model = ScooterCommandCatalog.findModel("so1")
        val runtime = CommandRuntimeState(dynamicSecret = 0x7F)
        val unlock = model.commands.unlock!!
        val normal = model.commands.normal!!

        assertNull(unlock.resolve())
        assertEquals("D707A27F000028", unlock.resolve(runtime))
        assertEquals("D707A47F00012B", normal.resolve(runtime))
        assertEquals("D707A97F00C8F7", model.speedCommand(20, runtime))
    }

    @Test
    fun dynamicD7StartupCommand_resolvesWithoutLiveSecret() {
        val model = ScooterCommandCatalog.findModel("so1")

        assertEquals("D707A0000001A8", model.commands.startupCommand!!.resolve())
        assertNull(model.commands.normal!!.resolve())
    }

    @Test
    fun so1A0TestProfile_isAvailableAsAlternativeDynamicProfile() {
        val model = ScooterCommandCatalog.findModel("so1_a0_test")
        val runtime = CommandRuntimeState(dynamicSecret = 0x7F)

        assertEquals("D707A07F03012A", model.commands.lock!!.resolve(runtime))
        assertEquals("D707A07F030029", model.commands.unlock!!.resolve(runtime))
    }

    @Test
    fun so2Air_usesA0CommandsAndA6Bootstrap() {
        val model = ScooterCommandCatalog.findModel("so2_air_a0_test")
        val runtime = CommandRuntimeState(dynamicSecret = 0x7F)

        assertEquals("D707A6000001AE", model.commands.startupCommand!!.resolve())
        assertEquals("D707A07F010027", model.commands.eco!!.resolve(runtime))
        assertEquals("D707A07F030029", model.commands.normal!!.resolve(runtime))
        assertEquals("D707A07F05002B", model.commands.sport!!.resolve(runtime))
        assertEquals("D707A07F03012A", model.commands.lock!!.resolve(runtime))
        assertEquals("D707A07F030029", model.commands.unlock!!.resolve(runtime))
    }

    @Test
    fun so2AirAndSo3_defaultToA4A2DynamicFamily() {
        listOf("so2_air", "so3").forEach { modelId ->
            val model = ScooterCommandCatalog.findModel(modelId)
            val runtime = CommandRuntimeState(dynamicSecret = 0x7F)

            assertEquals("D707A0000001A8", model.commands.startupCommand!!.resolve())
            assertEquals("D707A47F00002A", model.commands.eco!!.resolve(runtime))
            assertEquals("D707A47F00012B", model.commands.normal!!.resolve(runtime))
            assertEquals("D707A27F000129", model.commands.lock!!.resolve(runtime))
            assertEquals("D707A27F000028", model.commands.unlock!!.resolve(runtime))
        }
    }

    @Test
    fun tokenCommands_replaceSessionToken() {
        val model = ScooterCommandCatalog.findModel("so6")
        val runtime = CommandRuntimeState(sessionToken = "A1B2C3D4")
        val lock = model.commands.lock!!

        assertEquals("060101", model.commands.sessionTokenCommand!!.resolve())
        assertNull(lock.resolve())
        assertEquals("050C0101A1B2C3D4", lock.resolve(runtime))
        assertEquals("0546020101A1B2C3D4", model.commands.realtimeStartCommand!!.resolve(runtime))
    }

    @Test
    fun advancedModeCommand_isAvailableForSo4AndSo3Profiles() {
        val supportedModelIds = listOf(
            "s04_pro_gen2",
            "s04_pro_gen3",
            "s04_pro_legacy_test",
            "so4",
            "so4_5_1",
            "so4_5_2",
            "so3"
        )

        supportedModelIds.forEach { modelId ->
            val model = ScooterCommandCatalog.findModel(modelId)

            assertEquals(254, model.maxAdvancedMode)
            assertEquals("D706A30001AA", model.advancedModeCommand(1))
            assertEquals("D706A300FEA7", model.advancedModeCommand(254))
            assertNull(model.advancedModeCommand(0))
            assertNull(model.advancedModeCommand(255))
        }
    }

    @Test
    fun advancedModeGenerator_usesD7ByteChecksumWithoutCrLfBytes() {
        assertEquals("D706A30001AA", S04ModeCommandGenerator.build(1))
        assertEquals("D706A30056FF", S04ModeCommandGenerator.build(86))
        assertEquals("D706A300FEA7", S04ModeCommandGenerator.build(254))
    }
}
