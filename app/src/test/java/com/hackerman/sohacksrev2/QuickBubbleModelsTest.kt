package com.hackerman.sohacksrev2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBubbleModelsTest {
    @Test
    fun scopeSeparatesPhysicalDevicesAndModels() {
        val firstDevice = QuickBubbleScope("so4", "AA:BB:CC:DD:EE:01")
        val secondDevice = QuickBubbleScope("so4", "AA:BB:CC:DD:EE:02")
        val secondModel = QuickBubbleScope("so6", "AA:BB:CC:DD:EE:01")

        assertFalse(firstDevice.storageKey == secondDevice.storageKey)
        assertFalse(firstDevice.storageKey == secondModel.storageKey)
        assertEquals(
            firstDevice.storageKey,
            QuickBubbleScope("so4", "aa:bb:cc:dd:ee:01").storageKey
        )
    }

    @Test
    fun bubbleGeometryAndNameAreNormalized() {
        val bubble = QuickBubble(
            name = "  Eine sehr lange Bubble  ",
            actions = listOf(QuickBubbleAction(QuickBubbleActionType.SPORT)),
            positionX = -4f,
            positionY = 9f,
            sizeDp = 500
        ).normalized()

        assertEquals(QuickBubble.MAX_NAME_LENGTH, bubble.name.length)
        assertEquals(0f, bubble.positionX)
        assertEquals(1f, bubble.positionY)
        assertEquals(QuickBubble.MAX_SIZE_DP, bubble.sizeDp)
    }

    @Test
    fun actionsAreValidatedAgainstSelectedModel() {
        val so4 = ScooterCommandCatalog.findModel("so4")
        val so6 = ScooterCommandCatalog.findModel("so6")

        assertTrue(QuickBubbleAction(QuickBubbleActionType.SPORT).isAvailableFor(so4))
        assertTrue(QuickBubbleAction(QuickBubbleActionType.SPEED, "25").isAvailableFor(so4))
        assertFalse(QuickBubbleAction(QuickBubbleActionType.SPEED, "25").isAvailableFor(so6))
        assertTrue(QuickBubbleAction(QuickBubbleActionType.EXTRA, "light_on").isAvailableFor(so6))
        assertTrue(QuickBubbleAction(QuickBubbleActionType.EXIT_APP).isAvailableFor(so6))
    }
}
