package com.rork.hollowmarch.world

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The founding promise of worldgen: same seed, same province, always. */
class WorldGeneratorTest {

    @Test
    fun sameSeedProducesSameProvince() {
        val a = WorldGenerator.generate(424242L)
        val b = WorldGenerator.generate(424242L)

        assertEquals(a.seedCode, b.seedCode)
        assertEquals(a.provinceName, b.provinceName)
        assertEquals(a.ages, b.ages)
        assertEquals(a.cultures, b.cultures)
        assertEquals(a.powers, b.powers)
        assertEquals(a.figures, b.figures)
        assertEquals(a.deities, b.deities)
        assertEquals(a.events, b.events)
        assertEquals(a.sites, b.sites)
        assertEquals(a.wars, b.wars)
        assertEquals(a.artifacts, b.artifacts)
        assertEquals(a.beasts, b.beasts)
        assertEquals(a.houses, b.houses)
        assertEquals(a.claims, b.claims)
        assertEquals(a.rumors, b.rumors)
        assertEquals(a.currentYear, b.currentYear)
        assertEquals(a.vaultSiteId, b.vaultSiteId)
        assertEquals(a.barrowSiteId, b.barrowSiteId)
        assertEquals(a.ruinCount, b.ruinCount)
        assertArrayEquals(a.terrain.heights, b.terrain.heights, 1e-5f)
        assertArrayEquals(a.terrain.moisture, b.terrain.moisture, 1e-5f)
        assertEquals(a.terrain.rivers, b.terrain.rivers)
    }

    @Test
    fun generatedWorldHasDepth() {
        val world = WorldGenerator.generate(424242L)

        assertTrue("expected several peoples", world.cultures.size >= 3)
        assertTrue("expected a pantheon", world.deities.isNotEmpty())
        assertTrue("expected rulers and their kin", world.figures.size >= world.powers.size)
        assertTrue("expected history with teeth", world.events.size >= 20)
        assertTrue("expected a town", world.sites.any { it.kind == SiteKind.TOWN })
        assertTrue("expected a capital", world.sites.any { it.kind == SiteKind.CAPITAL })
        assertTrue("expected villages", world.sites.any { it.kind == SiteKind.VILLAGE })
        assertTrue("expected a city", world.sites.any { it.kind == SiteKind.CITY })
        assertTrue("expected settlements on livable ground", world.sites.none { s -> s.isSettlement && !s.ruined && (s.x <= 0f || s.x >= 1f || s.y <= 0f || s.y >= 1f) })
        assertTrue("expected a vault", world.sites.any { it.kind == SiteKind.VAULT })
        assertTrue("expected rivers", world.terrain.rivers.isNotEmpty())
        assertTrue("expected a real war ledger", world.wars.isNotEmpty())
        assertTrue(
            "wars should carry their battles",
            world.wars.all { it.battles.isNotEmpty() && it.endYear != null && it.outcome.isNotEmpty() }
        )
        assertTrue("expected growth in the chronicle", world.events.any { it.kind == EventKind.GROWTH })
        assertTrue("expected destruction in the chronicle", world.events.any { it.kind == EventKind.DESTRUCTION })
        assertTrue("expected a living capital with people", world.sites.any { it.kind == SiteKind.CAPITAL && !it.ruined && it.population > 0 })
        assertTrue("expected land and sea", world.terrain.heights.isNotEmpty())
        assertTrue("expected rumors", world.rumors.isNotEmpty())

        // Living settlements: towns keep taverns and markets, cities raise guild halls.
        val bigLiving = world.sites.filter { !it.ruined && it.population > 0 }
        assertTrue(
            "a big town keeps a tavern and a market",
            bigLiving.any {
                (it.kind == SiteKind.TOWN || it.kind == SiteKind.CITY || it.kind == SiteKind.CAPITAL) &&
                    it.structures.any { st -> st.kind == StructureKind.TAVERN } &&
                    it.structures.any { st -> st.kind == StructureKind.MARKET }
            }
        )
        assertTrue(
            "cities raise guild halls",
            bigLiving.any { it.kind == SiteKind.CITY && it.structures.any { st -> st.kind == StructureKind.GUILD } }
        )
        assertTrue(
            "the dead go under the hill",
            world.sites.any { it.structures.any { st -> st.kind == StructureKind.CATACOMB } }
        )
        assertTrue(
            "keepers are real figures",
            world.figures.any { it.title.startsWith("priest of") } &&
                world.figures.any { it.title.startsWith("innkeeper of") } &&
                world.figures.any { it.title.startsWith("guildmaster of") }
        )
        assertTrue(
            "chronicle remembers the buildings",
            world.events.any { it.kind == EventKind.CONSECRATION } &&
                world.events.any { it.kind == EventKind.CHARTER }
        )
        // Wealth is earned: a power holding a market or guild hall out-earns its population tithe.
        assertTrue("wealth is earned, not rolled", world.powers.any { it.wealth > 120 })

        // Living politics: houses, claims, loyalties, relationships, agendas.
        assertTrue("noble houses take their names", world.houses.isNotEmpty())
        assertTrue(
            "houses are headed by real figures",
            world.houses.any { h -> h.headFigureId?.let { world.figures.getOrNull(it) } != null }
        )
        assertTrue("rulers belong to houses", world.figures.any { it.houseId != null })
        assertTrue("bloodlines are real", world.figures.any { it.parentIds.isNotEmpty() })
        assertTrue("rulers wed", world.figures.any { it.spouseId != null })
        assertTrue("the dispossessed press claims", world.claims.isNotEmpty())
        assertTrue(
            "claims cite why they are pressed",
            world.claims.all { it.origin.isNotEmpty() && it.strength in 10..95 } &&
                world.claims.any { it.strength >= 50 }
        )
        assertTrue(
            "politics is felt on the ground",
            world.events.any { it.kind == EventKind.REBELLION } ||
                world.sites.any { !it.ruined && it.loyalty < 30 }
        )
        assertTrue(
            "influence shares are coherent",
            world.sites
                .filter { !it.ruined && it.isSettlement && it.influences.isNotEmpty() }
                .all { s -> s.influences.sumOf { it.share } in 90..110 }
        )
        assertTrue("figures keep their rivals", world.figures.any { it.rivals.isNotEmpty() })
        assertTrue(
            "the chronicle remembers marriages and claims",
            world.events.any { it.kind == EventKind.MARRIAGE } &&
                world.events.any { it.kind == EventKind.CLAIM }
        )
        assertTrue("powers keep agendas", world.powers.any { it.goals.isNotEmpty() })
    }

