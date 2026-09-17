package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.isSettlement

/**
 * One thing you did that the province can judge: what it was, on which day,
 * and whose ground it touched. The journal and the rumor mill both read these.
 */
data class Deed(
    val day: Int,
    val domain: String,
    val text: String,
    val siteId: Int? = null,
    val powerId: Int? = null,
    val houseId: Int? = null,
    val deityId: Int? = null
) {
    /** Compact form for the save file; deed text never contains the separators. */
    fun encode(): String =
        listOf(
            day.toString(),
            domain,
            (siteId ?: -1).toString(),
            (powerId ?: -1).toString(),
            (houseId ?: -1).toString(),
            (deityId ?: -1).toString(),
            text
        ).joinToString("|")

    companion object {
        fun decode(raw: String): Deed? {
            val parts = raw.split("|", limit = 7)
            if (parts.size < 7) return null
            val day = parts[0].toIntOrNull() ?: return null
            return Deed(
                day = day,
                domain = parts[1],
                siteId = parts[2].toIntOrNull()?.takeIf { it >= 0 },
                powerId = parts[3].toIntOrNull()?.takeIf { it >= 0 },
                houseId = parts[4].toIntOrNull()?.takeIf { it >= 0 },
                deityId = parts[5].toIntOrNull()?.takeIf { it >= 0 },
                text = parts[6]
            )
        }
    }
}

/** The four ledgers the province keeps on you. */
enum class Layer {
    /** Political standing with a power. */
    POWER,

    /** How the locals of one settlement speak of you at their gates. */
    SETTLEMENT,

    /** A noble house's favor. */
    HOUSE,

    /** Piety in one god's eyes. */
    DEITY
}

/** How the locals speak of you at the gates. */
fun regardLabel(value: Int): String = when {
    value <= -60 -> "Barred"
    value <= -25 -> "Unwelcome"
    value < 10 -> "Passing"
    value < 45 -> "Familiar"
    value < 75 -> "Fond"
    else -> "Beloved"
}

/** How a god's priests name you. */
fun pietyLabel(value: Int): String = when {
    value <= -60 -> "Abandoned"
    value <= -25 -> "Angered"
    value < 10 -> "Unheard"
    value < 45 -> "Heard"
    value < 75 -> "Blessed"
    else -> "Chosen"
}

/**
 * The province's layered memory of the delver: political standing, local regard,
 * house favor and piety, each held in -100..100 with the deed that last moved it.
 * Seeded from the frozen history so the same world always opens with the same
 * opinion of you, and encoded whole into the save so it survives quitting.
 */
class Reputation(world: World) {

    val powerStanding = mutableMapOf<Int, Int>()
    val settlementRegard = mutableMapOf<Int, Int>()
    val houseFavor = mutableMapOf<Int, Int>()
    val deityPiety = mutableMapOf<Int, Int>()
    val deedLog = mutableListOf<Deed>()

    private val reasons = mutableMapOf<Layer, MutableMap<Int, String>>()

    init {
        val vaultSite = world.site(world.vaultSiteId)
        val vaultHolder = vaultSite.holderPowerId
        val vaultClaimPowers = world.claims
            .filter { it.siteId == vaultSite.id }
            .mapNotNull { it.claimantPowerId }
            .toSet()

        // Waking among them: the sealed ground was somebody's to seal, and somebody else's to want.
        world.powers.forEach { power ->
            powerStanding[power.id] = when {
                power.hostileByNature -> -30
                power.id == vaultHolder -> -15
                power.id in vaultClaimPowers -> -10
                else -> 10
            }
        }
        // Locals take after their lords, but lightly.
        world.sites.filter { it.isSettlement && !it.ruined }.forEach { site ->
            settlementRegard[site.id] = (powerStanding[site.holderPowerId] ?: 0) / 2
        }
        // Houses inherit their patron's quarrels.
        world.houses.forEach { house ->
            val patron = house.patronPowerId?.let { powerStanding[it] } ?: 0
            houseFavor[house.id] = if (patron < 0) -15 else 0
        }
        // Gods whose followers hate outsiders do not listen to one.
        world.deities.forEach { deity ->
            val creedHostile = world.powers.any { it.cultureId == deity.cultureId && it.hostileByNature }
            deityPiety[deity.id] = if (creedHostile) -10 else 0
        }
    }

