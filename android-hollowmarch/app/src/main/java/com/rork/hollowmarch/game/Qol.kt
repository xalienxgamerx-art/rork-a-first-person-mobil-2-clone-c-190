package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.ChronicleEvent
import com.rork.hollowmarch.world.EventKind

/**
 * Small quality-of-life arithmetic, kept pure and honest: the satchel's order,
 * the wait till dawn, the search fields, and the chronicle's kind of year.
 */

/** How the satchel's ledger is laid out. */
internal enum class SatchelSort(val label: String) {
    CARRIED("as carried"),
    LIGHT("lightest"),
    WORTH("worth most"),
    FINE("finest")
}

/** The satchel laid out in the chosen order; [SatchelSort.CARRIED] keeps the pack's own. */
internal fun sortSatchel(items: List<Item>, order: SatchelSort): List<Item> = when (order) {
    SatchelSort.CARRIED -> items
    SatchelSort.LIGHT -> items.sortedBy { it.weight() }
    SatchelSort.WORTH -> items.sortedByDescending { it.value() }
    // the makes run shoddy to legendary down the enum, so finest first is a plain reverse
    SatchelSort.FINE -> items.sortedByDescending { it.quality.ordinal }
}

/** Whole hours until first light (the sixth hour); at dawn itself, the whole day. */
internal fun hoursTillDawn(hour: Int, minute: Int): Int {
    val till = ((6 * 60 - (hour * 60 + minute)) + 1440) % 1440
    val hours = (till + 59) / 60
    return if (hours == 0) 24 else hours
}

/** The clock's arcs, in in-game minutes one real second burns: twenty real minutes
 *  of daylight, fifteen of night. Dawn stands at the sixth hour, dusk at the eighteenth. */
internal const val DAY_CLOCK_RATE = 720f / (20f * 60f)
internal const val NIGHT_CLOCK_RATE = 720f / (15f * 60f)

/** In-game minutes one real second burns, by the arc the sun stands in. */
internal fun clockStep(timeOfDay: Float, step: Float): Float =
    step * if (timeOfDay >= 0.25f && timeOfDay < 0.75f) DAY_CLOCK_RATE else NIGHT_CLOCK_RATE

/** A field that forgives: blank queries match everything, the rest by a loose ear. */
internal fun searchMatches(text: String, query: String): Boolean =
    query.isBlank() || text.contains(query.trim(), ignoreCase = true)

/** The kinds of year the chronicle can be read by. */
internal enum class ChronicleFilter(val label: String, val kinds: Set<EventKind>) {
    ALL("every year", emptySet()),
    BLOOD(
        "wars & blood",
        setOf(
            EventKind.WAR, EventKind.BATTLE, EventKind.REBELLION, EventKind.PLOT,
            EventKind.DESTRUCTION, EventKind.RUIN, EventKind.BEAST
        )
    ),
    OMENS("sky omens", setOf(EventKind.COMET, EventKind.ECLIPSE, EventKind.MOONWONDER)),
    GROWTH(
        "founding & works",
        setOf(
            EventKind.FOUNDING, EventKind.GROWTH, EventKind.CHARTER, EventKind.GUILDHALL,
            EventKind.MARRIAGE, EventKind.SUCCESSION, EventKind.CONSECRATION, EventKind.CATACOMB,
            EventKind.ARTIFACT, EventKind.CLAIM, EventKind.TAVERN
        )
    ),
    FAITH(
        "gods & word",
        setOf(EventKind.PROPHECY, EventKind.SCHISM, EventKind.PACT, EventKind.TREATY, EventKind.SEALING)
    ),
    WANT(
        "hard years",
        setOf(EventKind.PLAGUE, EventKind.FAMINE, EventKind.FLOOD, EventKind.MIGRATION, EventKind.DEATH)
    )
}

/** True when an entry belongs on the page: the right kind of year, and the right words. */
internal fun chronicleMatches(event: ChronicleEvent, query: String, filter: ChronicleFilter): Boolean =
    (filter == ChronicleFilter.ALL || event.kind in filter.kinds) &&
        searchMatches(event.text + " " + event.subject, query)

/** The chronicler's mark for a pin you have stood on: how long it has been. */
internal fun walkedAgoLabel(daysAgo: Int): String = when {
    daysAgo <= 0 -> "walked today"
    daysAgo == 1 -> "walked yesterday"
    else -> "walked $daysAgo days since"
}
