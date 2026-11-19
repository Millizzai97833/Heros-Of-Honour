package com.example

import com.example.game.AntiCheatEngine
import com.example.model.GameCatalog
import com.example.model.PlayerProfile
import com.example.model.WarTheatre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun antiCheat_validatesCleanAndFlaggedMatches() {
        val ac = AntiCheatEngine()
        ac.resetMatchAudit(0f, 0f)
        assertTrue(ac.auditWeaponFire(0.5f, 45))
        assertFalse(ac.auditWeaponFire(0.5f, 999)) // Excessive damage tamper

        val flagged = ac.auditMatchOutcome(
            uid = "tester",
            theatre = WarTheatre.TANK,
            missionId = "mis_tank_iron",
            kills = 5,
            damageDealt = 300,
            shotsFired = 10,
            durationSec = 30
        )
        assertFalse(flagged.integrityPassed)
        assertTrue("DAMAGE_TAMPER" in flagged.flags)
        assertTrue("FIRE_RATE_EXPLOIT" in flagged.flags)
    }

    @Test
    fun loadoutStats_scalesWithAttachmentsAndSkills() {
        val baseProfile = PlayerProfile(userId = "u1")
        val upgradedProfile = baseProfile.copy(
            equippedWeapon = "wpn_tank_rail",
            equippedArmor = "arm_titanium",
            unlockedSkills = listOf("skl_adrenaline", "skl_fortress")
        )
        val baseStats = GameCatalog.computeLoadoutStats(baseProfile, WarTheatre.TANK)
        val upStats = GameCatalog.computeLoadoutStats(upgradedProfile, WarTheatre.TANK)
        assertTrue(upStats.damage > baseStats.damage)
        assertTrue(upStats.maxHp > baseStats.maxHp)
        assertEquals(6, GameCatalog.missions.size)
    }
}
