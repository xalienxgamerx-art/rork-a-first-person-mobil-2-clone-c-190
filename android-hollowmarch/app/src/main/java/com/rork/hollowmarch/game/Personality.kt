package com.rork.hollowmarch.game

import kotlin.random.Random

/**
 * NPC personality and traits — the heart under the trade.
 *
 * The role says what a soul does; this says who does it. Three parts:
 *
 *  - [Trait], the catalog: the marks of character the province knows, grouped
 *    by family, each with its base rarity, its open contradictions, and the
 *    behavioral values it leans on.
 *  - [Personality], the heart itself: four behavioral values (aggression,
 *    confidence, responsibility, energy, each 0–100) and a small handful of
 *    traits. It never replaces the role or the schedule — it bends the free
 *    hours, the breaks, the waking bell and the evening cup toward the soul's
 *    own temper, and sets how it carries itself in a fight.
 *  - [PersonalityBook], the dealing: values and traits are drawn once from the
 *    world's seed, the soul's own stable key and its trade, and never again.
 *    The heart rides the save in the persistent record, so a soul is the same
 *    soul after loading as it was before.
 *
 * The four values answer four different questions, and each moves only its
 * own levers: aggression decides how readily a fight starts and how far a
 * chase is worth; confidence decides when a hurt soul breaks off and runs;
 * responsibility decides how strongly duty holds against temptation; energy
 * decides how much of the day is spent doing anything at all.
 */

/** The family a trait belongs to — used for flavor, weights and role bias. */
enum class TraitCategory { SOCIAL, WORK, EMOTIONAL, VICE, VALUES, INTERESTS, NEGATIVE, RELATIONSHIP }

private fun ant(vararg names: String): Set<String> = names.toSet()

/**
 * One mark of character. [antonyms] names the traits it will not share a soul
 * with (read in both directions — the dealing checks each side). [weight] is
 * the base rarity; aggr/conf/resp/enrg are lean coefficients: a trait's draw
 * weight is multiplied by (1 + coef * value / 100), so Hot-Tempered rises with
 * aggression and Calm falls with it, without either ever being impossible.
 *
 * A few marks the chronicle lists twice (Family-Oriented, Protective, Jealous)
 * are one trait here — the same heart, wherever it shows.
 */
