package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.Attr
import com.rork.hollowmarch.game.ChronicleFilter
import com.rork.hollowmarch.game.GameEngine
import com.rork.hollowmarch.game.chronicleMatches
import com.rork.hollowmarch.game.searchMatches
import com.rork.hollowmarch.game.Skill
import com.rork.hollowmarch.game.SkillGroup
import com.rork.hollowmarch.game.WeaponCategory
import com.rork.hollowmarch.game.Layer
import com.rork.hollowmarch.game.pietyLabel
import com.rork.hollowmarch.game.regardLabel
import com.rork.hollowmarch.ui.theme.Ink
import com.rork.hollowmarch.world.Age
import com.rork.hollowmarch.world.EventKind
import com.rork.hollowmarch.world.Figure
import com.rork.hollowmarch.world.Power
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.isSettlement
import com.rork.hollowmarch.world.standingLabel

/** Rumors, powers and the province's own chronicle — the journal you carry. */
@Composable
fun JournalPanel(
    world: World,
    engine: GameEngine?,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Rumors", "Repute", "You", "Powers", "Towns", "Lords", "Gods", "Chronicle")

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Ink.Brass.copy(alpha = 0.24f),
                        activeContentColor = Ink.Brass,
                        activeBorderColor = Ink.Brass.copy(alpha = 0.6f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Ink.Faded,
                        inactiveBorderColor = Ink.Hairline
                    ),
                    icon = {}
                ) {
                    Text(label, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> RumorsTab(world, engine)
            1 -> ReputationTab(world, engine)
            2 -> CharacterTab(engine)
            3 -> PowersTab(world, engine)
            4 -> TownsTab(world, engine)
            5 -> LordsTab(world)
            6 -> GodsTab(world)
            else -> ChronicleTab(world, engine)
        }
    }
}

