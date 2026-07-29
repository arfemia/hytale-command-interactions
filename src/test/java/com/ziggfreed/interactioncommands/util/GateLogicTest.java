package com.ziggfreed.interactioncommands.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GateLogicTest {

    @Test
    void cooldownIsDisabledWhenZeroOrNegative() {
        assertTrue(GateLogic.cooldownDisabled(0f));
        assertTrue(GateLogic.cooldownDisabled(-1f));
    }

    @Test
    void cooldownIsEnabledWhenPositive() {
        assertFalse(GateLogic.cooldownDisabled(0.01f));
        assertFalse(GateLogic.cooldownDisabled(30f));
    }

    @Test
    void chanceOfOneAlwaysFiresRegardlessOfRoll() {
        assertFalse(GateLogic.missesChance(1f, 0f));
        assertFalse(GateLogic.missesChance(1f, 0.9999f));
    }

    @Test
    void chanceOfZeroAlwaysMisses() {
        assertTrue(GateLogic.missesChance(0f, 0f));
        assertTrue(GateLogic.missesChance(0f, 0.9999f));
    }

    @Test
    void midChanceHitsBelowThresholdAndMissesAtOrAbove() {
        assertFalse(GateLogic.missesChance(0.5f, 0.4f));
        assertTrue(GateLogic.missesChance(0.5f, 0.5f));
        assertTrue(GateLogic.missesChance(0.5f, 0.6f));
    }
}