enum class Trait(
    val category: TraitCategory,
    val antonyms: Set<String> = emptySet(),
    val weight: Float = 1f,
    val aggr: Float = 0f,
    val conf: Float = 0f,
    val resp: Float = 0f,
    val enrg: Float = 0f
) {
    // ---------------------------------------------------------------- social
    Friendly(TraitCategory.SOCIAL, ant("Rude", "Hostile", "Aloof", "Cruel"), 1.6f),
    Outgoing(TraitCategory.SOCIAL, ant("Introverted", "Shy", "Quiet"), 1.5f),
    Shy(TraitCategory.SOCIAL, ant("Outgoing", "Bold", "Charismatic"), 1.3f),
    Introverted(TraitCategory.SOCIAL, ant("Outgoing", "Socialite"), 1.3f),
    Charismatic(TraitCategory.SOCIAL, ant("Rude"), 0.8f, conf = 0.3f),
    Talkative(TraitCategory.SOCIAL, ant("Quiet"), 1.4f),
    Quiet(TraitCategory.SOCIAL, ant("Talkative", "Outgoing"), 1.3f),
    Rude(TraitCategory.SOCIAL, ant("Polite", "Friendly"), 0.8f, aggr = 0.3f),
    Polite(TraitCategory.SOCIAL, ant("Rude"), 1.3f, resp = 0.2f),
    Generous(TraitCategory.SOCIAL, ant("Selfish", "Greedy"), 1.2f),
    Selfish(TraitCategory.SOCIAL, ant("Generous", "Compassionate"), 1.2f),
    Compassionate(TraitCategory.SOCIAL, ant("Cruel", "Callous"), 1.2f),
    Cruel(TraitCategory.SOCIAL, ant("Compassionate", "Empathetic"), 0.5f, aggr = 0.6f),
    Empathetic(TraitCategory.SOCIAL, ant("Callous"), 1.1f),
    Aloof(TraitCategory.SOCIAL, ant("Friendly", "Outgoing"), 1f),
    Trusting(TraitCategory.SOCIAL, ant("Suspicious", "Paranoid"), 1.2f),
    Suspicious(TraitCategory.SOCIAL, ant("Trusting", "Gullible"), 1.2f),
    Gullible(TraitCategory.SOCIAL, ant("Suspicious", "Deceptive"), 0.9f),
    Deceptive(TraitCategory.SOCIAL, ant("Honest"), 0.9f, resp = -0.4f),
    Manipulative(TraitCategory.SOCIAL, ant("Honest", "Gullible"), 0.6f, resp = -0.3f),
    Flirtatious(TraitCategory.SOCIAL, ant("Chaste", "Reserved"), 1f),
    Lustful(TraitCategory.SOCIAL, ant("Chaste"), 0.8f),
    Jealous(TraitCategory.SOCIAL, weight = 1f),
    Protective(TraitCategory.SOCIAL, weight = 1.1f, aggr = 0.2f),
    Secretive(TraitCategory.SOCIAL, ant("Talkative"), 1f),

    // ------------------------------------------------------- work & lifestyle
    Hardworking(TraitCategory.WORK, ant("Lazy"), 1.6f, resp = 0.3f, enrg = 0.3f),
    Lazy(TraitCategory.WORK, ant("Hardworking", "Workaholic"), 1.3f, resp = -0.3f, enrg = -0.3f),
    Ambitious(TraitCategory.WORK, ant("Unmotivated"), 1.2f, enrg = 0.2f),
    Unmotivated(TraitCategory.WORK, ant("Ambitious"), 1f, enrg = -0.2f),
    Serious(TraitCategory.WORK, ant("Carefree"), 1.2f),
    Carefree(TraitCategory.WORK, ant("Serious", "Anxious"), 1.2f),
    Disciplined(TraitCategory.WORK, ant("Impulsive", "Messy"), 1.1f, resp = 0.3f),
    Impulsive(TraitCategory.WORK, ant("Disciplined", "Patient", "Cautious"), 1.1f, aggr = 0.2f),
    Patient(TraitCategory.WORK, ant("Impatient"), 1.2f),
    Impatient(TraitCategory.WORK, ant("Patient"), 1.1f),
    Reliable(TraitCategory.WORK, ant("Unreliable"), 1.3f, resp = 0.3f),
    Unreliable(TraitCategory.WORK, ant("Reliable"), 0.9f, resp = -0.3f),
    Organized(TraitCategory.WORK, ant("Messy"), 1f),
    Messy(TraitCategory.WORK, ant("Organized"), 1f),
    Frugal(TraitCategory.WORK, ant("Wasteful", "Spendthrift"), 1.1f),
    Wasteful(TraitCategory.WORK, ant("Frugal"), 0.9f),
    Greedy(TraitCategory.WORK, ant("Generous", "Ascetic"), 1.2f),
    Perfectionist(TraitCategory.WORK, weight = 0.9f, resp = 0.2f),
    Adventurous(TraitCategory.WORK, ant("Cautious"), 1f, enrg = 0.3f),
    Traditional(TraitCategory.WORK, ant("Progressive"), 1.1f),
    Independent(TraitCategory.WORK, ant("Dependent"), 1.1f),
    Dependent(TraitCategory.WORK, ant("Independent"), 0.9f),

    // -------------------------------------------------------------- emotional
    Cheerful(TraitCategory.EMOTIONAL, ant("Melancholic", "Bitter"), 1.3f),
    Melancholic(TraitCategory.EMOTIONAL, ant("Cheerful", "Optimistic"), 1f),
    Optimistic(TraitCategory.EMOTIONAL, ant("Pessimistic"), 1.2f, conf = 0.2f),
    Pessimistic(TraitCategory.EMOTIONAL, ant("Optimistic"), 1.1f),
    Calm(TraitCategory.EMOTIONAL, ant("HotTempered", "Nervous"), 1.2f, aggr = -0.8f),
    Nervous(TraitCategory.EMOTIONAL, ant("Calm", "Bold"), 1f, conf = -0.5f),
    Anxious(TraitCategory.EMOTIONAL, ant("Calm", "Carefree"), 1f, conf = -0.5f),
    Confident(TraitCategory.EMOTIONAL, ant("Insecure"), 1.2f, conf = 0.7f),
    Insecure(TraitCategory.EMOTIONAL, ant("Confident"), 1f, conf = -0.6f),
    Stoic(TraitCategory.EMOTIONAL, ant("Emotional"), 1.1f),
    Emotional(TraitCategory.EMOTIONAL, ant("Stoic"), 1.1f),
    HotTempered(TraitCategory.EMOTIONAL, ant("Calm", "EvenTempered"), 1f, aggr = 1.1f),
    EvenTempered(TraitCategory.EMOTIONAL, ant("HotTempered"), 1.1f, aggr = -0.6f),
    Stubborn(TraitCategory.EMOTIONAL, ant("Flexible"), 1.1f, conf = 0.2f),
    Flexible(TraitCategory.EMOTIONAL, ant("Stubborn"), 1f),
    Fearful(TraitCategory.EMOTIONAL, ant("Bold", "Reckless"), 1f, conf = -0.9f),
    Reckless(TraitCategory.EMOTIONAL, ant("Cautious", "Fearful"), 0.8f, aggr = 0.5f, conf = 0.5f),
    Cautious(TraitCategory.EMOTIONAL, ant("Reckless", "Bold"), 1.2f),
    Paranoid(TraitCategory.EMOTIONAL, ant("Trusting"), 0.7f),
    Bold(TraitCategory.EMOTIONAL, ant("Shy", "Fearful", "Cautious"), 1.1f, aggr = 0.3f, conf = 0.8f),

    // ---------------------------------------------------------- vice & habit
    Alcoholic(TraitCategory.VICE, ant("Ascetic"), 1f),
    Smoker(TraitCategory.VICE, weight = 0.7f),
    Gambler(TraitCategory.VICE, weight = 0.8f),
    Gluttonous(TraitCategory.VICE, ant("Ascetic"), 0.9f),
    Drunkard(TraitCategory.VICE, ant("Ascetic"), 0.7f),
    Workaholic(TraitCategory.VICE, ant("Lazy", "Procrastinator"), 0.8f, resp = 0.3f, enrg = 0.3f),
    Procrastinator(TraitCategory.VICE, ant("Workaholic", "Disciplined"), 0.9f, resp = -0.3f),
    NightOwl(TraitCategory.VICE, ant("EarlyRiser"), 1f),
    EarlyRiser(TraitCategory.VICE, ant("NightOwl"), 1.1f, enrg = 0.2f),
    Spendthrift(TraitCategory.VICE, ant("Frugal"), 0.9f),
    Hoarder(TraitCategory.VICE, weight = 0.7f),
    Kleptomaniac(TraitCategory.VICE, ant("Honest"), 0.4f, resp = -0.8f),
    Debauched(TraitCategory.VICE, ant("Chaste", "Ascetic"), 0.6f),
    Ascetic(TraitCategory.VICE, ant("Greedy", "Debauched", "Gluttonous"), 0.6f),

    // ----------------------------------------------------------------- values
    Honest(TraitCategory.VALUES, ant("Dishonest", "Deceptive", "Untrustworthy"), 1.5f, resp = 0.8f),
    Dishonest(TraitCategory.VALUES, ant("Honest", "HonorBound"), 1f, resp = -0.8f),
    LawAbiding(TraitCategory.VALUES, ant("Lawless"), 1.3f, resp = 0.9f),
    Lawless(TraitCategory.VALUES, ant("LawAbiding", "HonorBound"), 0.8f, resp = -0.9f),
    Religious(TraitCategory.VALUES, ant("Irreligious"), 1.4f),
    Irreligious(TraitCategory.VALUES, ant("Religious"), 0.7f),
    Superstitious(TraitCategory.VALUES, ant("Rational"), 1.1f),
    Rational(TraitCategory.VALUES, ant("Superstitious"), 1f),
    Traditionalist(TraitCategory.VALUES, ant("Progressive"), 1.2f),
    Progressive(TraitCategory.VALUES, ant("Traditionalist", "Traditional"), 0.8f),
    HonorBound(TraitCategory.VALUES, ant("Untrustworthy", "Opportunistic"), 1f, resp = 0.8f),
    Opportunistic(TraitCategory.VALUES, ant("HonorBound"), 1f, resp = -0.3f),
    Patriotic(TraitCategory.VALUES, weight = 0.9f, resp = 0.3f),
    Individualistic(TraitCategory.VALUES, ant("CommunityMinded", "Dependent"), 1f),
    FamilyOriented(TraitCategory.VALUES, weight = 1.3f),
    CommunityMinded(TraitCategory.VALUES, ant("Individualistic"), 1.1f),
    StatusSeeking(TraitCategory.VALUES, weight = 0.9f),
    Materialistic(TraitCategory.VALUES, ant("Ascetic"), 1.1f),

    // -------------------------------------------------------------- interests
    Bookish(TraitCategory.INTERESTS, weight = 1.2f),
    Artistic(TraitCategory.INTERESTS, weight = 1f),
    Musical(TraitCategory.INTERESTS, weight = 1f),
    Scholarly(TraitCategory.INTERESTS, weight = 1f),
    CraftMinded(TraitCategory.INTERESTS, weight = 1.1f),
    NatureLoving(TraitCategory.INTERESTS, weight = 1.1f),
    AnimalLover(TraitCategory.INTERESTS, weight = 1.1f),
    FoodLover(TraitCategory.INTERESTS, weight = 1.2f),
    Traveler(TraitCategory.INTERESTS, weight = 1f, enrg = 0.3f),
    Socialite(TraitCategory.INTERESTS, ant("Introverted"), 0.9f, enrg = 0.2f),
    Sportsman(TraitCategory.INTERESTS, weight = 0.9f, enrg = 0.3f),
    Hunter(TraitCategory.INTERESTS, weight = 0.9f),
    Fisherman(TraitCategory.INTERESTS, weight = 0.9f),
    Gardener(TraitCategory.INTERESTS, weight = 1f),
    Collector(TraitCategory.INTERESTS, weight = 0.8f),
    Antiquarian(TraitCategory.INTERESTS, weight = 0.6f),

    // ---------------------------------------------------- negative / antisocial
    Vindictive(TraitCategory.NEGATIVE, weight = 0.8f, aggr = 0.4f),
    Vengeful(TraitCategory.NEGATIVE, weight = 0.7f, aggr = 0.5f),
    Bullying(TraitCategory.NEGATIVE, ant("Compassionate"), 0.6f, aggr = 0.8f, conf = 0.3f),
    Cowardly(TraitCategory.NEGATIVE, ant("Bold", "Reckless"), 0.8f, conf = -1.2f),
    Hostile(TraitCategory.NEGATIVE, ant("Friendly", "Polite"), 0.7f, aggr = 0.7f),
    Prejudiced(TraitCategory.NEGATIVE, weight = 0.5f),
    Envious(TraitCategory.NEGATIVE, weight = 0.8f),
    Narcissistic(TraitCategory.NEGATIVE, weight = 0.4f, conf = 0.5f),
    Arrogant(TraitCategory.NEGATIVE, weight = 0.7f, conf = 0.5f),
    Condescending(TraitCategory.NEGATIVE, weight = 0.5f, conf = 0.3f),
    Violent(TraitCategory.NEGATIVE, weight = 0.4f, aggr = 1.4f),
    Callous(TraitCategory.NEGATIVE, ant("Compassionate", "Empathetic"), 0.8f),
    Untrustworthy(TraitCategory.NEGATIVE, ant("Honest", "HonorBound", "Loyal"), 0.8f, resp = -0.7f),
    Resentful(TraitCategory.NEGATIVE, weight = 0.8f),
    Bitter(TraitCategory.NEGATIVE, ant("Cheerful"), 0.8f),

    // ------------------------------------------------------------ relationship
    Romantic(TraitCategory.RELATIONSHIP, weight = 1.1f),
    Chaste(TraitCategory.RELATIONSHIP, ant("Promiscuous", "Lustful", "Debauched"), 1.1f),
    Loyal(TraitCategory.RELATIONSHIP, ant("Unfaithful", "Untrustworthy"), 1.4f, resp = 0.4f),
    Unfaithful(TraitCategory.RELATIONSHIP, ant("Loyal"), 0.7f, resp = -0.3f),
    ChildLoving(TraitCategory.RELATIONSHIP, weight = 0.9f),
    Possessive(TraitCategory.RELATIONSHIP, weight = 0.7f),
    CommitmentAverse(TraitCategory.RELATIONSHIP, ant("MarriageMinded", "Loyal"), 0.7f),
    MarriageMinded(TraitCategory.RELATIONSHIP, ant("CommitmentAverse"), 1f),
    Promiscuous(TraitCategory.RELATIONSHIP, ant("Chaste"), 0.6f),
    Affectionate(TraitCategory.RELATIONSHIP, ant("Reserved", "Aloof"), 1.2f),
    Reserved(TraitCategory.RELATIONSHIP, ant("Affectionate", "Outgoing"), 1.1f);

    /** True when two traits will not share a soul. Reads in both directions. */
    fun conflictsWith(other: Trait): Boolean =
        other.name in antonyms || name in other.antonyms

    companion object {
        private val byNameMap: Map<String, Trait> by lazy { entries.associateBy { it.name } }

        /** The trait a persisted name stands for, or null when it names nothing. */
        fun byName(name: String): Trait? = byNameMap[name]
    }
}