    fun standingFor(powerId: Int?): Int = value(Layer.POWER, powerId)

    fun regardFor(siteId: Int?): Int = value(Layer.SETTLEMENT, siteId)

    fun favorFor(houseId: Int?): Int = value(Layer.HOUSE, houseId)

    fun pietyFor(deityId: Int?): Int = value(Layer.DEITY, deityId)

    /** The most recent deed that moved this opinion — the "why" the journal prints. */
    fun reasonFor(layer: Layer, id: Int?): String? = reasons[layer]?.get(id)

    /** Move an opinion and remember what moved it. */
    fun adjust(layer: Layer, id: Int?, delta: Int, why: String) {
        id ?: return
        if (delta == 0) return
        val map = mapFor(layer)
        map[id] = ((map[id] ?: 0) + delta).coerceIn(-100, 100)
        reasons.getOrPut(layer) { mutableMapOf() }[id] = why
    }

    /** Add a deed to the log the journal reads; the log keeps only the recent past. */
    fun logDeed(deed: Deed) {
        deedLog.add(deed)
        if (deedLog.size > DEED_LOG_CAP) deedLog.removeAt(0)
    }

    private fun value(layer: Layer, id: Int?): Int {
        id ?: return 0
        return (mapFor(layer)[id] ?: 0).coerceIn(-100, 100)
    }

    private fun mapFor(layer: Layer): MutableMap<Int, Int> = when (layer) {
        Layer.POWER -> powerStanding
        Layer.SETTLEMENT -> settlementRegard
        Layer.HOUSE -> houseFavor
        Layer.DEITY -> deityPiety
    }

    // ------------------------------------------------------------ persistence

    /**
     * Whole-ledger encoding for the save file: opinion maps, reasons and the deed
     * log, in sections the decoder can skip past if a field comes back short.
     */
    fun encode(): String {
        val maps = Layer.entries.joinToString(ENTRY) { layer ->
            "${layer.name}$PAIR" + mapFor(layer).entries.joinToString(ENTRY) { "${it.key}$FIELD${it.value}" }
        }
        val whys = Layer.entries.joinToString(ENTRY) { layer ->
            val byId = reasons[layer] ?: return@joinToString ""
            byId.entries.joinToString(ENTRY) { "${layer.name}$PAIR${it.key}$FIELD${it.value}" }
        }
        val deeds = deedLog.joinToString(ENTRY) { it.encode() }
        return listOf(maps, whys, deeds).joinToString(SECTION)
    }

    /** Restore a ledger saved with [encode]; malformed sections are ignored. */
    fun restore(encoded: String) {
        val sections = encoded.split(SECTION)
        if (sections.isEmpty()) return

        sections[0].split(ENTRY).forEach { piece ->
            val parts = piece.split(PAIR, FIELD, limit = 3)
            if (parts.size != 3) return@forEach
            val layer = Layer.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
            val id = parts[1].toIntOrNull() ?: return@forEach
            val value = parts[2].toIntOrNull() ?: return@forEach
            mapFor(layer)[id] = value.coerceIn(-100, 100)
        }

        if (sections.size > 1) {
            sections[1].split(ENTRY).forEach { piece ->
                val parts = piece.split(PAIR, FIELD, limit = 3)
                if (parts.size != 3) return@forEach
                val layer = Layer.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                val id = parts[1].toIntOrNull() ?: return@forEach
                reasons.getOrPut(layer) { mutableMapOf() }[id] = parts[2]
            }
        }

        if (sections.size > 2) {
            deedLog.clear()
            sections[2].split(ENTRY).forEach { piece ->
                if (piece.isBlank()) return@forEach
                Deed.decode(piece)?.let { deedLog.add(it) }
            }
        }
    }

    companion object {
        private const val SECTION = "\u001F"
        private const val ENTRY = "\u001E"
        private const val PAIR = ":"
        private const val FIELD = "="
        private const val DEED_LOG_CAP = 24

        /** A ledger rebuilt from a save; falls back to the seeded one if the data is lost. */
        fun fromSave(world: World, encoded: String?): Reputation {
            val fresh = Reputation(world)
            if (!encoded.isNullOrBlank()) fresh.restore(encoded)
            return fresh
        }
    }
}
