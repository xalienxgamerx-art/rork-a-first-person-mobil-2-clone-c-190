package com.rork.hollowmarch.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.hollowmarch.game.Entity
import com.rork.hollowmarch.game.GameEngine
import com.rork.hollowmarch.game.GameViewModel
import com.rork.hollowmarch.game.Hand
import com.rork.hollowmarch.game.HudState
import com.rork.hollowmarch.game.Interact
import com.rork.hollowmarch.game.Item
import com.rork.hollowmarch.game.ItemArchetype
import com.rork.hollowmarch.game.ItemSlot
import com.rork.hollowmarch.game.Renderer3D
import com.rork.hollowmarch.game.RenderScene
import com.rork.hollowmarch.game.Shields
import com.rork.hollowmarch.game.Skill
import com.rork.hollowmarch.game.SkyEvents
import com.rork.hollowmarch.game.WeaponCategory
import com.rork.hollowmarch.ui.components.AutomapPanel
import com.rork.hollowmarch.ui.components.BearingsPanel
import com.rork.hollowmarch.ui.components.CompassTick
import com.rork.hollowmarch.ui.components.EngravedRule
import com.rork.hollowmarch.ui.components.Glyph
import com.rork.hollowmarch.ui.components.JournalPanel
import com.rork.hollowmarch.ui.components.LootPanel
import com.rork.hollowmarch.ui.components.MonoText
import com.rork.hollowmarch.ui.components.Paperdoll
import com.rork.hollowmarch.ui.components.SatchelPanel
import com.rork.hollowmarch.ui.components.TouchActionButton
import com.rork.hollowmarch.ui.components.TouchJoystick
import com.rork.hollowmarch.ui.components.Vial
import com.rork.hollowmarch.ui.components.WoodcutIcon
import com.rork.hollowmarch.ui.components.stoneLabel
import com.rork.hollowmarch.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.atan2

private enum class Bloom { NONE, ACTIONS, SPELLS, REST }

