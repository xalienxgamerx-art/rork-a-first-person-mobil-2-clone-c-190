package com.rork.hollowmarch.game

import kotlin.math.cos
import kotlin.math.sin

/** The eye and the frame it sees: the shared contract between engine and renderer. */

/** Where the eye is and which way it looks. */
class Camera(var x: Float, var y: Float, var angle: Float) {
    var bob: Float = 0f
    /** Eye height as a fraction of the wall: sinks when you crouch, rises when you stand. */
    var eye: Float = 0.5f
    /** True pitch in radians, up positive; drawn as a shear of the horizon. */
    var pitch: Float = 0f
    val dirX: Float get() = cos(angle)
    val dirY: Float get() = sin(angle)
}

class RenderScene(
    val map: GameMap,
    val camera: Camera,
    val torch: Float,
    val outdoorLight: Float,
    val townBearing: Float,
    val townDistance: Float,
    val hurtFlash: Float,
    val strikeArc: Float,
    val swingPhase: Float,
    val groundItems: List<GroundItem> = emptyList(),
    val heldTorch: Boolean = false,
    /** The weapon in hand: its own sprite tinted by its material (-1 keeps the old trusty blade). */
    val heldWeaponSprite: Int = -1,
    val heldWeaponTint: Int = 0xFFFFFF,
    /** The shield on the arm: its own sprite tinted by its material (-1 bares the arm). */
    val heldShieldSprite: Int = -1,
    val heldShieldTint: Int = 0xFFFFFF,
    /** How far the board is raised into the guard, 0..1, eased by the engine. */
    val shieldRaise: Float = 0f,
    val sky: Sky? = null,
    /** Animation clock in seconds, for twinkle and drift. */
    val clock: Float = 0f,
    val dayNumber: Int = 1,
    /** Fraction of the day passed, 0..1; 0.5 is noon. */
    val timeOfDay: Float = 0.5f,
    val weather: WeatherState = WeatherState(WeatherKind.CLEAR, 0f, 0f, 0f),
    /** The aurora's strength over this spot, 0..1; 0 means no lights tonight. */
    val aurora: Float = 0f,
    /** The falling stars of this moment. */
    val meteors: List<Meteor> = emptyList(),
    /** What is in the air right now: honest shot, flying. */
    val projectiles: List<Projectile> = emptyList()
)
