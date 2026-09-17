package com.rork.hollowmarch.game

import android.content.Context
import android.content.SharedPreferences

/** A single persisted expedition: which world, where in it, and in what condition. */
data class SaveSlot(
    val seed: Long,
    val day: Int,
    val minutes: Float,
    val outdoor: Boolean,
    val depth: Int,
    val x: Float,
    val y: Float,
    val angle: Float,
    val vitality: Int,
    val fatigue: Int,
    val magicka: Int,
    val torch: Float,
    val kills: Int,
    val brass: Int,
    val siteId: Int,
    val siteName: String,
    val deeds: List<String>,
    val reputation: String = "",
    val stats: String = "",
    val classKey: String = "",
    val growth: String = "",
    val proficiencies: String = "",
    val masteries: String = "",
    val nextUid: Int = 0,
    val inventory: String = "",
    val equipment: String = "",
    val dropped: String = "",
    val looted: String = "",
    val onOverland: Boolean = false,
    val settlements: String = "",
    val visited: String = "",
    val rumors: String = "",
    /**
     * The province's persistent memory — deaths by stable id, emptied
     * containers, souls and where they stand, discoveries, faction pressure,
     * the world clock the simulation reached, and the change ledger. Blank in
     * saves written before Persistent World v1; those fall back to [looted].
     */
    val worldState: String = "",
    /** The generation contract the save was written against. */
    val worldVersion: Int = WORLD_STATE_VERSION
)

object SaveStore {
    private const val PREFS = "hollowmarch_save"
    private const val KEY_SEED = "seed"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_OUTDOOR = "outdoor"
    private const val KEY_DEPTH = "depth"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_ANGLE = "angle"
    private const val KEY_VIT = "vit"
    private const val KEY_FAT = "fat"
    private const val KEY_MAG = "mag"
    private const val KEY_TORCH = "torch"
    private const val KEY_KILLS = "kills"
    private const val KEY_BRASS = "brass"
    private const val KEY_SITE_ID = "site_id"
    private const val KEY_SITE = "site"
    private const val KEY_DEEDS = "deeds"
    private const val KEY_REPUTATION = "reputation"
    private const val KEY_STATS = "stats"
    private const val KEY_CLASS = "class"
    private const val KEY_GROWTH = "growth"
    private const val KEY_PROFICIENCIES = "proficiencies"
    private const val KEY_MASTERIES = "masteries"
    private const val KEY_NEXT_UID = "next_uid"
    private const val KEY_INVENTORY = "inventory"
    private const val KEY_EQUIPMENT = "equipment"
    private const val KEY_DROPPED = "dropped"
    private const val KEY_LOOTED = "looted"
    private const val KEY_ON_OVERLAND = "on_overland"
    private const val KEY_SETTLEMENTS = "settlements"
    private const val KEY_VISITED = "visited"
    private const val KEY_RUMORS = "rumors"
    private const val KEY_WORLD_STATE = "world_state"
    private const val KEY_WORLD_VERSION = "world_version"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun hasSave(): Boolean = prefs?.contains(KEY_SEED) == true

    fun load(): SaveSlot? {
        val p = prefs ?: return null
        if (!p.contains(KEY_SEED)) return null
        val minutes = p.getFloat(KEY_MINUTES, 0f)
        return SaveSlot(
            seed = p.getLong(KEY_SEED, 0L),
            day = (minutes / 1440f).toInt() + 1,
            minutes = minutes,
            outdoor = p.getBoolean(KEY_OUTDOOR, false),
            depth = p.getInt(KEY_DEPTH, 3),
            x = p.getFloat(KEY_X, 2f),
            y = p.getFloat(KEY_Y, 2f),
            angle = p.getFloat(KEY_ANGLE, 0f),
            vitality = p.getInt(KEY_VIT, 61),
            fatigue = p.getInt(KEY_FAT, 40),
            magicka = p.getInt(KEY_MAG, 30),
            torch = p.getFloat(KEY_TORCH, 1f),
            kills = p.getInt(KEY_KILLS, 0),
            brass = p.getInt(KEY_BRASS, 0),
            siteId = p.getInt(KEY_SITE_ID, -1),
            siteName = p.getString(KEY_SITE, "") ?: "",
            deeds = p.getString(KEY_DEEDS, "")?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList(),
            reputation = p.getString(KEY_REPUTATION, "") ?: "",
            stats = p.getString(KEY_STATS, "") ?: "",
            classKey = p.getString(KEY_CLASS, "") ?: "",
            growth = p.getString(KEY_GROWTH, "") ?: "",
            proficiencies = p.getString(KEY_PROFICIENCIES, "") ?: "",
            masteries = p.getString(KEY_MASTERIES, "") ?: "",
            nextUid = p.getInt(KEY_NEXT_UID, 0),
            inventory = p.getString(KEY_INVENTORY, "") ?: "",
            equipment = p.getString(KEY_EQUIPMENT, "") ?: "",
            dropped = p.getString(KEY_DROPPED, "") ?: "",
            looted = p.getString(KEY_LOOTED, "") ?: "",
            onOverland = p.getBoolean(KEY_ON_OVERLAND, false),
            settlements = p.getString(KEY_SETTLEMENTS, "") ?: "",
            visited = p.getString(KEY_VISITED, "") ?: "",
            rumors = p.getString(KEY_RUMORS, "") ?: "",
            worldState = p.getString(KEY_WORLD_STATE, "") ?: "",
            worldVersion = p.getInt(KEY_WORLD_VERSION, 0)
        )
    }

    fun save(slot: SaveSlot) {
        prefs?.edit()?.apply {
            putLong(KEY_SEED, slot.seed)
            putFloat(KEY_MINUTES, slot.minutes)
            putBoolean(KEY_OUTDOOR, slot.outdoor)
            putInt(KEY_DEPTH, slot.depth)
            putFloat(KEY_X, slot.x)
            putFloat(KEY_Y, slot.y)
            putFloat(KEY_ANGLE, slot.angle)
            putInt(KEY_VIT, slot.vitality)
            putInt(KEY_FAT, slot.fatigue)
            putInt(KEY_MAG, slot.magicka)
            putFloat(KEY_TORCH, slot.torch)
            putInt(KEY_KILLS, slot.kills)
            putInt(KEY_BRASS, slot.brass)
            putInt(KEY_SITE_ID, slot.siteId)
            putString(KEY_SITE, slot.siteName)
            putString(KEY_DEEDS, slot.deeds.takeLast(12).joinToString("\u001F"))
            putString(KEY_REPUTATION, slot.reputation)
            putString(KEY_STATS, slot.stats)
            putString(KEY_CLASS, slot.classKey)
            putString(KEY_GROWTH, slot.growth)
            putString(KEY_PROFICIENCIES, slot.proficiencies)
            putString(KEY_MASTERIES, slot.masteries)
            putInt(KEY_NEXT_UID, slot.nextUid)
            putString(KEY_INVENTORY, slot.inventory)
            putString(KEY_EQUIPMENT, slot.equipment)
            putString(KEY_DROPPED, slot.dropped)
            putString(KEY_LOOTED, slot.looted)
            putBoolean(KEY_ON_OVERLAND, slot.onOverland)
            putString(KEY_SETTLEMENTS, slot.settlements)
            putString(KEY_VISITED, slot.visited)
            putString(KEY_RUMORS, slot.rumors)
            putString(KEY_WORLD_STATE, slot.worldState)
            putInt(KEY_WORLD_VERSION, slot.worldVersion)
        }?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
