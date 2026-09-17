package com.rork.hollowmarch.game

import android.util.Log
import androidx.lifecycle.ViewModel
import com.rork.hollowmarch.game.Skill
import com.rork.hollowmarch.world.Rumor
import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.WorldGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

data class FadingLine(val text: String, val alpha: Float)

data class HudState(
    val locationTitle: String = "",
    val heading: String = "N",
    val depthLabel: String = "",
    val vitality: Int = 0,
    val maxVitality: Int = 1,
    val fatigue: Int = 0,
    val maxFatigue: Int = 1,
    val magicka: Int = 0,
    val maxMagicka: Int = 1,
    val torch: Float = 1f,
    val outdoor: Boolean = false,
    val lines: List<FadingLine> = emptyList(),
    val usePrompt: String = "",
    val day: Int = 1,
    val clock: String = "0:00",
    val timeOfDay: String = "dusk",
    val brass: Int = 0,
    val kills: Int = 0,
    val explored: Int = 0,
    val dead: Boolean = false,
    val warded: Boolean = false,
    val level: Int = 1,
    val className: String = "",
    val crouched: Boolean = false,
    val blocking: Boolean = false,
    val detection: String = "",
    val load: Float = 0f,
    val capacity: Float = 1f,
    val lootable: String = "",
    val satchelCount: Int = 0,
    /** What the marksmanship hand is doing: the draw, the charge, the count of shot. */
    val rangedLine: String = ""
)

data class TitleState(
    val seedCode: String = "",
    val province: String = "",
    val ageLabel: String = "",
    val yearsSimulated: Int = 0,
    val peoples: Int = 0,
    val powers: Int = 0,
    val ruins: Int = 0,
    val figures: Int = 0,
    val wars: Int = 0,
    val relics: Int = 0,
    val beasts: Int = 0,
    val generationLog: List<String> = emptyList(),
    val continueLabel: String? = null
)

/** What you ask the forge for: a seed, and how much history to burn into it. */
data class WorldSettings(
    val seedText: String = "",
    val historyYears: Int = 400,
    val maxEvents: Int = 220,
    val peoples: Int = 0
) {
    /** Words become numbers the same way every time, so a word forges the same world. */
    fun resolvedSeed(): Long {
        val text = seedText.trim()
        if (text.isEmpty()) return Random.nextLong(1_000_000L, 9_999_999L)
        return text.toLongOrNull() ?: text.fold(1125899906842597L) { acc, c -> 31 * acc + c.code }
    }
}

/** Owns the province, the expedition inside it, and the snapshot the HUD reads. */
class GameViewModel : ViewModel() {

    private var _world: World = WorldGenerator.generate(SaveStore.load()?.seed ?: freshSeed())
    val world: World get() = _world

    /** The province's own roster of classes, named by its seed. */
    var classRoster: ClassRoster = ClassRoster(_world)
        private set

    private var _engine: GameEngine? = null
    val engine: GameEngine? get() = _engine

    private val _hud = MutableStateFlow(HudState())
    val hud: StateFlow<HudState> = _hud.asStateFlow()

    private val _title = MutableStateFlow(buildTitleState())
    val title: StateFlow<TitleState> = _title.asStateFlow()

    private var publishTimer = 0f

    private fun freshSeed(): Long = Random.nextLong(1_000_000L, 9_999_999L)

    private fun buildTitleState(): TitleState {
        val slot = SaveStore.load()
        val continueLabel = slot?.takeIf { it.seed == _world.seed }?.let {
            "Continue — ${it.siteName.ifBlank { _world.site(_world.vaultSiteId).name }}, Day ${it.day}"
        }
        return TitleState(
            seedCode = _world.seedCode,
            province = _world.provinceName,
            ageLabel = "${_world.currentAge.name}, Year ${_world.currentYear}",
            yearsSimulated = _world.currentYear,
            peoples = _world.cultures.size,
            powers = _world.powers.size,
            ruins = _world.ruinCount,
            figures = _world.figures.size,
            wars = _world.warCount(),
            relics = _world.artifacts.size,
            beasts = _world.beasts.size,
            generationLog = _world.highlightEvents(3).map { "Yr ${it.year} — ${it.text}" },
            continueLabel = continueLabel
        )
    }