/**
 * A soul's heart: four behavioral values on one range (0–100) and the small
 * handful of traits the dealing gave it. The values are independent axes —
 * each method below reads only the axis (and traits) that answer its question.
 */
data class Personality(
    val aggression: Int,
    val confidence: Int,
    val responsibility: Int,
    val energy: Int,
    val traits: List<Trait> = emptyList()
) {
    fun has(trait: Trait): Boolean = trait in traits
    fun hasAny(vararg candidates: Trait): Boolean = candidates.any { it in traits }

    // ------------------------------------------------- the day bends, the trade stands

    /**
     * Hours added to or taken from the soul's waking bell: early risers and the
     * tireless rise before their trade, night owls and the idle after it.
     */
    fun wakeAdjust(): Float {
        var v = 0f
        if (has(Trait.EarlyRiser)) v -= 0.9f
        if (has(Trait.NightOwl)) v += 1.1f
        if (has(Trait.Hardworking) || has(Trait.Workaholic)) v -= 0.4f
        if (has(Trait.Lazy) || has(Trait.Unmotivated)) v += 0.6f
        if (has(Trait.Disciplined)) v -= 0.3f
        v += (50f - energy) / 100f * 0.8f
        return v
    }

    /** How long the trade's own work stretches: the diligent longer, the idle shorter. */
    fun workMultiplier(): Float {
        var v = 1f + (energy - 50f) / 100f * 0.2f
        if (has(Trait.Hardworking)) v += 0.22f
        if (has(Trait.Workaholic)) v += 0.3f
        if (has(Trait.Ambitious)) v += 0.12f
        if (has(Trait.Disciplined)) v += 0.1f
        if (has(Trait.Perfectionist)) v += 0.12f
        if (has(Trait.Lazy)) v -= 0.25f
        if (has(Trait.Unmotivated)) v -= 0.15f
        if (has(Trait.Procrastinator)) v -= 0.12f
        return v.coerceIn(0.6f, 1.6f)
    }

    /** How long the well and the meal break stretches: the idle linger, the diligent don't. */
    fun breakMultiplier(): Float {
        var v = 1f + (50f - energy) / 100f * 0.4f
        if (has(Trait.Lazy)) v += 0.5f
        if (has(Trait.Carefree)) v += 0.3f
        if (has(Trait.Talkative)) v += 0.2f
        if (has(Trait.Alcoholic) || has(Trait.Drunkard)) v += 0.25f
        if (has(Trait.Gluttonous)) v += 0.3f
        if (has(Trait.Hardworking) || has(Trait.Workaholic)) v -= 0.3f
        if (has(Trait.Impatient)) v -= 0.15f
        return v.coerceIn(0.5f, 2.2f)
    }

    /**
     * What the evening leisure hour becomes: home by habit, unless the heart
     * pulls elsewhere — the drinker to the tavern, the devout to the temple.
     */
    fun eveningDuty(rng: Random): Duty {
        val options = listOf(
            Duty.HOME to pull(Duty.HOME, 19f, hasTrade = false) * 1.4f,
            Duty.TAVERN to pull(Duty.TAVERN, 19f, hasTrade = false),
            Duty.TEMPLE to pull(Duty.TEMPLE, 19f, hasTrade = false),
            Duty.WELL to pull(Duty.WELL, 19f, hasTrade = false) * 0.8f,
            Duty.MARKET to pull(Duty.MARKET, 19f, hasTrade = false) * 0.6f
        )
        return weightedPick(options, rng) ?: Duty.HOME
    }

    // ------------------------------------------------------------- decisions

    /**
     * Where a free hour goes. The scheduled block is the baseline and holds a
     * habit's advantage; every candidate destination is weighted by the heart
     * — traits, the four values, the hour and whether the soul has a trade —
     * and the strongest pull wins. The trade's own hours are never
     * second-guessed: a work block, a patrol, the fields all stand.
     */
    fun freeTimeDecision(hour: Float, scheduled: Duty, workDuty: Duty?, rng: Random): Duty {
        if (workDuty != null && scheduled == workDuty) return scheduled
        if (scheduled in WORK_LIKE) return scheduled
        var best = scheduled
        var bestWeight = pull(scheduled, hour, workDuty != null) * 1.2f + 1.2f
        for (duty in CANDIDATES) {
            if (duty == scheduled) continue
            val w = pull(duty, hour, workDuty != null) * (0.8f + rng.nextFloat() * 0.4f)
            if (w > bestWeight) {
                bestWeight = w
                best = duty
            }
        }
        return best
    }

    /** What the chronicler writes of a free hour spent where the heart chose. */
    fun freeTimeActivity(duty: Duty, role: String): String = when (duty) {
        Duty.WORK -> ROLES.workText(role) ?: "minding the work"
        Duty.TAVERN -> when {
            hasAny(Trait.Alcoholic, Trait.Drunkard) -> "deep in the cups at the tavern"
            has(Trait.Gambler) -> "at the dice in the tavern"
            else -> "keeping company at the tavern"
        }
        Duty.HOME -> when {
            has(Trait.FamilyOriented) -> "at home among the family"
            has(Trait.Bookish) -> "at home with a book"
            has(Trait.Lazy) -> "lounging at home"
            else -> "at leisure at home"
        }
        Duty.TEMPLE -> "at prayers"
        Duty.WELL -> "passing the time at the well"
        Duty.MARKET -> "browsing the market"
        Duty.ROAD -> when {
            has(Trait.Adventurous) || has(Trait.Traveler) -> "out walking the edges of the place"
            else -> "out for a walk"
        }
        else -> "about the day"
    }

    /**
     * The pull of one destination at one hour, from traits and values alone —
     * the same weight model behind free hours and evenings alike, so the
     * combinations speak for themselves: Greedy + Lawless weighs the shadow
     * side of the market higher than Greedy + Law-Abiding ever will.
     */
    private fun pull(duty: Duty, hour: Float, hasTrade: Boolean): Float {
        var v = when (duty) {
            Duty.HOME -> 2.5f
            Duty.WELL -> 1.8f
            Duty.TAVERN -> 0.6f
            Duty.TEMPLE -> 0.4f
            Duty.MARKET -> 0.8f
            Duty.WORK -> if (hasTrade && hour in 7f..18.5f) 0.5f else 0f
            Duty.ROAD -> if (hour in 7f..18.5f) 0.35f else 0f
            else -> 0.4f
        }
        if (v <= 0f) return 0f

        fun bump(vararg ts: Trait, by: Float) {
            if (ts.any { has(it) }) v += by
        }
        when (duty) {
            Duty.TAVERN -> {
                bump(Trait.Alcoholic, by = 9f)
                bump(Trait.Drunkard, by = 8f)
                bump(Trait.Socialite, by = 6f)
                bump(Trait.Gambler, by = 5f)
                bump(Trait.Outgoing, Trait.Debauched, by = 4f)
                bump(
                    Trait.Friendly, Trait.Talkative, Trait.Flirtatious, Trait.Musical,
                    Trait.Cheerful, Trait.Lustful, Trait.Promiscuous, Trait.Lawless, by = 2.5f
                )
                bump(Trait.Shy, by = -6f)
                bump(Trait.Introverted, Trait.Ascetic, by = -5f)
                bump(Trait.Quiet, Trait.Serious, by = -3f)
                bump(Trait.Suspicious, by = -2f)
                if (hour in 8f..17f && hasTrade && responsibility > 65f) v *= 0.55f
            }
            Duty.HOME -> {
                bump(Trait.FamilyOriented, by = 6f)
                bump(Trait.Introverted, Trait.Shy, by = 5f)
                bump(Trait.Lazy, by = 4f)
                bump(Trait.Quiet, Trait.Carefree, Trait.Paranoid, Trait.Aloof, by = 3f)
                bump(Trait.Bookish, Trait.Melancholic, Trait.Ascetic, Trait.Suspicious, by = 2f)
                if (energy < 35) v += 4f
                bump(Trait.Socialite, by = -4f)
                bump(Trait.Outgoing, Trait.Adventurous, by = -2f)
                if (energy > 75) v -= 2f
            }
            Duty.WELL -> {
                bump(Trait.Outgoing, by = 5f)
                bump(Trait.Friendly, Trait.Talkative, Trait.Socialite, by = 4f)
                bump(Trait.CommunityMinded, by = 3f)
                bump(Trait.Charismatic, by = 2f)
                bump(Trait.Shy, Trait.Introverted, Trait.Aloof, by = -5f)
                bump(Trait.Paranoid, by = -3f)
                bump(Trait.Suspicious, Trait.Secretive, by = -2f)
            }
            Duty.TEMPLE -> {
                bump(Trait.Religious, by = 9f)
                bump(Trait.Superstitious, by = 4f)
                bump(Trait.Traditionalist, Trait.Melancholic, Trait.Anxious, by = 2f)
                bump(Trait.Irreligious, by = -5f)
                bump(Trait.Rational, by = -2f)
            }
            Duty.MARKET -> {
                bump(Trait.Greedy, by = 5f)
                bump(Trait.Materialistic, by = 4f)
                bump(Trait.Collector, Trait.FoodLover, Trait.Frugal, Trait.StatusSeeking, by = 3f)
                bump(Trait.Opportunistic, by = 2f)
                bump(Trait.Ascetic, by = -3f)
                if (hour in 8f..17f && hasTrade && responsibility > 65f) v *= 0.85f
            }
            Duty.WORK -> {
                bump(Trait.Workaholic, by = 10f)
                bump(Trait.Hardworking, by = 8f)
                bump(Trait.Ambitious, Trait.Greedy, Trait.Perfectionist, Trait.Disciplined, by = 4f)
                bump(Trait.Serious, by = 3f)
                bump(Trait.Lazy, by = -8f)
                bump(Trait.Unmotivated, Trait.Procrastinator, by = -6f)
                bump(Trait.Carefree, by = -3f)
                v += (responsibility - 50f) / 12f
            }
            Duty.ROAD -> {
                bump(Trait.Adventurous, Trait.Traveler, by = 8f)
                bump(Trait.Sportsman, Trait.NatureLoving, Trait.Hunter, by = 3f)
                if (energy > 70) v += 2f
                if (energy < 35) v -= 3f
                bump(Trait.Cautious, Trait.Fearful, by = -4f)
                bump(Trait.Lazy, by = -2f)
                if (hour in 8f..17f && hasTrade && responsibility > 65f) v *= 0.6f
            }
            else -> Unit
        }
        // energy leans the active choices out and the quiet ones in
        return if (duty in ACTIVE_DUTIES) v * (0.6f + energy / 125f) else v
    }

    // ---------------------------------------------------------------- combat

    /**
     * The hurt fraction at which the soul breaks off and runs — from
     * confidence alone: the brave stand to the last, the fearful quit early.
     * Aggression has no say here, and confidence no say in the pursuit.
     */
    fun retreatHpFraction(): Float {
        var v = (100f - confidence) / 100f * 0.45f
        if (has(Trait.Cowardly)) v += 0.2f
        if (has(Trait.Fearful)) v += 0.15f
        if (hasAny(Trait.Anxious, Trait.Nervous)) v += 0.05f
        if (has(Trait.Cautious)) v += 0.05f
        if (has(Trait.Bold)) v -= 0.1f
        if (has(Trait.Reckless)) v -= 0.15f
        if (hasAny(Trait.Stoic, Trait.Confident)) v -= 0.05f
        return v.coerceIn(0f, 0.6f)
    }

    /** How far a chase is worth following: aggression's own lever. */
    fun pursueRadius(): Float {
        var v = 5f + aggression / 100f * 9f
        if (hasAny(Trait.Reckless, Trait.Vengeful, Trait.Vindictive)) v += 2f
        if (hasAny(Trait.Cautious, Trait.Cowardly)) v -= 2f
        return v.coerceIn(2.5f, 14f)
    }

    /** How fast the hand comes again: a hot temper strikes sooner. */
    fun attackCooldownScale(): Float = (1.25f - aggression / 400f).coerceIn(0.9f, 1.4f)

    // ------------------------------------------------------------ the record

    /** Packed for the save: the four values and the trait names, one field. */
    fun encode(): String = listOf(
        "$aggression", "$confidence", "$responsibility", "$energy",
        traits.joinToString("+") { it.name }
    ).joinToString("/")

    companion object {
        /** Duties that only ever name a trade's work; free hours never overwrite them. */
        private val WORK_LIKE = setOf(Duty.WORK, Duty.ROAD, Duty.GATE, Duty.FIELDS, Duty.WATER)

        /** Where a free hour may go instead of where the schedule put it. */
        private val CANDIDATES =
            listOf(Duty.HOME, Duty.WELL, Duty.TAVERN, Duty.TEMPLE, Duty.MARKET, Duty.WORK, Duty.ROAD)

        private val ACTIVE_DUTIES = setOf(Duty.WELL, Duty.MARKET, Duty.ROAD, Duty.WORK)

        /** Read a heart back from the save; null when the field names nothing. */
        fun decode(raw: String): Personality? {
            val f = raw.split("/")
            if (f.size < 5) return null
            val a = f[0].toIntOrNull() ?: return null
            val c = f[1].toIntOrNull() ?: return null
            val r = f[2].toIntOrNull() ?: return null
            val e = f[3].toIntOrNull() ?: return null
            val traits = f[4].split("+").mapNotNull { Trait.byName(it) }
            return Personality(
                a.coerceIn(0, 100), c.coerceIn(0, 100), r.coerceIn(0, 100), e.coerceIn(0, 100), traits
            )
        }

        private fun weightedPick(options: List<Pair<Duty, Float>>, rng: Random): Duty? {
            val usable = options.filter { it.second > 0f }
            if (usable.isEmpty()) return null
            var roll = rng.nextDouble() * usable.sumOf { it.second.toDouble() }
            var chosen = usable.last().first
            for ((duty, w) in usable) {
                roll -= w
                if (roll <= 0) {
                    chosen = duty
                    break
                }
            }
            return chosen
        }
    }
}

