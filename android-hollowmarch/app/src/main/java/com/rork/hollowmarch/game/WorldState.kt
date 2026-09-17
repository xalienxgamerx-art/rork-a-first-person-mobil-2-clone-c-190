package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.World

/**
 * Persistent World v1 — the authoritative record of what has *happened* to the
 * generated province.
 *
 * The architecture this file completes:
 *
 *     world seed -> deterministic generation -> base world (immutable [World])
 *                -> persistent [WorldState] (this file)
 *                -> runtime scene ([GameMap], rebuilt on demand)
 *                -> renderer
 *
 * The base world says what originally exists. This says what became of it.
 * The loaded [GameMap] is only a view: it is never the authority for whether
 * something exists. Nothing here duplicates generated data — only the deltas.
 */

/** The state contract's version, written into every save so old ones can be read. */
const val WORLD_STATE_VERSION = 1

/** The kinds of thing the world remembers by name of number, not by given name. */
enum class PersistKind(val tag: String) {
    /** A named soul of a living place. */
    RESIDENT("r"),
    /** A creature of the dark, rolled by generation. */
    CREATURE("c"),
    /** The thing that keeps the lowest floor. */
    BOSS("x"),
    /** A megabeast of the chronicle: one id for the whole province. */
    BEAST("b"),
    /** A grave, urn, chest or cache that can be emptied. */
    CONTAINER("k");