    /** Forge a brand-new province from [settings] and roll a delver into its sealed vault. */
    fun forgeNewWorld(settings: WorldSettings = WorldSettings()) {
        SaveStore.clear()
        _world = WorldGenerator.generate(
            seed = settings.resolvedSeed(),
            historyYears = settings.historyYears,
            maxEvents = settings.maxEvents,
            cultureCount = settings.peoples
        )
        classRoster = ClassRoster(_world)
        _engine = null
        _title.value = buildTitleState()
    }

    fun startExpedition(resume: Boolean, creation: DelverCreation? = null) {
        val slot = if (resume) SaveStore.load()?.takeIf { it.seed == _world.seed } else null
        if (!resume) SaveStore.clear()
        _engine = GameEngine(_world, slot, creation)
        _engine?.let { logMapState("engine ready") }
        publish(force = true)
    }

    fun step(dt: Float) {
        val engine = _engine ?: return
        engine.update(dt)
        publishTimer += dt
        if (publishTimer > 0.12f) {
            publishTimer = 0f
            publish(force = false)
        }
    }

    // Each thumb writes only its own channels — a single shared setter let the
    // two sticks zero each other's axes the moment both were held down.
    fun setMoveInput(move: Float, strafe: Float) {
        _engine?.let {
            it.moveInput = move
            it.strafeInput = strafe
        }
    }

    fun setTurnInput(turn: Float) {
        _engine?.turnInput = turn
    }

    fun setLookInput(look: Float) {
        _engine?.lookInput = look
    }

    fun strike() {
        _engine?.strike()
        publish(force = true)
    }

    fun usePortal() {
        _engine?.usePortal()
        _engine?.let { logMapState("portal ->") }
        publish(force = true)
        persist()
    }

    /** Runtime diagnostic: what the map actually holds after generation. */
    private fun logMapState(tag: String) {
        val engine = _engine ?: return
        Log.i(
            "Hollowmarch",
            "$tag ${engine.map.title} — buildings=${engine.map.buildings.size} " +
                "residents=${engine.map.entities.count { it.resident }} " +
                "doors=${engine.map.portals.count { it.door }} " +
                "prompt=${engine.usePrompt()}"
        )
    }

    /** The USE hand: pocket, take up, search, or the way out — whatever stands nearest. */
    fun useButton(): Interact {
        val engine = _engine ?: return Interact.NONE
        val result = engine.interact()
        publish(force = true)
        persist()
        return result
    }

    fun rest(hours: Int) {
        _engine?.rest(hours)
        publish(force = true)
        persist()
    }

    fun restTillDawn() {
        _engine?.restTillDawn()
        publish(force = true)
        persist()
    }

    fun castWard() {
        _engine?.castWard()
        publish(force = true)
    }

    fun castMend() {
        _engine?.castMend()
        publish(force = true)
    }

    fun relightTorch() {
        _engine?.relightTorch()
        publish(force = true)
    }

    fun offerAtTemple() {
        _engine?.offerAtTemple()
        publish(force = true)
        persist()
    }

    /** The offering the ground you stand on allows: temple silver, or a shrine coin. */
    fun offer() {
        val engine = _engine ?: return
        when {
            engine.canOffer() -> engine.offerAtTemple()
            engine.canOfferAtShrine() -> engine.offerAtShrine()
            else -> return
        }
        publish(force = true)
        persist()
    }

    fun bearings(): List<Bearing> = _engine?.bearings() ?: emptyList()

    fun revive() {
        _engine?.revive()
        publish(force = true)
        persist()
    }

    fun toggleCrouch() {
        _engine?.toggleCrouch()
        publish(force = true)
    }