/**
 * The dealing of hearts: from the world's seed, the soul's own stable key and
 * its trade, once and forever. Deterministic — the same call draws the same
 * soul, and a loaded save never redraws what it already holds.
 */
object PersonalityBook {

    /** A soul's stable personality key: the world's seed and the soul's own id. */
    fun key(worldSeed: Long, id: String): Long =
        worldSeed * 8191L + id.hashCode().toLong() * 17L + 5L

    /**
     * Draw a soul. The trade leans the four values — a guard's blood runs
     * hotter than a priest's, a thief's responsibility sits low, a laborer's
     * energy high — and leans the trait pool the same way, without ever
     * deciding the matter outright.
     */
    fun deal(key: Long, role: String): Personality {
        val rng = Random(key * 6151L + 41L)
        val category = ROLES.byName(role)?.category ?: RoleCategory.COMMON
        val ranges = RANGES[category] ?: DEFAULT_RANGES
        val drawn = ranges.map { (base, span) -> (base + rng.nextInt(span + 1)).coerceIn(3, 97) }

        val count = when (rng.nextInt(20)) {
            in 0..3 -> 2
            in 4..10 -> 3
            in 11..16 -> 4
            else -> 5
        }
        val seeds = CATEGORY_SEEDS[category] ?: emptyMap()
        val picked = mutableListOf<Trait>()
        repeat(count) {
            val pool = Trait.entries
                .filter { t -> t !in picked && picked.none { t.conflictsWith(it) } }
                .map { t -> t to t.weight * (seeds[t] ?: 1f) * valueBias(t, drawn) }
            if (pool.isEmpty()) return@repeat
            var roll = rng.nextDouble() * pool.sumOf { it.second.toDouble() }
            var chosen = pool.last().first
            for ((t, w) in pool) {
                roll -= w
                if (roll <= 0) {
                    chosen = t
                    break
                }
            }
            picked += chosen
        }
        return Personality(drawn[0], drawn[1], drawn[2], drawn[3], picked)
    }