@Composable
private fun CharacterTab(engine: GameEngine?) {
    if (engine == null) {
        MonoText("No delver walks the province yet.", color = Ink.Faded, fontSize = 12.sp)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                text = engine.klass?.name ?: "No calling taken",
                color = Ink.Brass,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            MonoText(
                "Level ${engine.growth.level}",
                color = Ink.Faded,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(10.dp))
            AttrSummary(engine)
            Spacer(Modifier.height(14.dp))
        }
        SkillGroup.entries.forEach { group ->
            item(key = "group-${group.name}") {
                MonoText(
                    group.label.uppercase(),
                    color = Ink.Faded,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }
            items(Skill.entries.filter { it.group == group }, key = { it.name }) { skill ->
                SkillRow(engine, skill)
            }
        }
        item(key = "proficiencies-header") {
            MonoText(
                "WEAPON PROFICIENCIES",
                color = Ink.Faded,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
        items(WeaponCategory.entries.size, key = { WeaponCategory.entries[it].name }) { index ->
            ProficiencyRow(engine, WeaponCategory.entries[index])
        }
        item {
            MonoText(
                "Melee is how well you fight; the proficiencies are how well you fight with each kind of arm. Hands teach their kind, and every piece teaches its keeper.",
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** The eight attributes as a compact two-column ledger, values only. */
@Composable
private fun AttrSummary(engine: GameEngine) {
    Attr.entries.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            pair.forEach { attr ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonoText(attr.label, color = Ink.Faded, fontSize = 11.sp)
                    MonoText("${engine.stats[attr]}", color = Ink.Parchment, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    engine: GameEngine,
    skill: Skill
) {
    val value = engine.growth.value(skill)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(skill.label, color = Ink.Parchment, fontSize = 13.sp)
                MonoText("$value", color = Ink.Brass, fontSize = 12.sp)
            }
            GrowthBar(
                fraction = engine.growth.progressFraction(skill),
                color = Ink.Brass,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** One family of arm: its standing and the practice toward the next. */
@Composable
private fun ProficiencyRow(
    engine: GameEngine,
    category: WeaponCategory
) {
    val value = engine.proficiencies.value(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(category.label, color = Ink.Parchment, fontSize = 13.sp)
                MonoText("$value", color = Ink.Brass, fontSize = 12.sp)
            }
            GrowthBar(
                fraction = engine.proficiencies.progressFraction(category),
                color = Ink.Verdigris,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun GrowthBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0.02f, 1f)
    Row(modifier = modifier.fillMaxWidth().height(6.dp)) {
        Box(Modifier.weight(f).fillMaxHeight().background(color.copy(alpha = 0.75f)))
        Box(
            Modifier
                .weight((1f - f).coerceAtLeast(0.02f))
                .fillMaxHeight()
                .background(Ink.Hairline.copy(alpha = 0.5f))
        )
    }
}

/** The journal's loose ear: a single search line, forgiving of case and clutter. */
@Composable
private fun JournalSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { MonoText("search…", color = Ink.Dim, fontSize = 12.sp) },
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Ink.Parchment),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Ink.Surface,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Ink.Parchment,
            unfocusedTextColor = Ink.Parchment,
            cursorColor = Ink.Brass,
            focusedIndicatorColor = Ink.Hairline,
            unfocusedIndicatorColor = Ink.Hairline.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun RumorsTab(world: World, engine: GameEngine?) {
    var query by remember { mutableStateOf("") }
    val rumors = (engine?.rumors ?: world.rumors)
        .filter { searchMatches(it.text + " " + it.source, query) }
    val deeds = engine?.deeds ?: emptyList()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            JournalSearchField(query = query, onQuery = { query = it })
            Spacer(Modifier.height(8.dp))
        }
        items(rumors.size) { index ->
            val rumor = rumors[index]
            Row(modifier = Modifier.padding(vertical = 10.dp)) {
                PowerSeal(
                    seed = index * 7 + rumor.text.length,
                    color = if (rumor.aboutPlayer) Ink.Brass else Ink.Faded,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = rumor.text,
                        color = if (rumor.aboutPlayer) Ink.Parchment else Ink.Parchment.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    MonoText(
                        "— ${rumor.source}, ${if (rumor.daysOld <= 0) "today" else "${rumor.daysOld} d"}",
                        color = Ink.Dim,
                        fontSize = 10.sp
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ink.Hairline.copy(alpha = 0.6f))
            )
        }
        if (deeds.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                MonoText("WHAT YOU HAVE DONE", color = Ink.Faded, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
            }
            items(deeds.reversed()) { deed ->
                MonoText("· $deed", color = Ink.Faded, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun ReputationTab(world: World, engine: GameEngine?) {
    if (engine == null) {
        MonoText("You are not yet known in this province.", color = Ink.Faded, fontSize = 12.sp)
        return
    }
    val rep = engine.reputation
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            MonoText(
                "HOW THE PROVINCE SEES YOU",
                color = Ink.Faded,
                fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        item { ReputeHeader("POWERS") }
        items(world.powers.filter { !it.extinct }) { power ->
            val value = rep.standingFor(power.id)
            ReputeRow(
                title = power.name,
                word = standingLabel(value),
                value = value,
                color = reputeInk(value),
                reason = rep.reasonFor(Layer.POWER, power.id)
            )
        }

        item { ReputeHeader("TOWNS") }
        items(
            world.sites
                .filter { it.isSettlement && !it.ruined }
                .sortedByDescending { kotlin.math.abs(rep.regardFor(it.id)) }
                .take(10)
        ) { site ->
            val value = rep.regardFor(site.id)
            ReputeRow(
                title = "${site.name} · ${site.kind.label}" +
                    if (site.id in (engine?.visitedSites ?: emptySet())) " — walked" else "",
                word = regardLabel(value),
                value = value,
                color = reputeInk(value),
                reason = rep.reasonFor(Layer.SETTLEMENT, site.id)
            )
        }

        if (world.houses.any { !it.extinct }) {
            item { ReputeHeader("HOUSES") }
            items(world.houses.filter { !it.extinct }) { house ->
                val value = rep.favorFor(house.id)
                ReputeRow(
                    title = house.name,
                    word = standingLabel(value),
                    value = value,
                    color = reputeInk(value),
                    reason = rep.reasonFor(Layer.HOUSE, house.id)
                )
            }
        }

        item { ReputeHeader("GODS") }
        items(world.deities) { deity ->
            val value = rep.pietyFor(deity.id)
            ReputeRow(
                title = "${deity.name}, ${deity.domain}",
                word = pietyLabel(value),
                value = value,
                color = reputeInk(value),
                reason = rep.reasonFor(Layer.DEITY, deity.id)
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

private fun reputeInk(value: Int): Color = when {
    value <= -25 -> Ink.Blood
    value >= 45 -> Ink.Verdigris
    else -> Ink.Faded
}

@Composable
private fun ReputeHeader(label: String) {
    MonoText(
        label,
        color = Ink.Faded,
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun ReputeRow(title: String, word: String, value: Int, color: Color, reason: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = title,
                color = Ink.Parchment,
                fontSize = 13.sp,
                maxLines = 1
            )
            MonoText(word, color = color, fontSize = 11.sp)
        }
        StandingBar(value, color, Modifier.padding(top = 5.dp))
        if (reason != null) {
            MonoText(
                "— $reason",
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun PowersTab(world: World, engine: GameEngine?) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(world.powers, key = { it.id }) { power ->
            val standing = engine?.standingFor(power) ?: 0
            PowerRow(world, power, standing)
        }
        item {
            Spacer(Modifier.height(14.dp))
            MonoText("PEOPLES", color = Ink.Faded, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(world.cultures, key = { it.id }) { culture ->
            Column(modifier = Modifier.padding(vertical = 7.dp)) {
                Text(culture.name, color = Ink.Parchment, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                MonoText(
                    "${culture.epithet} · ${culture.craft} · ${culture.homeland}",
                    color = Ink.Faded,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PowerRow(world: World, power: Power, standing: Int) {
    val color = when {
        standing <= -25 -> Ink.Blood
        standing >= 45 -> Ink.Verdigris
        else -> Ink.Faded
    }
    val parent = world.power(power.splinterFromId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        PowerSeal(seed = power.id * 13 + power.name.length, color = color, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    power.name,
                    color = if (power.extinct) Ink.Faded else Ink.Parchment,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                MonoText(
                    if (power.extinct) "broken" else standingLabel(standing),
                    color = if (power.extinct) Ink.Blood else color,
                    fontSize = 10.sp
                )
            }
            MonoText(
                "${power.kind.label} of ${world.culture(power.cultureId).name} · founded yr ${power.foundedYear}",
                color = Ink.Dim,
                fontSize = 10.sp
            )
            if (parent != null) {
                MonoText("split from ${parent.name}", color = Ink.Dim, fontSize = 10.sp)
            }
            world.relationLine(power)?.let {
                MonoText(it, color = Ink.Faded, fontSize = 10.sp)
            }
            if (power.goals.isNotEmpty()) {
                MonoText(
                    "purposes: " + power.goals.joinToString(" · "),
                    color = Ink.Verdigris.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            val pressed = world.claims.filter { it.claimantPowerId == power.id }
            if (pressed.isNotEmpty()) {
                MonoText(
                    "presses a claim on " + pressed.joinToString { claim ->
                        val site = world.sites.firstOrNull { it.id == claim.siteId }
                        "${site?.name ?: "lost ground"} (${claim.strength})"
                    },
                    color = Ink.Brass,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = "Holds ${power.creed}.",
                color = Ink.Faded,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            StandingBar(standing, color, Modifier.padding(top = 6.dp))
        }
    }
}

/** The province's steads: who owns them, who really holds sway, and how restive they are. */
@Composable
private fun TownsTab(world: World, engine: GameEngine?) {
    val steads = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }
        .sortedByDescending { engine?.folkOf(it.id) ?: it.population }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(steads, key = { it.id }) { site ->
            TownRow(
                world,
                site,
                engine?.visitedSites ?: emptySet(),
                folk = engine?.folkOf(site.id) ?: site.population,
                stageLabel = engine?.stageAt(site)?.label ?: site.kind.label
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ink.Hairline.copy(alpha = 0.5f))
            )
        }
    }
}

private fun rebellionRisk(site: Site): Pair<String, Color> = when {
    site.loyalty < 25 -> "ready to rise" to Ink.Blood
    site.loyalty < 45 -> "restless" to Ink.Brass
    else -> "quiet" to Ink.Verdigris
}

private fun groupDigits(n: Int): String =
    n.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun TownRow(
    world: World,
    site: Site,
    visited: Set<Int>,
    folk: Int,
    stageLabel: String
) {
    val holder = world.power(site.holderPowerId)
    val (risk, riskColor) = rebellionRisk(site)
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                site.name + if (site.id in visited) " — walked" else "",
                color = Ink.Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            MonoText(risk, color = riskColor, fontSize = 10.sp)
        }
        MonoText(
            "${stageLabel} · ${holder?.name ?: "unclaimed"} · ${groupDigits(folk)} souls",
            color = Ink.Dim,
            fontSize = 10.sp
        )
        MonoText(
            "loyalty ${site.loyalty} · stability ${site.stability} · garrison ${site.garrison}",
            color = if (site.loyalty < 40) Ink.Blood.copy(alpha = 0.9f) else Ink.Faded,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (site.influences.isNotEmpty()) {
            MonoText(
                "who holds sway",
                color = Ink.Faded,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            site.influences.take(4).forEach { influence ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        influence.label,
                        color = Ink.Faded,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    MonoText("${influence.share}", color = Ink.Brass, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(3.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(influence.share / 100f)
                            .fillMaxHeight()
                            .background(Ink.Brass.copy(alpha = 0.55f))
                    )
                }
            }
        }
        val misery = world.events.count {
            (it.kind == EventKind.PLAGUE || it.kind == EventKind.FAMINE) && it.text.contains(site.name)
        }
        if (misery > 0) {
            MonoText(
                "$misery hard years are remembered here",
                color = Ink.Blood.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        val keepers = site.structures.mapNotNull { structure ->
            world.figure(structure.keeperFigureId)
                ?.takeIf { it.diedYear == null }
                ?.let { "${structure.kind.label} — ${it.name}" }
        }
        if (keepers.isNotEmpty()) {
            MonoText(
                keepers.joinToString(" · "),
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        world.claimsOn(site.id).take(1).forEach { claim ->
            MonoText(
                "claimed: ${claim.origin} · strength ${claim.strength}",
                color = Ink.Brass.copy(alpha = 0.85f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** Every name history kept: lords, champions, prophets and monster-slayers. */
@Composable
private fun LordsTab(world: World) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(world.figures.size) { index ->
            LordsRow(world, world.figures[index])
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ink.Hairline.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun LordsRow(world: World, figure: Figure) {
    val holder = world.power(figure.powerId)
    val alive = figure.diedYear == null
    val lifespan = if (alive) "b. yr ${figure.bornYear}" else "yr ${figure.bornYear}–${figure.diedYear}"
    Column(modifier = Modifier.padding(vertical = 9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${figure.name}, ${figure.title}",
                color = if (alive) Ink.Parchment else Ink.Parchment.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            MonoText(
                lifespan + (figure.lifespan?.let { " · $it yrs" } ?: ""),
                color = if (alive) Ink.Verdigris else Ink.Dim,
                fontSize = 10.sp
            )
        }
        val lines = buildList {
            holder?.let { add(it.name) }
            figure.parentIds.firstOrNull()?.let { parentId ->
                world.figure(parentId)?.let { add("heir of ${it.name}") }
            }
        }
        if (lines.isNotEmpty()) {
            MonoText(lines.joinToString(" · "), color = Ink.Dim, fontSize = 10.sp)
        }
        figure.deathCause?.let {
            MonoText("died of $it", color = Ink.Blood.copy(alpha = 0.85f), fontSize = 10.sp)
        }
        figure.feats.forEach { feat ->
            MonoText("· $feat", color = Ink.Faded, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The pantheons: who each people keeps, and which halls keep which gods. */
@Composable
private fun GodsTab(world: World) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        world.cultures.forEach { culture ->
            item(key = "culture-${culture.id}") {
                Column(modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
                    Text(culture.name, color = Ink.Brass, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    MonoText(
                        "hold ${culture.values.joinToString()} · forbid ${culture.taboo}",
                        color = Ink.Dim,
                        fontSize = 10.sp
                    )
                }
            }
            val pantheon = world.deities.filter { it.cultureId == culture.id }
            itemsIndexed(pantheon, key = { _, deity -> "deity-${deity.id}" }) { _, deity ->
                val patrons = world.powers.filter { it.deityId == deity.id && !it.extinct }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(deity.name, color = Ink.Parchment, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        MonoText(deity.domain, color = Ink.Verdigris, fontSize = 10.sp)
                    }
                    MonoText(deity.epithet, color = Ink.Faded, fontSize = 11.sp)
                    MonoText(
                        if (patrons.isEmpty()) "kept by no hall" else "kept by " + patrons.joinToString { it.name },
                        color = Ink.Dim,
                        fontSize = 10.sp
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Ink.Hairline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun StandingBar(standing: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction = ((standing + 100) / 200f).coerceIn(0.02f, 1f)
    Row(modifier = modifier.fillMaxWidth().height(6.dp)) {
        Box(
            Modifier
                .weight(fraction)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.75f))
        )
        Box(
            Modifier
                .weight((1f - fraction).coerceAtLeast(0.02f))
                .fillMaxHeight()
                .background(Ink.Hairline.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun ChronicleTab(world: World, engine: GameEngine?) {
    var ageIndex by remember { mutableIntStateOf(world.ages.lastIndex) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChronicleFilter.ALL) }
    val age: Age = world.ages[ageIndex]
    // the world's own annals, plus the years you have lived through yourself
    val events = (world.eventsInAge(age) + (engine?.chronicle ?: emptyList()))
        .filter { it.year in age.startYear..age.endYear }
        .filter { chronicleMatches(it, query, filter) }
        .sortedBy { it.year }

    Column {
        JournalSearchField(query = query, onQuery = { query = it })
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChronicleFilter.entries.forEach { option ->
                val chosen = filter == option
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .background(if (chosen) Ink.Brass.copy(alpha = 0.18f) else Color.Transparent)
                        .border(1.dp, if (chosen) Ink.Brass.copy(alpha = 0.6f) else Ink.Hairline)
                        .clickable { filter = option }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    MonoText(
                        option.label,
                        color = if (chosen) Ink.Brass else Ink.Faded,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            world.ages.forEachIndexed { index, a ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .clickable { ageIndex = index }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = a.name,
                        color = if (index == ageIndex) Ink.Brass else Ink.Faded,
                        fontSize = 12.sp,
                        fontWeight = if (index == ageIndex) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2
                    )
                    MonoText(
                        "${a.startYear}–${a.endYear}",
                        color = if (index == ageIndex) Ink.Brass else Ink.Dim,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Ink.Hairline)
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(events) { index, event ->
                Row(modifier = Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    PowerSeal(
                        seed = event.year + index,
                        color = when (event.kind) {
                            EventKind.WAR, EventKind.BATTLE, EventKind.PLAGUE,
                            EventKind.FLOOD, EventKind.FAMINE, EventKind.BEAST,
                            EventKind.DESTRUCTION, EventKind.REBELLION, EventKind.PLOT -> Ink.Blood
                            EventKind.FOUNDING, EventKind.MIGRATION, EventKind.TREATY,
                            EventKind.PACT, EventKind.SUCCESSION, EventKind.GROWTH,
                            EventKind.CONSECRATION, EventKind.CATACOMB, EventKind.MARRIAGE -> Ink.Verdigris
                            EventKind.ARTIFACT, EventKind.SEALING, EventKind.PROPHECY,
                            EventKind.CHARTER, EventKind.GUILDHALL, EventKind.TAVERN,
                            EventKind.CLAIM, EventKind.COMET, EventKind.ECLIPSE,
                            EventKind.MOONWONDER -> Ink.Brass
                            else -> Ink.Faded
                        },
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        MonoText("Yr ${event.year}", color = Ink.Brass, fontSize = 11.sp)
                        Text(
                            text = event.text,
                            color = Ink.Parchment.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Ink.Hairline.copy(alpha = 0.55f))
                )
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank() && filter == ChronicleFilter.ALL) {
                            "The chroniclers of this age left nothing behind."
                        } else {
                            "Nothing of that kind in this age."
                        },
                        color = Ink.Dim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            }
        }
    }
}
