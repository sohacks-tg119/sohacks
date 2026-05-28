package com.hackerman.sohacksrev2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScooterCommandCatalogTest {
    @Test
    fun catalog_containsGen2AndGen3Profiles() {
        assertNotNull(ScooterCommandCatalog.findModel("s04_pro_gen2"))
        assertNotNull(ScooterCommandCatalog.findModel("s04_pro_gen3"))
        assertEquals(2, ScooterCommandCatalog.models.size)
    }

    @Test
    fun findModel_fallsBackToDefaultForUnknownId() {
        assertEquals(ScooterCommandCatalog.defaultModel, ScooterCommandCatalog.findModel("missing"))
        assertEquals(ScooterCommandCatalog.defaultModel, ScooterCommandCatalog.findModel(null))
    }

    @Test
    fun speedCommand_returnsKnownS04Commands() {
        val model = ScooterCommandCatalog.findModel("s04_pro_gen2")

        assertEquals("D707A900005000", model.speedCommand(8))
        assertEquals("D707A90000C878", model.speedCommand(20))
        assertEquals("D707A900012CDD", model.speedCommand(30))
        assertNull(model.speedCommand(31))
    }

    @Test
    fun advancedModeCommand_generatesChecksumCompatibleCommands() {
        val model = ScooterCommandCatalog.findModel("s04_pro_gen3")

        assertEquals("D706A30001AA0D0A", model.advancedModeCommand(1))
        assertEquals("D706A300560F0D0A", model.advancedModeCommand(86))
        assertEquals("D706A30058110D0A", model.advancedModeCommand(88))
        assertEquals("D706A300FEB70D0A", model.advancedModeCommand(254))
        assertNull(model.advancedModeCommand(255))
    }
}