    /** The heart of one summoned to the watch or the warband: hot blood, steady nerve. */
    fun dealWatch(key: Long, warband: Boolean): Personality =
        deal(key, if (warband) "Bandit" else "Guard")

    /**
     * The heart of the wild: beasts carry the four values but no trade and no
     * traits — their temper is their hide. The flesh leans the draws: the hound
     * runs hot and tireless, the wraith does not know fear, vermin bolt, and
     * the horned prey runs rather than rows. The same key and the same flesh
     * draw the same temper, forever.
     */
    fun dealBeast(key: Long, name: String): Personality {
        val rng = Random(key * 6151L + 97L)
        val lower = name.lowercase()
        val ranges = TEMPER.firstOrNull { (marks, _) -> marks.any { lower.contains(it) } }?.second ?: WILD_DEFAULT
        val drawn = ranges.map { (base, span) -> (base + rng.nextInt(span + 1)).coerceIn(3, 97) }
        return Personality(drawn[0], drawn[1], drawn[2], drawn[3], emptyList())
    }

    /** The four draws per craft: aggression, confidence, responsibility, energy. */
    private val RANGES: Map<RoleCategory, List<Pair<Int, Int>>> = mapOf(
        RoleCategory.MILITARY to listOf(55 to 36, 55 to 36, 55 to 30, 45 to 36),
        RoleCategory.SECURITY to listOf(50 to 36, 55 to 36, 50 to 30, 50 to 36),
        RoleCategory.UNDERWORLD to listOf(40 to 40, 35 to 40, 5 to 26, 40 to 36),
        RoleCategory.FAITH to listOf(5 to 26, 45 to 36, 65 to 30, 45 to 30),
        RoleCategory.MEDICINE to listOf(5 to 26, 40 to 36, 60 to 30, 50 to 30),
        RoleCategory.LORE to listOf(5 to 30, 35 to 36, 55 to 36, 35 to 36),
        RoleCategory.ARCANE to listOf(10 to 30, 40 to 36, 40 to 40, 35 to 36),
        RoleCategory.GOVERNANCE to listOf(15 to 36, 55 to 36, 40 to 45, 30 to 36),
        RoleCategory.NOBILITY to listOf(15 to 36, 55 to 36, 35 to 40, 30 to 36),
        RoleCategory.AGRICULTURE to listOf(20 to 40, 40 to 40, 45 to 40, 60 to 30),
        RoleCategory.EXTRACTION to listOf(25 to 40, 45 to 40, 45 to 40, 60 to 30),
        RoleCategory.CONSTRUCTION to listOf(25 to 40, 40 to 40, 45 to 40, 60 to 30),
        RoleCategory.WILDERNESS to listOf(35 to 40, 50 to 40, 35 to 40, 60 to 30),
        RoleCategory.HOSPITALITY to listOf(15 to 40, 40 to 40, 35 to 40, 45 to 36),
        RoleCategory.COMMERCE to listOf(15 to 40, 40 to 40, 35 to 45, 45 to 36),
        RoleCategory.ARTS to listOf(15 to 40, 40 to 40, 30 to 40, 45 to 40),
        RoleCategory.SERVICE to listOf(10 to 30, 30 to 36, 50 to 36, 40 to 36),
        RoleCategory.DOMESTIC to listOf(10 to 30, 30 to 36, 50 to 36, 40 to 36),
        RoleCategory.COMMON to listOf(10 to 40, 20 to 40, 30 to 45, 30 to 40)
    )
    private val DEFAULT_RANGES = listOf(20 to 40, 40 to 40, 40 to 40, 40 to 40)