/**
 * The viewport owns the screen. Everything else is a slim carved edge, a needle
 * tick and one fading line — until you tap the bar and it blooms open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    viewModel: GameViewModel,
    onLeave: () -> Unit
) {
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    var bloom by remember { mutableStateOf(Bloom.NONE) }
    var showAutomap by remember { mutableStateOf(false) }
    var showTravel by remember { mutableStateOf(false) }
    var showJournal by remember { mutableStateOf(false) }
    var showLoot by remember { mutableStateOf(false) }
    var showSatchel by remember { mutableStateOf(false) }
    var lootEntity by remember { mutableStateOf<Entity?>(null) }
    var handChoice by remember { mutableStateOf<Item?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }

    val paused = showAutomap || showTravel || showJournal || showLoot || showSatchel || hud.dead
    val travelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val journalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lootSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val satchelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler {
        when {
            showAutomap -> showAutomap = false
            showTravel -> showTravel = false
            showJournal -> showJournal = false
            showLoot -> {
                showLoot = false
                lootEntity = null
            }
            showSatchel -> showSatchel = false
            bloom != Bloom.NONE -> bloom = Bloom.NONE
            else -> {
                viewModel.persist()
                onLeave()
            }
        }
    }

    // The USE hand: pocket, take up, search, or the way out — whatever stands nearest.
    val pressUse: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when (viewModel.useButton()) {
            Interact.LOOT -> {
                lootEntity = viewModel.engine?.lootableHere()
                if (lootEntity != null) showLoot = true
            }
            else -> {}
        }
    }

    LaunchedEffect(bloom, interactionTick) {
        if (bloom != Bloom.NONE) {
            delay(7000)
            bloom = Bloom.NONE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Canvas)
    ) {
        Viewport(
            viewModel = viewModel,
            paused = paused,
            modifier = Modifier.fillMaxSize()
        )

        // --- needle tick at the very top edge
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = insets.calculateTopPadding() + 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CompassTick(
                headingDegrees = ((viewModel.engine?.camera?.angle ?: 0f) * 57.2958f) % 360f,
                modifier = Modifier.width(190.dp)
            )
            MonoText(
                text = if (hud.outdoor) "${hud.locationTitle.uppercase()} · ${hud.clock} · ${hud.timeOfDay}"
                else "${hud.heading} · ${hud.depthLabel}",
                color = Ink.Faded.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
            if (hud.className.isNotBlank()) {
                MonoText(
                    text = "${hud.className} · LVL ${hud.level}",
                    color = Ink.Faded.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            if (hud.detection.isNotBlank()) {
                val tint = when (hud.detection) {
                    "seen" -> Ink.Blood
                    "searching" -> Ink.Brass
                    else -> Ink.Verdigris
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    WoodcutIcon(Glyph.EYE, tint = tint, size = 13.dp)
                    Spacer(Modifier.width(5.dp))
                    MonoText("you are ${hud.detection}", color = tint, fontSize = 10.sp)
                }
            }
            if (hud.rangedLine.isNotBlank()) {
                MonoText(
                    text = hud.rangedLine,
                    color = if (hud.rangedLine.endsWith("…")) Ink.Brass else Ink.Parchment,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        // --- fading chronicle of the last two things that happened,
        //     told at the top so the hands at the bottom keep their room
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = insets.calculateTopPadding() + 44.dp)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            hud.lines.forEach { line ->
                Text(
                    text = line.text,
                    color = Ink.Parchment.copy(alpha = 0.9f * line.alpha),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Ink.Canvas.copy(alpha = 0.55f * line.alpha))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .padding(bottom = 2.dp)
                )
            }
            if (hud.usePrompt.isNotBlank()) {
                Text(
                    text = hud.usePrompt,
                    color = Ink.Brass,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = pressUse)
                        .background(Ink.Canvas.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // --- twin thumb-sticks, the hand that swings, the hand that opens
        if (!paused) {
            TouchJoystick(
                label = "MOVE",
                onChange = { x, y -> viewModel.setMoveInput(move = -y, strafe = x) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = insets.calculateBottomPadding() + 86.dp)
                    .size(118.dp)
            )
            TouchJoystick(
                label = "LOOK",
                onChange = { x, y ->
                    viewModel.setTurnInput(turn = x)
                    viewModel.setLookInput(look = -y)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = insets.calculateBottomPadding() + 86.dp)
                    .size(118.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 152.dp, bottom = insets.calculateBottomPadding() + 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TouchActionButton(
                    glyph = Glyph.SWORD,
                    label = "SWING",
                    diameter = 68.dp,
                    onClick = {
                        viewModel.strike()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                Spacer(Modifier.height(12.dp))
                TouchActionButton(
                    glyph = Glyph.EYE,
                    label = "USE",
                    diameter = 52.dp,
                    enabled = hud.usePrompt.isNotBlank(),
                    onClick = pressUse
                )
                Spacer(Modifier.height(12.dp))
                TouchActionButton(
                    glyph = Glyph.SHIELD,
                    label = if (hud.blocking) "LOWER" else "GUARD",
                    diameter = 44.dp,
                    onClick = {
                        viewModel.toggleBlock()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                Spacer(Modifier.height(12.dp))
                TouchActionButton(
                    glyph = Glyph.CROUCH,
                    label = if (hud.crouched) "RISE" else "CROUCH",
                    diameter = 44.dp,
                    onClick = {
                        viewModel.toggleCrouch()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }

        // --- the nestled edge: bar plus everything it blooms into
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = bloom != Bloom.NONE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                BloomPanel(
                    hud = hud,
                    bloom = bloom,
                    canOffer = viewModel.engine?.let { it.canOffer() || it.canOfferAtShrine() } == true,
                    offerLabel = if (viewModel.engine?.canOffer() == true) {
                        "Offer 10 brass at the temple"
                    } else {
                        "Offer 5 brass at the shrine"
                    },
                    wardCost = viewModel.engine?.wardCost() ?: 8,
                    mendCost = viewModel.engine?.mendCost() ?: 12,
                    tillDawn = viewModel.engine?.hoursTillDawn() ?: 12,
                    canQuickRemedy = viewModel.engine?.let { e ->
                        e.vitality < e.maxVitality &&
                            e.inventory.all.any { item -> item.archetype == ItemArchetype.REMEDY }
                    } == true,
                    remedyCount = viewModel.engine?.inventory?.all
                        ?.filter { it.archetype == ItemArchetype.REMEDY }
                        ?.sumOf { it.count } ?: 0,
                    onBloom = {
                        bloom = it
                        interactionTick++
                    },
                    onMap = {
                        showAutomap = true
                        bloom = Bloom.NONE
                    },
                    onTransport = {
                        if (hud.outdoor) {
                            showTravel = true
                        } else {
                            viewModel.engine?.pushLog("No road runs underground. Climb out first.")
                        }
                        bloom = Bloom.NONE
                    },
                    onJournal = {
                        showJournal = true
                        bloom = Bloom.NONE
                    },
                    onSatchel = {
                        showSatchel = true
                        bloom = Bloom.NONE
                    },
                    onWard = {
                        viewModel.castWard()
                        interactionTick++
                    },
                    onMend = {
                        viewModel.castMend()
                        interactionTick++
                    },
                    onTorch = {
                        viewModel.relightTorch()
                        interactionTick++
                    },
                    onRest = { hours ->
                        viewModel.rest(hours)
                        bloom = Bloom.NONE
                    },
                    onOffer = {
                        viewModel.offer()
                        interactionTick++
                    },
                    onQuickRemedy = {
                        viewModel.useRemedy()
                        interactionTick++
                    }
                )
            }
            StatusEdgeBar(
                hud = hud,
                bottomInset = insets.calculateBottomPadding(),
                onTap = {
                    bloom = if (bloom == Bloom.NONE) Bloom.ACTIONS else Bloom.NONE
                    interactionTick++
                }
            )
        }

        if (showAutomap) {
            val engine = viewModel.engine
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.Canvas.copy(alpha = 0.86f))
                    .clickable { showAutomap = false }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (engine != null) {
                    if (engine.onOverland) {
                        // the province keeps no map: only bearings, sky and smoke
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MonoText(
                                "You carry no map of the province.",
                                color = Ink.Parchment,
                                fontSize = 14.sp
                            )
                            MonoText(
                                "Trust your bearing, the sun, and the smoke on the sky.",
                                color = Ink.Faded,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else {
                        AutomapPanel(
                            map = engine.map,
                            playerX = engine.camera.x,
                            playerY = engine.camera.y,
                            playerAngle = engine.camera.angle,
                            exploredPercent = hud.explored
                        )
                    }
                }
            }
        }

        if (hud.dead) {
            DeathOverlay(
                onRevive = { viewModel.revive() },
                onLeave = {
                    viewModel.persist()
                    onLeave()
                }
            )
        }
    }

    if (showTravel) {
        ModalBottomSheet(
            onDismissRequest = { showTravel = false },
            sheetState = travelSheetState,
            containerColor = Ink.Overlay,
            contentColor = Ink.Parchment
        ) {
            BearingsPanel(
                province = viewModel.world.provinceName,
                bearings = viewModel.bearings(),
                weatherNote = viewModel.engine?.weatherWalkNote() ?: "",
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
    }

    if (showJournal) {
        ModalBottomSheet(
            onDismissRequest = { showJournal = false },
            sheetState = journalSheetState,
            containerColor = Ink.Overlay,
            contentColor = Ink.Parchment
        ) {
            JournalPanel(
                world = viewModel.world,
                engine = viewModel.engine,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .padding(bottom = 20.dp)
            )
        }
    }

    if (showLoot) {
        ModalBottomSheet(
            onDismissRequest = {
                showLoot = false
                lootEntity = null
            },
            sheetState = lootSheetState,
            containerColor = Ink.Overlay,
            contentColor = Ink.Parchment
        ) {
            val entity = lootEntity
            val engine = viewModel.engine
            if (engine != null && entity != null) {
                LootPanel(
                    entity = entity,
                    styleRoster = engine.styleRoster,
                    modifier = Modifier
                        .fillMaxHeight(0.82f)
                        .padding(bottom = 20.dp),
                    onTake = { item ->
                        viewModel.takeLootItem(entity, item)
                        if (entity.loot.isEmpty() && entity.lootBrass == 0 && entity.equipment?.isEmpty() != false) {
                            showLoot = false
                        }
                    },
                    onTakeEquipped = { slot ->
                        viewModel.takeEquipped(entity, slot)
                        if (entity.loot.isEmpty() && entity.lootBrass == 0 && entity.equipment?.isEmpty() != false) {
                            showLoot = false
                        }
                    },
                    onTakeAll = {
                        viewModel.takeAllLoot(entity)
                        showLoot = false
                        lootEntity = null
                    }
                )
            }
        }
    }

    if (showSatchel) {
        val engine = viewModel.engine
        ModalBottomSheet(
            onDismissRequest = { showSatchel = false },
            sheetState = satchelSheetState,
            containerColor = Ink.Overlay,
            contentColor = Ink.Parchment
        ) {
            if (engine != null) {
                SatchelPanel(
                    inventory = engine.inventory,
                    equipment = engine.equipment,
                    styleRoster = engine.styleRoster,
                    load = engine.inventory.weight(),
                    capacity = engine.carryCapacity(),
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .padding(bottom = 20.dp),
                    onUse = { item ->
                        when (item.archetype) {
                            ItemArchetype.REMEDY -> viewModel.useRemedy()
                            ItemArchetype.TORCH -> viewModel.relightTorch()
                            else -> {}
                        }
                    },
                    onDrop = { item -> viewModel.dropItem(item) },
                    onEquip = { item ->
                        // Both hands free: ask which hand takes it. Otherwise it takes the free one.
                        if (viewModel.engine?.canChooseHand(item) == true) handChoice = item
                        else viewModel.equipItem(item)
                    },
                    onUnequip = { slot -> viewModel.unequipSlot(slot) },
                    weaponLore = { item -> weaponLoreLine(engine, item) }
                )
            }
        }
    }

    handChoice?.let { item ->
        val engine = viewModel.engine ?: return@let
        AlertDialog(
            onDismissRequest = { handChoice = null },
            containerColor = Ink.Overlay,
            titleContentColor = Ink.Brass,
            textContentColor = Ink.Parchment,
            title = { Text("Which hand?", fontSize = 16.sp) },
            text = {
                Text(
                    "You have both hands free. Which takes the ${engine.styleRoster.nameFor(item)}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.equipItem(item, Hand.RIGHT)
                    handChoice = null
                }) { Text("Right", color = Ink.Brass) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.equipItem(item, Hand.LEFT)
                    handChoice = null
                }) { Text("Left", color = Ink.Brass) }
            }
        )
    }
}

/** The lore of a held arm: the hand's standing with its kind, and the keeper's familiarity with this one piece. */
private fun weaponLoreLine(engine: GameEngine, item: Item): String? {
    if (item.archetype.slot == ItemSlot.SHIELD) {
        return "${Shields.summary(item)} · ${engine.growth.value(Skill.DEFENSE)} Defense"
    }
    if (!item.archetype.isWeapon) return null
    val category = WeaponCategory.forWeapon(item.archetype) ?: WeaponCategory.UNARMED
    val mastery = if (item.uid > 0) engine.masteries.value(item.uid) else 0
    return "${category.label} proficiency ${engine.proficiencies.value(category)} · " +
        "mastery of this piece $mastery"
}