    companion object {
        fun byTag(tag: String): PersistKind? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Stable, deterministic identity for persistent things.
 *
 * An id is derived from the world seed, the kind of thing, the generation
 * context (which site, which floor) and the index generation gave it within
 * that context. It is never derived from a given name: two souls called the
 * same thing are two different souls, and both are remembered apart.
 *
 * Generation order is deterministic, so the same id names the same thing across
 * leaving an area, rebuilding the scene, saving, loading and restarting.
 */
object EntityKey {
    private const val SEP = "#"

    /** The world's own short stamp, so ids of one province never read as another's. */
    fun seedTag(seed: Long): String = seed.toString(36)

    fun of(seed: Long, kind: PersistKind, siteId: Int, floor: Int, index: Int): String =
        listOf(seedTag(seed), kind.tag, "$siteId", "$floor", "$index").joinToString(SEP)

    /** A chronicle beast carries its world id wherever it is met. */
    fun beast(seed: Long, beastId: Int): String =
        listOf(seedTag(seed), PersistKind.BEAST.tag, "$beastId").joinToString(SEP)

    /** Which kind an id names, read back from the id itself. */
    fun kindOf(id: String): PersistKind? =
        id.split(SEP).getOrNull(1)?.let { PersistKind.byTag(it) }

    /** One face of one place: the generation context a scene is rebuilt from. */
    fun scene(siteId: Int, floor: Int): String = "$siteId/$floor"
}

/** The kinds of change the world keeps a ledger of. */
enum class WorldChangeKind {
    DEATH, LOOT, MOVE, DISCOVERY, SETTLEMENT, FACTION, RUMOR, WEATHER, OBJECT, EVENT
}

/**
 * One meaningful thing that happened to the world, in a form that survives
 * unloading, travel, saving and loading. Deliberately not an event-sourcing
 * framework: the state above is authoritative, and this is its ledger.
 */
data class WorldChange(
    val minutes: Float,
    val kind: WorldChangeKind,
    /** What it happened to: an entity id, a site id, a power id. */
    val subject: String,
    val text: String
) {
    val day: Int get() = (minutes / 1440f).toInt() + 1
}

/**
 * What the world knows of one soul when nobody is looking at it. Enough to put
 * it back on its feet in the right place, with the right allegiance and the
 * right state, without the scene it belongs to being loaded.
 */
data class NpcRecord(
    val id: String,
    val name: String,
    val siteId: Int,
    val floor: Int,
    /** The building this soul keeps, or -1 for the homeless. */
    val homeBuilding: Int,
    /** The power whose ground it lives on, when the ground has a holder. */
    val powerId: Int,
    /** Where generation first set it down: always an open cell. */
    val anchorX: Float,
    val anchorY: Float,
    var x: Float,
    var y: Float,
    var alive: Boolean = true,
    /** What it is doing right now, in the chronicler's words. */
    var activity: String = "about its day",
    /** The trade this soul was dealt: a role from the registry, blank for the untraded. */
    var role: String = "",
    /** The building this soul works at, or -1 when the work has no door of its own. */
    var workBuilding: Int = -1,
    /** The dealt heart, or null for records written before souls had them. */
    var personality: Personality? = null
)

/** The day and the folk count a scene was built from, frozen so it rebuilds the same. */
data class SceneStamp(val day: Int, val folk: Int)

/**
 * The province's memory. Sparse by design: it holds deaths, emptied containers,
 * moved souls, discoveries and events — never a copy of the terrain, the sites
 * or the creature rolls, all of which the seed already answers for.
 */
class WorldState(val seed: Long, val version: Int = WORLD_STATE_VERSION) {

    /** Things that will not stand again, by stable id. */
    private val dead = mutableSetOf<String>()

    /** Containers the world remembers being emptied, by stable id. */
    private val emptied = mutableSetOf<String>()

    /** Beasts of the chronicle put down, by their world id. */
    private val slainBeasts = mutableSetOf<Int>()

    /** Places you have stood, and the day you last stood there. */
    val discovered = mutableSetOf<Int>()
    private val discoveredOn = mutableMapOf<Int, Int>()

    /** Dead places whose seals you have broken — the holders remember. */
    val delvedSites = mutableSetOf<Int>()

    /** Souls the world is keeping track of, by stable id. */
    private val npcs = linkedMapOf<String, NpcRecord>()

    /** What each scene was built from, so reconstruction is not a fresh roll. */
    private val sceneStamps = linkedMapOf<String, SceneStamp>()

    /** How hard each power has been pressing its business while you were away. */
    val factionPressure = linkedMapOf<Int, Int>()

    /** The world clock the simulation has been carried up to. */
    var lastSimMinutes: Float = 0f

    /** The ledger of meaningful change, newest last. */
    private val changes = ArrayDeque<WorldChange>()

    /** Keys from saves written before stable ids existed, kept until migrated. */
    private val legacy = mutableSetOf<String>()

    // ------------------------------------------------------------------ deaths

    fun markDead(id: String, minutes: Float, text: String) {
        if (id.isBlank() || !dead.add(id)) return
        npcs[id]?.alive = false
        record(WorldChange(minutes, WorldChangeKind.DEATH, id, text))
    }

    fun isDead(id: String): Boolean = id.isNotBlank() && id in dead

    val deadCount: Int get() = dead.size

    // -------------------------------------------------------------- containers

    fun markEmptied(id: String, minutes: Float, text: String) {
        if (id.isBlank() || !emptied.add(id)) return
        record(WorldChange(minutes, WorldChangeKind.LOOT, id, text))
    }

    fun isEmptied(id: String): Boolean = id.isNotBlank() && id in emptied

    // ------------------------------------------------------------------ beasts

    fun slayBeast(beastId: Int, minutes: Float, text: String) {
        if (beastId < 0 || !slainBeasts.add(beastId)) return
        record(WorldChange(minutes, WorldChangeKind.DEATH, "beast:$beastId", text))
    }

    fun slainBeastIds(): Set<Int> = slainBeasts

    // ------------------------------------------------------------- discoveries

    fun discover(siteId: Int, day: Int, minutes: Float, name: String) {
        val first = discovered.add(siteId)
        discoveredOn[siteId] = day
        if (first) {
            record(WorldChange(minutes, WorldChangeKind.DISCOVERY, "$siteId", "Came to $name"))
        }
    }

    fun visitedDay(siteId: Int): Int? = discoveredOn[siteId]

    // -------------------------------------------------------------------- npcs

    fun npc(id: String): NpcRecord? = npcs[id]

    /** Every soul the world is tracking in one face of one place. */
    fun npcsIn(siteId: Int, floor: Int): List<NpcRecord> =
        npcs.values.filter { it.siteId == siteId && it.floor == floor }

    /** Every soul still standing, wherever it is. */
    fun livingNpcs(): List<NpcRecord> = npcs.values.filter { it.alive }

    /** Take note of a soul the first time the world sees it; keep what it knows after. */
    fun rememberNpc(record: NpcRecord) {
        val known = npcs[record.id]
        if (known == null) {
            npcs[record.id] = record.also { it.alive = !isDead(it.id) }
        } else {
            if (isDead(record.id)) known.alive = false
            // the trade was dealt once; an older record keeps it
            if (known.role.isBlank() && record.role.isNotBlank()) {
                known.role = record.role
                known.workBuilding = record.workBuilding
            }
            // the heart was dealt once; an older record keeps it
            if (known.personality == null && record.personality != null) {
                known.personality = record.personality
            }
        }
    }

    val npcCount: Int get() = npcs.size

    // ------------------------------------------------------------------ scenes

    /**
     * The day and folk a scene must be rebuilt from. The first visit freezes
     * them; later visits reuse them, so re-entering a place reconstructs it
     * from base world plus this state instead of rolling a fresh, unrelated one.
     * A place whose folk carried it to another stage is re-stamped: those walls
     * really did change.
     */
    fun sceneStamp(sceneKey: String, site: Site, day: Int, folk: Int): SceneStamp {
        val known = sceneStamps[sceneKey]
        if (known != null) {
            val grew = stageOf(site, folk) != stageOf(site, known.folk)
            if (!grew) return known
        }
        val stamp = SceneStamp(day, folk)
        sceneStamps[sceneKey] = stamp
        return stamp
    }

    fun knownStamp(sceneKey: String): SceneStamp? = sceneStamps[sceneKey]

    // ------------------------------------------------------------------ ledger

    fun record(change: WorldChange) {
        changes.addLast(change)
        while (changes.size > LEDGER_LENGTH) changes.removeFirst()
    }

    /** The ledger, oldest first. */
    fun changes(): List<WorldChange> = changes.toList()

    fun changesOf(kind: WorldChangeKind): List<WorldChange> = changes.filter { it.kind == kind }

    // ------------------------------------------------------------- old saves

    /** Keys from a save written before stable ids; migrated scene by scene. */
    fun takeLegacyKeys(keys: Collection<String>) {
        legacy += keys.filter { it.isNotBlank() }
    }

    fun hasLegacy(): Boolean = legacy.isNotEmpty()

    /**
     * Fold a pre-id save's memory of one scene into stable ids. Old keys named
     * containers by their index in the whole entity list, bosses and beasts by
     * number, and souls by their given name; each is matched once, here, and
     * then the world speaks only in ids.
     */
    fun migrateScene(siteId: Int, floor: Int, entities: List<Entity>) {
        if (legacy.isEmpty()) return
        if ("boss:$siteId" in legacy) {
            entities.filter { it.boss && it.persistId.isNotBlank() }
                .forEach { dead += it.persistId }
        }
        entities.forEachIndexed { index, entity ->
            val id = entity.persistId
            if (id.isBlank()) return@forEachIndexed
            if (entity.container) {
                val flat = "$siteId:$index"
                val inBuilding = if (floor >= SiteGen.BUILDING_FLOOR_BASE) {
                    "$siteId:b${floor - SiteGen.BUILDING_FLOOR_BASE}:$index"
                } else {
                    flat
                }
                if (flat in legacy || inBuilding in legacy) emptied += id
            }
            if (entity.resident && "slain:$siteId:${entity.name}" in legacy) dead += id
            if (entity.beastId >= 0 && "beast:${entity.beastId}" in legacy) {
                slainBeasts += entity.beastId
                dead += id
            }
        }
        legacy.filter { it.startsWith("beast:") }
            .mapNotNull { it.removePrefix("beast:").toIntOrNull() }
            .forEach { slainBeasts += it }
    }

    // --------------------------------------------------------------- the save

    /** The whole of the world's memory, packed for the save. */
    fun encode(): String {
        val sections = mutableListOf<String>()
        sections += "V$FIELD$version"
        sections += "S$FIELD${EntityKey.seedTag(seed)}"
        sections += "T$FIELD$lastSimMinutes"
        sections += "D$FIELD${dead.joinToString(RECORD)}"
        sections += "K$FIELD${emptied.joinToString(RECORD)}"
        sections += "B$FIELD${slainBeasts.joinToString(RECORD)}"
        sections += "E$FIELD${delvedSites.joinToString(RECORD)}"
        sections += "G$FIELD" + discovered.joinToString(RECORD) { "$it=${discoveredOn[it] ?: 0}" }
        sections += "P$FIELD" + sceneStamps.entries.joinToString(RECORD) { (key, stamp) ->
            "$key=${stamp.day}=${stamp.folk}"
        }
        sections += "F$FIELD" + factionPressure.entries.joinToString(RECORD) { "${it.key}=${it.value}" }
        sections += "N$FIELD" + npcs.values.joinToString(RECORD) { npc ->
            listOf(
                npc.id, npc.name, "${npc.siteId}", "${npc.floor}", "${npc.homeBuilding}",
                "${npc.powerId}", "${npc.anchorX}", "${npc.anchorY}", "${npc.x}", "${npc.y}",
                "${npc.alive}", npc.activity, npc.role, "${npc.workBuilding}",
                npc.personality?.encode() ?: ""
            ).joinToString(FIELD)
        }
        sections += "C$FIELD" + changes.joinToString(RECORD) { change ->
            listOf("${change.minutes}", change.kind.name, change.subject, change.text)
                .joinToString(FIELD)
        }
        sections += "L$FIELD${legacy.joinToString(RECORD)}"
        return sections.joinToString(SECTION)
    }

    companion object {
        /** How many changes the ledger keeps; the state above is the authority. */
        const val LEDGER_LENGTH = 64

        private const val SECTION = "\u001D"
        private const val RECORD = "\u001C"
        private const val FIELD = "\u001B"

        /**
         * Wake the world's memory from a save. A blank or unreadable string, or
         * one written for another world, gives a clean memory — the base world
         * then stands exactly as generated, which is the correct fallback.
         */
        fun decode(raw: String?, seed: Long, legacyLooted: Collection<String> = emptyList()): WorldState {
            val state = WorldState(seed)
            state.takeLegacyKeys(legacyLooted)
            if (raw.isNullOrBlank()) return state
            val sections = raw.split(SECTION).mapNotNull { section ->
                val cut = section.indexOf(FIELD)
                if (cut <= 0) null else section.substring(0, cut) to section.substring(cut + 1)
            }.toMap()
            // A save of another province is not this province's memory.
            val stamp = sections["S"]
            if (stamp != null && stamp != EntityKey.seedTag(seed)) return state
            fun records(key: String): List<String> =
                sections[key]?.split(RECORD)?.filter { it.isNotBlank() } ?: emptyList()

            state.lastSimMinutes = sections["T"]?.toFloatOrNull() ?: 0f
            state.dead += records("D")
            state.emptied += records("K")
            state.slainBeasts += records("B").mapNotNull { it.toIntOrNull() }
            state.delvedSites += records("E").mapNotNull { it.toIntOrNull() }
            records("G").forEach { piece ->
                val parts = piece.split("=")
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                state.discovered += id
                parts.getOrNull(1)?.toIntOrNull()?.let { state.discoveredOn[id] = it }
            }
            records("P").forEach { piece ->
                val parts = piece.split("=")
                if (parts.size < 3) return@forEach
                val day = parts[1].toIntOrNull() ?: return@forEach
                val folk = parts[2].toIntOrNull() ?: return@forEach
                state.sceneStamps[parts[0]] = SceneStamp(day, folk)
            }
            records("F").forEach { piece ->
                val parts = piece.split("=")
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                state.factionPressure[id] = parts.getOrNull(1)?.toIntOrNull() ?: 0
            }
            records("N").forEach { piece ->
                val f = piece.split(FIELD)
                if (f.size < 12) return@forEach
                val id = f[0]
                if (id.isBlank()) return@forEach
                state.npcs[id] = NpcRecord(
                    id = id,
                    name = f[1],
                    siteId = f[2].toIntOrNull() ?: -1,
                    floor = f[3].toIntOrNull() ?: 0,
                    homeBuilding = f[4].toIntOrNull() ?: -1,
                    powerId = f[5].toIntOrNull() ?: -1,
                    anchorX = f[6].toFloatOrNull() ?: 0f,
                    anchorY = f[7].toFloatOrNull() ?: 0f,
                    x = f[8].toFloatOrNull() ?: 0f,
                    y = f[9].toFloatOrNull() ?: 0f,
                    alive = f[10] == "true",
                    activity = f[11],
                    role = f.getOrNull(12) ?: "",
                    workBuilding = f.getOrNull(13)?.toIntOrNull() ?: -1,
                    personality = f.getOrNull(14)?.takeIf { it.isNotBlank() }?.let { Personality.decode(it) }
                )
            }
            records("C").forEach { piece ->
                val f = piece.split(FIELD)
                if (f.size < 4) return@forEach
                val kind = runCatching { WorldChangeKind.valueOf(f[1]) }.getOrNull() ?: return@forEach
                state.changes.addLast(
                    WorldChange(f[0].toFloatOrNull() ?: 0f, kind, f[2], f[3])
                )
            }
            state.takeLegacyKeys(records("L"))
            return state
        }
    }
}

/**
 * The bridge between the world's memory and a loaded scene: stamping stable ids
 * onto freshly generated entities, and putting the scene back the way the world
 * remembers it.
 */
object SceneBinder {

    /** Which kind of persistent thing an entity is, or null when it is scenery or passing. */
    fun kindOf(entity: Entity): PersistKind? = when {
        entity.beastId >= 0 -> PersistKind.BEAST
        entity.traveler -> null
        entity.boss -> PersistKind.BOSS
        entity.resident -> PersistKind.RESIDENT
        entity.container -> PersistKind.CONTAINER
        entity.kind == EntityKind.ENEMY -> PersistKind.CREATURE
        else -> null
    }

    /**
     * Give every persistent thing in a freshly built scene its stable id.
     * Generation order is deterministic, so the index each kind is counted out
     * in is deterministic too — and identical on every rebuild of that scene.
     * Scenery and souls met on the road take no id: they are not persistent.
     */
    fun stamp(seed: Long, siteId: Int, floor: Int, entities: List<Entity>) {
        val counters = mutableMapOf<PersistKind, Int>()
        entities.forEach { entity ->
            val kind = kindOf(entity)
            if (kind == null) {
                entity.persistId = ""
                return@forEach
            }
            entity.persistId = if (kind == PersistKind.BEAST) {
                EntityKey.beast(seed, entity.beastId)
            } else {
                val index = counters.getOrElse(kind) { 0 }
                counters[kind] = index + 1
                EntityKey.of(seed, kind, siteId, floor, index)
            }
        }
    }

    /**
     * Take note of the souls of a scene, so the world can keep them when the
     * scene is gone. Their spawn spot is kept as an anchor: generation only
     * ever sets them down on open ground, so it is always somewhere they can
     * legally stand.
     */
    fun remember(state: WorldState, world: World, siteId: Int, floor: Int, map: GameMap) {
        val powerId = world.sites.firstOrNull { it.id == siteId }?.holderPowerId ?: -1
        map.entities.forEach { entity ->
            if (!entity.resident || entity.persistId.isBlank()) return@forEach
            state.rememberNpc(
                NpcRecord(
                    id = entity.persistId,
                    name = entity.name,
                    siteId = siteId,
                    floor = floor,
                    homeBuilding = entity.homeBuilding,
                    powerId = powerId,
                    anchorX = entity.x,
                    anchorY = entity.y,
                    x = entity.x,
                    y = entity.y,
                    role = entity.role,
                    workBuilding = entity.workBuilding,
                    personality = entity.personality
                )
            )
        }
    }

    /**
     * Put the scene back the way the world remembers it: the dead do not stand,
     * emptied containers hold nothing, and every soul stands where the world
     * last left it — never inside a wall.
     */
    fun restore(state: WorldState, siteId: Int, floor: Int, map: GameMap) {
        map.entities.removeAll { it.persistId.isNotBlank() && state.isDead(it.persistId) }
        map.entities.forEach { entity ->
            if (entity.container && state.isEmptied(entity.persistId)) {
                entity.loot.clear()
                entity.lootBrass = 0
            }
            if (entity.resident) {
                val record = state.npc(entity.persistId) ?: return@forEach
                if (!map.isWall(record.x, record.y)) {
                    entity.x = record.x
                    entity.y = record.y
                }
                // the trade is the world's own memory: the persisted role stands
                if (record.role.isNotBlank()) {
                    entity.role = record.role
                    entity.workBuilding = record.workBuilding
                }
                // so does the heart
                if (record.personality != null) {
                    entity.personality = record.personality
                }
            }
        }
    }
}