    /** Draw ranges per flesh: aggression, confidence, responsibility, energy. */
    private val TEMPER: List<Pair<List<String>, List<Pair<Int, Int>>>> = listOf(
        listOf("hound", "wolf", "jackal", "lion") to listOf(55 to 30, 55 to 30, 1 to 20, 70 to 25),
        listOf("wraith", "wight", "shade", "phantom", "specter") to listOf(50 to 30, 75 to 20, 1 to 20, 30 to 30),
        listOf("husk", "ghoul", "zombie") to listOf(45 to 30, 55 to 30, 1 to 20, 25 to 30),
        listOf("bear", "boar", "troll", "ogre", "brute") to listOf(70 to 25, 70 to 25, 1 to 20, 55 to 30),
        listOf("rat", "bat", "spider", "vermin", "snake", "scorpion") to listOf(35 to 30, 20 to 25, 1 to 20, 60 to 30),
        listOf("deer", "elk", "hare", "rabbit", "doe", "stag", "goat") to listOf(5 to 20, 30 to 30, 1 to 20, 70 to 25)
    )
    private val WILD_DEFAULT = listOf(40 to 30, 40 to 30, 20 to 30, 45 to 35)

    /** The traits a trade breeds: weights multiplied into the dealing pool. */
    private val CATEGORY_SEEDS: Map<RoleCategory, Map<Trait, Float>> = mapOf(
        RoleCategory.FAITH to mapOf(Trait.Religious to 6f, Trait.Traditionalist to 2.5f, Trait.Stoic to 1.5f),
        RoleCategory.UNDERWORLD to mapOf(
            Trait.Dishonest to 5f, Trait.Lawless to 5f, Trait.Opportunistic to 3f,
            Trait.Suspicious to 3f, Trait.Violent to 2f
        ),
        RoleCategory.MILITARY to mapOf(
            Trait.Disciplined to 4f, Trait.Loyal to 3f, Trait.Bold to 2.5f, Trait.HonorBound to 2.5f
        ),
        RoleCategory.SECURITY to mapOf(Trait.Bold to 3f, Trait.Suspicious to 2f),
        RoleCategory.MEDICINE to mapOf(Trait.Compassionate to 4f, Trait.Patient to 3f, Trait.Calm to 2f),
        RoleCategory.LORE to mapOf(
            Trait.Bookish to 5f, Trait.Scholarly to 5f, Trait.Rational to 2.5f, Trait.Quiet to 1.5f
        ),
        RoleCategory.ARCANE to mapOf(Trait.Bookish to 4f, Trait.Scholarly to 4f, Trait.Superstitious to 2f),
        RoleCategory.COMMERCE to mapOf(Trait.Greedy to 3f, Trait.Opportunistic to 2.5f, Trait.Frugal to 2f),
        RoleCategory.AGRICULTURE to mapOf(
            Trait.Hardworking to 3f, Trait.NatureLoving to 3f, Trait.AnimalLover to 2.5f,
            Trait.Traditionalist to 2f
        ),
        RoleCategory.EXTRACTION to mapOf(Trait.Hardworking to 3f, Trait.Stoic to 2f),
        RoleCategory.CONSTRUCTION to mapOf(Trait.Hardworking to 3f, Trait.CraftMinded to 2.5f),
        RoleCategory.HOSPITALITY to mapOf(Trait.Outgoing to 3f, Trait.Talkative to 2.5f, Trait.Friendly to 2f),
        RoleCategory.ARTS to mapOf(Trait.Artistic to 5f, Trait.Musical to 3f, Trait.Carefree to 1.5f),
        RoleCategory.NOBILITY to mapOf(Trait.StatusSeeking to 3f, Trait.Arrogant to 2f, Trait.Polite to 2f),
        RoleCategory.GOVERNANCE to mapOf(Trait.LawAbiding to 3f, Trait.Ambitious to 3f, Trait.HonorBound to 2f),
        RoleCategory.SERVICE to mapOf(Trait.Reliable to 3f, Trait.Polite to 2.5f, Trait.Reserved to 1.5f),
        RoleCategory.DOMESTIC to mapOf(Trait.Reliable to 3f, Trait.Polite to 2.5f, Trait.ChildLoving to 2f)
    )

    /** How strongly the four values pull a trait's draw weight. */
    private fun valueBias(t: Trait, values: List<Int>): Float =
        (1f + t.aggr * values[0] / 100f).coerceAtLeast(0.05f) *
            (1f + t.conf * values[1] / 100f).coerceAtLeast(0.05f) *
            (1f + t.resp * values[2] / 100f).coerceAtLeast(0.05f) *
            (1f + t.enrg * values[3] / 100f).coerceAtLeast(0.05f)
}