@Composable
private fun Viewport(
    viewModel: GameViewModel,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val heightPx = constraints.maxHeight.coerceAtLeast(1)
        val bufferW = 150
        val bufferH = (bufferW * heightPx / widthPx).coerceIn(120, 420)

        val renderer = remember(bufferW, bufferH) {
            Renderer3D(bufferW, bufferH)
        }
        val bitmap = remember(bufferW, bufferH) {
            Bitmap.createBitmap(bufferW, bufferH, Bitmap.Config.ARGB_8888)
        }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val frame = remember { mutableIntStateOf(0) }

        LaunchedEffect(bufferW, bufferH, paused) {
            var last = 0L
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f)
                    last = now
                    val engine = viewModel.engine ?: return@withFrameNanos
                    if (!paused) viewModel.step(dt.coerceIn(0f, 0.08f))
                    // smoke on the sky: the nearest living gates, seen from the open road
                    val bearing = engine.skylineBearing()
                        ?: atan2(2f - engine.camera.y, (engine.map.width / 2f) - engine.camera.x)
                    renderer.render(
                        RenderScene(
                            map = engine.map,
                            camera = engine.camera,
                            torch = engine.litTorch,
                            outdoorLight = engine.skyLight,
                            townBearing = bearing,
                            townDistance = if (engine.outdoor) 1f else 0f,
                            hurtFlash = engine.hurtFlash,
                            strikeArc = engine.strikeArc,
                            swingPhase = engine.swingPhase,
                            groundItems = engine.groundItems,
                            heldTorch = engine.holdsTorch,
                            heldWeaponSprite = engine.heldWeaponSpriteId,
                            heldWeaponTint = engine.heldWeaponTint,
                            heldShieldSprite = engine.heldShieldSpriteId,
                            heldShieldTint = engine.heldShieldTint,
                            shieldRaise = engine.shieldRaise,
                            sky = if (engine.outdoor) engine.sky else null,
                            clock = engine.animTime,
                            dayNumber = engine.day,
                            timeOfDay = engine.timeOfDay,
                            weather = engine.weather,
                            aurora = engine.auroraStrength(),
                            meteors = if (engine.outdoor) SkyEvents.meteors(
                                engine.world.seed, engine.day, engine.animTime,
                                engine.outdoorLight < 0.25f, engine.weather.cloud
                            ) else emptyList(),
                            projectiles = engine.projectiles
                        )
                    )
                    bitmap.setPixels(renderer.pixels, 0, bufferW, 0, 0, bufferW, bufferH)
                    frame.intValue++
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            frame.intValue
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bufferW, bufferH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None
            )

            // the aim-mark: a fine brass reticle at the delver's point of strike.
            // It pales as the shield rises to cover the view, and reddens with pain.
            val engine = viewModel.engine
            if (engine != null && !paused) {
                val alpha = (1f - engine.shieldRaise.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                if (alpha > 0.02f) {
                    val hurt = engine.hurtFlash.coerceIn(0f, 1f)
                    val ink = lerp(Ink.Brass, Ink.Blood, hurt * 0.85f)
                    val shade = Ink.Canvas.copy(alpha = 0.55f * alpha)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val gap = 4.dp.toPx()
                    val tick = 4.dp.toPx()
                    val bold = 3f.dp.toPx()
                    val fine = 1.2f.dp.toPx()
                    val arms = listOf(
                        Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)
                    )
                    // dark ground pass first: the mark must read against snow and sky
                    for (d in arms) {
                        drawLine(
                            shade,
                            Offset(cx + d.x * gap, cy + d.y * gap),
                            Offset(cx + d.x * (gap + tick), cy + d.y * (gap + tick)),
                            strokeWidth = bold,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(shade, radius = bold, center = Offset(cx, cy))
                    for (d in arms) {
                        drawLine(
                            ink.copy(alpha = 0.85f * alpha),
                            Offset(cx + d.x * gap, cy + d.y * gap),
                            Offset(cx + d.x * (gap + tick), cy + d.y * (gap + tick)),
                            strokeWidth = fine,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(ink.copy(alpha = 0.9f * alpha), radius = fine, center = Offset(cx, cy))
                }
            }
        }
    }
}

@Composable
private fun StatusEdgeBar(
    hud: HudState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Surface.copy(alpha = 0.95f))
            .clickable(onClick = onTap)
            .padding(bottom = bottomInset)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Ink.Hairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Paperdoll(
                vitalityFraction = hud.vitality / hud.maxVitality.toFloat(),
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
            )
            Spacer(Modifier.width(14.dp))
            Vial(
                fraction = hud.vitality / hud.maxVitality.toFloat(),
                color = Ink.Blood,
                label = "VIT",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Vial(
                fraction = hud.fatigue / hud.maxFatigue.toFloat(),
                color = Ink.Brass,
                label = "FAT",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Vial(
                fraction = hud.magicka / hud.maxMagicka.toFloat(),
                color = Ink.Verdigris,
                label = "MAG",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WoodcutIcon(
                    glyph = if (hud.outdoor) Glyph.TRANSPORT else Glyph.MAP,
                    tint = Ink.Faded,
                    size = 20.dp
                )
                MonoText(
                    if (hud.outdoor) "roads" else "${hud.explored}%",
                    color = Ink.Dim,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun BloomPanel(
    hud: HudState,
    bloom: Bloom,
    canOffer: Boolean,
    offerLabel: String,
    wardCost: Int,
    mendCost: Int,
    tillDawn: Int,
    canQuickRemedy: Boolean,
    remedyCount: Int,
    onBloom: (Bloom) -> Unit,
    onMap: () -> Unit,
    onTransport: () -> Unit,
    onJournal: () -> Unit,
    onSatchel: () -> Unit,
    onWard: () -> Unit,
    onMend: () -> Unit,
    onTorch: () -> Unit,
    onRest: (Int) -> Unit,
    onOffer: () -> Unit,
    onQuickRemedy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Overlay.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MonoText("VIT ${hud.vitality}/${hud.maxVitality}", color = Ink.Blood, fontSize = 11.sp)
            MonoText("FAT ${hud.fatigue}/${hud.maxFatigue}", color = Ink.Brass, fontSize = 11.sp)
            MonoText("MAG ${hud.magicka}/${hud.maxMagicka}", color = Ink.Verdigris, fontSize = 11.sp)
            MonoText("brass ${hud.brass}", color = Ink.Faded, fontSize = 11.sp)
            MonoText(
                "${stoneLabel(hud.load)}st",
                color = if (hud.load > hud.capacity) Ink.Blood else Ink.Faded,
                fontSize = 11.sp
            )
        }
        EngravedRule(Modifier.padding(vertical = 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GlyphButton(Glyph.SPELLBOOK, "Spells") {
                onBloom(if (bloom == Bloom.SPELLS) Bloom.ACTIONS else Bloom.SPELLS)
            }
            GlyphButton(Glyph.REST, "Rest") {
                onBloom(if (bloom == Bloom.REST) Bloom.ACTIONS else Bloom.REST)
            }
            GlyphButton(Glyph.MAP, "Map", onClick = onMap)
            GlyphButton(Glyph.BAG, "Satchel", onClick = onSatchel)
            GlyphButton(Glyph.TRANSPORT, "Bearings", onClick = onTransport)
            GlyphButton(Glyph.JOURNAL, "Journal", onClick = onJournal)
        }
        AnimatedVisibility(visible = canOffer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InkChip(offerLabel, Modifier.weight(1f), onOffer)
            }
        }
        AnimatedVisibility(visible = canQuickRemedy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InkChip("Drink a remedy · $remedyCount", Modifier.weight(1f), onQuickRemedy)
            }
        }
        AnimatedVisibility(visible = bloom == Bloom.SPELLS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InkChip("Ward · $wardCost", Modifier.weight(1f), onWard)
                InkChip("Mend · $mendCost", Modifier.weight(1f), onMend)
                InkChip("Dress torch · 3", Modifier.weight(1f), onTorch)
            }
        }
        AnimatedVisibility(visible = bloom == Bloom.REST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InkChip("One hour", Modifier.weight(1f)) { onRest(1) }
                InkChip("Eight hours", Modifier.weight(1f)) { onRest(8) }
                InkChip("Till dawn · $tillDawn h", Modifier.weight(1f)) { onRest(tillDawn) }
            }
        }
    }
}

@Composable
private fun GlyphButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Ink.Surface)
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            WoodcutIcon(glyph, tint = Ink.Parchment, size = 24.dp)
        }
        Spacer(Modifier.height(5.dp))
        MonoText(label, color = Ink.Faded, fontSize = 9.sp)
    }
}

@Composable
private fun InkChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(Ink.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        MonoText(label, color = Ink.Parchment, fontSize = 11.sp)
    }
}

@Composable
private fun DeathOverlay(onRevive: () -> Unit, onLeave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60D0B09)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "YOU ARE DEAD",
                color = Ink.Blood,
                fontSize = 22.sp,
                letterSpacing = 6.sp,
                fontFamily = FontFamily.Serif
            )
            EngravedRule(Modifier.padding(vertical = 16.dp))
            Text(
                text = "The province takes note, and goes on without you. Someone will drag you out for half your brass.",
                color = Ink.Faded,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRevive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Brass,
                    contentColor = Ink.Canvas
                )
            ) {
                Text("Wake at the barrow")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Back to the title plate",
                color = Ink.Faded,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(onClick = onLeave)
                    .padding(10.dp)
            )
        }
    }
}