    @Test
    fun forgeSettingsShapeTheWorld() {
        // A short, quiet history: few pages, exact years, fixed peoples.
        val short = WorldGenerator.generate(99L, historyYears = 300, maxEvents = 50, cultureCount = 6)
        assertEquals(300, short.currentYear)
        assertEquals(6, short.cultures.size)
        // The chronicle cap holds for the sim's own pages; the Drowning, its sealing,
        // and the sky omens always claim extra ones.
        val extraPages = short.events.count {
            it.kind == EventKind.FLOOD || it.kind == EventKind.SEALING ||
                (it.kind == EventKind.DESTRUCTION && it.text.contains("goes under the flood")) ||
                (it.kind == EventKind.ARTIFACT && it.text.contains("Grave-Nail")) ||
                it.kind == EventKind.COMET || it.kind == EventKind.ECLIPSE ||
                it.kind == EventKind.MOONWONDER
        }
        assertTrue(
            "capped chronicle: ${short.events.size - extraPages} regular pages",
            short.events.size - extraPages <= 50
        )
        assertTrue("short history should be quieter", short.events.size < 100)

        // No hard ceilings: deep histories, crowded provinces, an uncapped chronicle.
        val deep = WorldGenerator.generate(99L, historyYears = 2400, maxEvents = 0, cultureCount = 12)
        assertEquals(2400, deep.currentYear)
        assertEquals(12, deep.cultures.size)
        assertTrue("uncapped chronicle should run deep: ${deep.events.size}", deep.events.size > 53)

        // The same settings forge the same province, word or number.
        val again = WorldGenerator.generate(99L, historyYears = 300, maxEvents = 50, cultureCount = 6)
        assertEquals(short.seedCode, again.seedCode)
        assertEquals(short.events, again.events)
        assertEquals(short.rumors, again.rumors)
    }
}