    fun toggleBlock() {
        _engine?.toggleBlock()
        publish(force = true)
    }

    fun pickpocket() {
        _engine?.pickpocket()
        publish(force = true)
        persist()
    }

    fun useRemedy() {
        _engine?.useRemedy()
        publish(force = true)
        persist()
    }

    fun dropItem(item: Item) {
        _engine?.dropItem(item)
        publish(force = true)
        persist()
    }

    /** Don a thing from the satchel; what it displaced returns to the load. */
    fun equipItem(item: Item, hand: Hand? = null) {
        _engine?.equipFromSatchel(item, hand)
        publish(force = true)
        persist()
    }

    /** Strip a worn thing back into the satchel. */
    fun unequipSlot(slot: WearSlot) {
        _engine?.unequipFromEquipment(slot)
        publish(force = true)
        persist()
    }

    /** Strip one worn thing from a corpse straight into the satchel. */
    fun takeEquipped(entity: Entity, slot: WearSlot) {
        _engine?.takeEquipped(entity, slot)
        publish(force = true)
        persist()
    }

    fun takeLootItem(entity: Entity, item: Item) {
        _engine?.takeItem(entity, item)
        publish(force = true)
        persist()
    }

    fun takeAllLoot(entity: Entity) {
        _engine?.takeAll(entity)
        publish(force = true)
        persist()
    }

    fun rumors(): List<Rumor> = _engine?.rumors ?: _world.rumors

    fun deeds(): List<String> = _engine?.deeds ?: emptyList()

    fun persist() {
        _engine?.let { SaveStore.save(it.toSaveSlot()) }
        _title.value = buildTitleState()
    }

    private fun publish(force: Boolean) {
        val engine = _engine ?: return
        val lines = engine.log.takeLast(2).map { line ->
            FadingLine(line.text, (1f - (line.age - 3.5f) / 2.5f).coerceIn(0f, 1f))
        }.filter { it.alpha > 0.02f }

        _hud.value = HudState(
            locationTitle = engine.map.title,
            heading = engine.heading,
            depthLabel = when {
                engine.onOverland ->
                    "${engine.terrainLabel()} · ${engine.skyWord()} · " +
                        "${engine.hour}:${engine.minute.toString().padStart(2, '0')}"
                engine.outdoor ->
                    "${engine.skyWord()} · ${engine.hour}:${engine.minute.toString().padStart(2, '0')}"
                else -> "${engine.depth}·${(engine.torch * 100).roundToInt()}"
            },
            vitality = engine.vitality,
            maxVitality = engine.maxVitality,
            fatigue = engine.fatigue,
            maxFatigue = engine.maxFatigue,
            magicka = engine.magicka,
            maxMagicka = engine.maxMagicka,
            torch = engine.torch,
            outdoor = engine.outdoor,
            lines = lines,
            usePrompt = engine.usePrompt(),
            day = engine.day,
            clock = "${engine.hour}:${engine.minute.toString().padStart(2, '0')}",
            timeOfDay = engine.timeOfDayLabel,
            brass = engine.brass,
            kills = engine.kills,
            explored = (engine.map.exploredFraction() * 100).roundToInt(),
            dead = engine.dead,
            warded = engine.wardTimer > 0f,
            level = engine.growth.level,
            className = engine.klass?.name ?: "",
            crouched = engine.crouched,
            blocking = engine.blocking,
            detection = when (engine.detectionState()) {
                DetectionState.SEEN -> "seen"
                DetectionState.SEARCHING -> "searching"
                DetectionState.HIDDEN -> "hidden"
                DetectionState.NONE -> ""
            },
            load = engine.inventory.weight(),
            capacity = engine.carryCapacity(),
            satchelCount = engine.inventory.all.size,
            rangedLine = engine.rangedHud() ?: ""
        )
        if (force) persistLight()
    }

    private var persistCounter = 0

    private fun persistLight() {
        persistCounter++
        if (persistCounter % 6 == 0) persist()
    }
}
