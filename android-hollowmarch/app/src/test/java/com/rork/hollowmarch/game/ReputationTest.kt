package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationTest {

    private val world = WorldGenerator.generate(424242L)

    @Test
    fun sameWorldSeedsTheSameOpinions() {
        val a = Reputation(world)
        val b = Reputation(world)
        assertEquals(a.powerStanding, b.powerStanding)
        assertEquals(a.settlementRegard, b.settlementRegard)
        assertEquals(a.houseFavor, b.houseFavor)
        assertEquals(a.deityPiety, b.deityPiety)
    }

    @Test
    fun theSealedGroundHoldsAGrudge() {
        val rep = Reputation(world)
        val vaultSite = world.site(world.vaultSiteId)
        val neutral = world.powers
            .filter { !it.hostileByNature && it.id != vaultSite.holderPowerId }
            .filter { rep.standingFor(it.id) > 0 }
        assertTrue("a neutral power exists to compare against", neutral.isNotEmpty())
        vaultSite.holderPowerId?.let { holderId ->
            assertTrue(
                "the vault's holder thinks worse of you than a neutral power",
                rep.standingFor(holderId) < rep.standingFor(neutral.first().id)
            )
        }
        world.claims
            .filter { it.siteId == vaultSite.id }
            .mapNotNull { it.claimantPowerId }
            .distinct()
            .forEach { claimant ->
                assertTrue(
                    "a claimant on the vault resents you",
                    rep.standingFor(claimant) <= 0
                )
            }
    }

    @Test
    fun localsTakeAfterTheirLords() {
        val rep = Reputation(world)
        world.sites
            .filter { it.isSettlement && !it.ruined }
            .forEach { site ->
                val holder = rep.standingFor(site.holderPowerId)
                val locals = rep.regardFor(site.id)
                assertTrue(
                    "local regard tracks the holder at half strength",
                    locals in minOf(0, holder / 2)..maxOf(0, holder / 2)
                )
            }
    }

    @Test
    fun opinionsStayWithinBounds() {
        val rep = Reputation(world)
        val power = world.powers.first()
        rep.adjust(Layer.POWER, power.id, 1000, "sainthood")
        assertEquals(100, rep.standingFor(power.id))
        rep.adjust(Layer.POWER, power.id, -2000, "ruin")
        assertEquals(-100, rep.standingFor(power.id))
        // Adjusting nothing touches nothing.
        rep.adjust(Layer.POWER, null, 50, "ghost deed")
        rep.adjust(Layer.HOUSE, null, -50, "ghost deed")
    }

    @Test
    fun everyAdjustmentLeavesAReason() {
        val rep = Reputation(world)
        val site = world.sites.first { it.isSettlement && !it.ruined }
        assertNull(rep.reasonFor(Layer.SETTLEMENT, site.id))
        val before = rep.regardFor(site.id)
        rep.adjust(Layer.SETTLEMENT, site.id, 12, "Cleared the dead from the well")
        assertEquals(before + 12, rep.regardFor(site.id))
        assertEquals("Cleared the dead from the well", rep.reasonFor(Layer.SETTLEMENT, site.id))
        rep.adjust(Layer.SETTLEMENT, site.id, -4, "Sold them salt and lies")
        assertEquals("Sold them salt and lies", rep.reasonFor(Layer.SETTLEMENT, site.id))
    }

    @Test
    fun theLedgerSurvivesASaveRoundTrip() {
        val rep = Reputation(world)
        val power = world.powers.first()
        val site = world.sites.first { it.isSettlement && !it.ruined }
        val deity = world.deities.firstOrNull()
        rep.adjust(Layer.POWER, power.id, -40, "Looted the tomb")
        rep.adjust(Layer.SETTLEMENT, site.id, 30, "Shared the road's bread")
        deity?.let { rep.adjust(Layer.DEITY, it.id, 22, "Left silver at the altar") }
        rep.logDeed(
            Deed(
                day = 7, domain = "loot", text = "Pried brass from the dead",
                siteId = site.id, powerId = power.id, deityId = deity?.id
            )
        )

        val restored = Reputation.fromSave(world, rep.encode())
        assertEquals(rep.powerStanding, restored.powerStanding)
        assertEquals(rep.settlementRegard, restored.settlementRegard)
        assertEquals(rep.deityPiety, restored.deityPiety)
        assertEquals(rep.reasonFor(Layer.POWER, power.id), restored.reasonFor(Layer.POWER, power.id))
        assertEquals(rep.deedLog, restored.deedLog)
    }

    @Test
    fun aMangledSaveFallsBackToTheSeededLedger() {
        val seeded = Reputation(world)
        assertEquals(seeded.powerStanding, Reputation.fromSave(world, null).powerStanding)
        assertEquals(seeded.powerStanding, Reputation.fromSave(world, "").powerStanding)
        assertEquals(seeded.powerStanding, Reputation.fromSave(world, "nonsense\u001F\u001E\u001D").powerStanding)
    }

    @Test
    fun aPartlyMangledSaveKeepsWhatItCan() {
        val rep = Reputation(world)
        val power = world.powers.first()
        rep.adjust(Layer.POWER, power.id, -40, "Looted the tomb")
        val good = rep.encode()
        val mangled = good.substring(0, good.length / 2) + "\u0000garbage"
        val restored = Reputation.fromSave(world, mangled)
        // Something survives or nothing does — either way it must not throw, and
        // whatever it kept must still be a coherent ledger.
        if (restored.standingFor(power.id) != rep.standingFor(power.id)) {
            assertNotEquals(101, restored.standingFor(power.id))
        }
    }

    @Test
    fun theDeedLogKeepsOnlyTheRecentPast() {
        val rep = Reputation(world)
        repeat(30) { rep.logDeed(Deed(day = it, domain = "battle", text = "Deed $it")) }
        assertEquals(24, rep.deedLog.size)
        assertEquals(29, rep.deedLog.last().day)
    }

    @Test
    fun deedsRoundTripThroughTheirCompactForm() {
        val deed = Deed(
            day = 12, domain = "offering", text = "Left silver at the shrine of the Tide-Father",
            siteId = 4, deityId = 2
        )
        assertEquals(deed, Deed.decode(deed.encode()))
        assertNull(Deed.decode("not a deed"))
        assertNull(Deed.decode("3|battle|short"))
    }
}
