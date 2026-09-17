package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** A flat, camera-facing billboard, painted once at startup like a 1996 sprite sheet. */
class Sprite(val w: Int, val h: Int, val px: IntArray)

object Sprites {
    const val HUSK = 0
    const val HOUND = 1
    const val WRAITH = 2
    const val REEDS = 3
    const val DEAD_TREE = 4
    const val STANDING_STONE = 5
    const val GRAVE = 6
    const val BRAZIER = 7
    const val HELD_SWORD = 8
    const val HELD_TORCH = 9
    const val URN = 10
    const val ITEM = 11
    const val CORPSE = 12
    const val TENT = 13
    const val PINE = 14
    const val CAIRN = 15
    const val HILLDOOR = 16
    const val WATCHFIRE = 17
    const val PILGRIM = 18
    // The arms: one shape per weapon archetype, painted in tintable gray so
    // each takes the hue of the metal it was worked from.
    const val W_BLADE = 19
    const val W_AXE = 20
    const val W_MACE = 21
    const val W_SPEAR = 22
    const val W_DAGGER = 23
    const val W_BOW = 24
    const val W_CLUB = 25
    const val W_BLUDGEON = 26
    const val W_FLAIL = 27
    const val W_FLANGED_MACE = 28
    const val W_MORNING_STAR = 29
    const val W_WAR_HAMMER = 30
    const val W_SHESTOPYOR = 31
    const val W_QUARTERSTAFF = 32
    const val W_SHORTSWORD = 33
    const val W_FALCHION = 34
    const val W_MESSER = 35
    const val W_SABRE = 36
    const val W_SCIMITAR = 37
    const val W_ARMING_SWORD = 38
    const val W_ULFBERHT = 39
    const val W_BATTLE_AXE = 40
    const val W_KATANA = 41
    const val W_BILL = 42
    const val W_DANE_AXE = 43
    const val W_GLAIVE = 44
    const val W_GUANDAO = 45
    const val W_PUDAO = 46
    const val W_SOVNYA = 47
    const val W_NAGINATA = 48
    const val W_BARDICHE = 49
    const val W_WAR_SCYTHE = 50
    const val W_KNIFE = 51
    const val W_RAPIER = 52
    const val W_HORSEMANS_PICK = 53
    const val W_ESTOC = 54
    const val W_PIKE = 55
    const val W_POLEAXE = 56
    const val W_HALBERD = 57
    const val W_HARPOON = 58
    const val W_TRIDENT = 59
    const val W_BEC_DE_CORBIN = 60
    const val W_LANCE = 61
    const val W_PLANCON = 62
    // The boards: one shape per shield archetype, painted in tintable gray so
    // each takes the hue of the material it was worked from.
    const val S_BUCKLER = 63
    const val S_ROUND = 64
    const val S_TARGE = 65
    const val S_HEATER = 66
    const val S_KITE = 67
    const val S_ALMOND = 68
    const val S_HUNGARIAN = 69
    const val S_PAVISE = 70
    const val S_RONDACHE = 71
    const val S_ROTELLA = 72
    const val S_TOWER = 73
    // The ranged arms: one shape per marksmanship archetype, painted in tintable
    // gray so each takes the hue of the metal it was worked from.
    const val W_CROSSBOW = 74
    const val W_ARQUEBUS = 75
    const val W_HAND_CANNON = 76
    const val W_SLING = 77
    // The shot: what flies when the string is cut or the powder speaks.
    const val W_THREE_EYE = 82
    const val W_THROWING_KNIFE = 83
    const val W_FRANCISCA = 84
    const val W_CHAKRAM = 85
    const val W_SHURIKEN = 86
    const val P_ARROW = 78
    const val P_BOLT = 79
    const val P_BALL = 80
    const val P_SHOT = 81
    const val COUNT = 87

    private lateinit var sheet: Array<Sprite>
    private var built = false

    fun ensureBuilt() {
        if (built) return
        val rng = Random(770411L)
        sheet = Array(COUNT) { id ->
            when (id) {
                HUSK -> husk(rng)
                HOUND -> hound(rng)
                WRAITH -> wraith(rng)
                REEDS -> reeds(rng)
                DEAD_TREE -> deadTree(rng)
                STANDING_STONE -> standingStone(rng)
                GRAVE -> grave(rng)
                BRAZIER -> brazier(rng)
                HELD_SWORD -> heldSword(rng)
                URN -> urn(rng)
                ITEM -> itemBundle(rng)
                CORPSE -> corpse(rng)
                TENT -> tent(rng)
                PINE -> pine(rng)
                CAIRN -> cairn(rng)
                HILLDOOR -> hilldoor(rng)
                WATCHFIRE -> watchfire(rng)
                PILGRIM -> pilgrim(rng)
                W_BLADE -> wBlade(rng)
                W_AXE -> wAxe(rng)
                W_MACE -> wMace(rng)
                W_SPEAR -> wSpear(rng)
                W_DAGGER -> wDagger(rng)
                W_BOW -> wBow(rng)
                W_CLUB -> wClub(rng)
                W_BLUDGEON -> wBludgeon(rng)
                W_FLAIL -> wFlail(rng)
                W_FLANGED_MACE -> wFlangedMace(rng)
                W_MORNING_STAR -> wMorningStar(rng)
                W_WAR_HAMMER -> wWarHammer(rng)
                W_SHESTOPYOR -> wShestopyor(rng)
                W_QUARTERSTAFF -> wQuarterstaff(rng)
                W_SHORTSWORD -> wShortsword(rng)
                W_FALCHION -> wFalchion(rng)
                W_MESSER -> wMesser(rng)
                W_SABRE -> wSabre(rng)
                W_SCIMITAR -> wScimitar(rng)
                W_ARMING_SWORD -> wArmingSword(rng)
                W_ULFBERHT -> wUlfberht(rng)
                W_BATTLE_AXE -> wBattleAxe(rng)
                W_KATANA -> wKatana(rng)
                W_BILL -> wBill(rng)
                W_DANE_AXE -> wDaneAxe(rng)
                W_GLAIVE -> wGlaive(rng)
                W_GUANDAO -> wGuandao(rng)
                W_PUDAO -> wPudao(rng)
                W_SOVNYA -> wSovnya(rng)
                W_NAGINATA -> wNaginata(rng)
                W_BARDICHE -> wBardiche(rng)
                W_WAR_SCYTHE -> wWarScythe(rng)
                W_KNIFE -> wKnife(rng)
                W_RAPIER -> wRapier(rng)
                W_HORSEMANS_PICK -> wHorsemanPick(rng)
                W_ESTOC -> wEstoc(rng)
                W_PIKE -> wPike(rng)
                W_POLEAXE -> wPoleaxe(rng)
                W_HALBERD -> wHalberd(rng)
                W_HARPOON -> wHarpoon(rng)
                W_TRIDENT -> wTrident(rng)
                W_BEC_DE_CORBIN -> wBecDeCorbin(rng)
                W_LANCE -> wLance(rng)
                W_PLANCON -> wPlancon(rng)
                S_BUCKLER -> sBuckler(rng)
                S_ROUND -> sRound(rng)
                S_TARGE -> sTarge(rng)
                S_HEATER -> sHeater(rng)
                S_KITE -> sKite(rng)
                S_ALMOND -> sAlmond(rng)
                S_HUNGARIAN -> sHungarian(rng)
                S_PAVISE -> sPavise(rng)
                S_RONDACHE -> sRondache(rng)
                S_ROTELLA -> sRotella(rng)
                S_TOWER -> sTower(rng)
                W_CROSSBOW -> wCrossbow(rng)
                W_ARQUEBUS -> wArquebus(rng)
                W_HAND_CANNON -> wHandCannon(rng)
                W_SLING -> wSling(rng)
                P_ARROW -> pArrow(rng)
                P_BOLT -> pBolt(rng)
                P_BALL -> pBall(rng)
                P_SHOT -> pShot(rng)
                W_THREE_EYE -> wThreeEye(rng)
                W_THROWING_KNIFE -> wThrowingKnife(rng)
                W_FRANCISCA -> wFrancisca(rng)
                W_CHAKRAM -> wChakram(rng)
                W_SHURIKEN -> wShuriken(rng)
                else -> heldTorch(rng)
            }
        }
        built = true
    }

    operator fun get(id: Int): Sprite = sheet[id.coerceIn(0, COUNT - 1)]

    private class Painter(val w: Int, val h: Int) {
        val px = IntArray(w * h)

        fun put(x: Int, y: Int, color: Int) {
            if (x in 0 until w && y in 0 until h) px[y * w + x] = color or (0xFF shl 24)
        }

        /** A pixel of fixed hue — leather, wood, cord — drawn but never tinted. */
        fun putFixed(x: Int, y: Int, color: Int) {
            if (x in 0 until w && y in 0 until h) px[y * w + x] = color or (0xFE shl 24)
        }

        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, rng: Random, grain: Int = 22) {
            for (y in y0..y1) for (x in x0..x1) {
                put(x, y, jitter(color, rng, grain))
            }
        }

        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, color: Int, rng: Random, grain: Int = 22) {
            for (y in (cy - ry)..(cy + ry)) {
                for (x in (cx - rx)..(cx + rx)) {
                    val dx = (x - cx).toFloat() / rx
                    val dy = (y - cy).toFloat() / ry
                    if (dx * dx + dy * dy <= 1f) {
                        val lit = 1f - 0.35f * ((dx + 0.4f).coerceIn(-1f, 1f))
                        put(x, y, jitter(scale(color, lit), rng, grain))
                    }
                }
            }
        }

        fun limb(x0: Int, y0: Int, x1: Int, y1: Int, thick: Int, color: Int, rng: Random) {
            val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
            for (i in 0..steps) {
                val x = x0 + (x1 - x0) * i / steps
                val y = y0 + (y1 - y0) * i / steps
                for (ty in -thick / 2..thick / 2) for (tx in -thick / 2..thick / 2) {
                    put(x + tx, y + ty, jitter(color, rng, 18))
                }
            }
        }

        fun build(): Sprite = Sprite(w, h, px)
    }

    private fun jitter(color: Int, rng: Random, amount: Int): Int {
        val d = rng.nextInt(amount) - amount / 2
        val r = (((color shr 16) and 0xFF) + d).coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) + d).coerceIn(0, 255)
        val b = ((color and 0xFF) + d).coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun scale(color: Int, f: Float): Int {
        val r = ((((color shr 16) and 0xFF) * f).roundToInt()).coerceIn(0, 255)
        val g = ((((color shr 8) and 0xFF) * f).roundToInt()).coerceIn(0, 255)
        val b = (((color and 0xFF) * f).roundToInt()).coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun husk(rng: Random): Sprite {
        val p = Painter(48, 72)
        val skin = 0x8D7C61
        p.limb(19, 44, 16, 70, 7, scale(skin, 0.75f), rng)
        p.limb(29, 44, 33, 70, 7, scale(skin, 0.7f), rng)
        p.ellipse(24, 34, 11, 15, skin, rng)
        p.limb(14, 26, 6, 46, 5, scale(skin, 0.7f), rng)
        p.limb(34, 26, 43, 42, 5, scale(skin, 0.8f), rng)
        p.ellipse(24, 13, 8, 9, scale(skin, 1.05f), rng)
        // sunken sockets and a slack jaw
        p.rect(20, 11, 22, 13, 0x140F0B, rng, 6)
        p.rect(26, 11, 28, 13, 0x140F0B, rng, 6)
        p.rect(23, 17, 26, 20, 0x1A120D, rng, 6)
        // tar-marks of the cult that raised it
        p.rect(19, 6, 29, 8, 0x241A12, rng, 10)
        // old wounds
        for (i in 0 until 14) {
            val x = 14 + rng.nextInt(20)
            val y = 24 + rng.nextInt(24)
            p.rect(x, y, x + 1 + rng.nextInt(2), y + 1, 0x6B2A1E, rng, 12)
        }
        return p.build()
    }

    private fun hound(rng: Random): Sprite {
        val p = Painter(56, 44)
        val hide = 0x4A3B2A
        p.ellipse(28, 22, 17, 9, hide, rng)
        p.limb(16, 28, 13, 42, 5, scale(hide, 0.8f), rng)
        p.limb(24, 28, 24, 42, 5, scale(hide, 0.75f), rng)
        p.limb(36, 28, 37, 42, 5, scale(hide, 0.8f), rng)
        p.limb(43, 28, 46, 42, 5, scale(hide, 0.75f), rng)
        p.ellipse(11, 17, 8, 6, scale(hide, 1.1f), rng)
        p.rect(3, 16, 9, 19, scale(hide, 0.9f), rng)
        p.rect(5, 15, 6, 16, 0xA33B28, rng, 4)
        p.limb(45, 18, 54, 12, 3, scale(hide, 0.7f), rng)
        return p.build()
    }

    private fun wraith(rng: Random): Sprite {
        val p = Painter(48, 76)
        val cloth = 0x2B2C33
        for (y in 8 until 76) {
            val halfWidth = (6 + (y - 8) * 0.22f).toInt()
            val fade = if (y > 62) (76 - y) / 14f else 1f
            for (x in (24 - halfWidth)..(24 + halfWidth)) {
                if (rng.nextFloat() > fade) continue
                val wave = Textures.valueNoise(x * 0.3f, y * 0.12f, 11)
                p.put(x, y, jitter(scale(cloth, 0.7f + wave * 0.6f), rng, 14))
            }
        }
        p.ellipse(24, 16, 9, 10, 0x191A1F, rng, 8)
        p.rect(20, 14, 22, 16, 0x4E7A6B, rng, 8)
        p.rect(26, 14, 28, 16, 0x4E7A6B, rng, 8)
        return p.build()
    }

    private fun reeds(rng: Random): Sprite {
        val p = Painter(44, 56)
        for (i in 0 until 26) {
            val baseX = 4 + rng.nextInt(36)
            val height = 22 + rng.nextInt(30)
            val lean = rng.nextInt(7) - 3
            val color = if (rng.nextInt(3) == 0) 0x6A6440 else 0x3E4A2E
            p.limb(baseX, 55, baseX + lean, 55 - height, 2, color, rng)
        }
        return p.build()
    }

    private fun deadTree(rng: Random): Sprite {
        val p = Painter(64, 96)
        val bark = 0x2E271D
        p.limb(32, 95, 30, 46, 9, bark, rng)
        p.limb(30, 60, 14, 40, 5, bark, rng)
        p.limb(30, 54, 48, 32, 5, bark, rng)
        p.limb(14, 40, 6, 28, 3, bark, rng)
        p.limb(48, 32, 58, 20, 3, bark, rng)
        p.limb(30, 46, 33, 18, 4, bark, rng)
        p.limb(33, 24, 22, 12, 3, bark, rng)
        return p.build()
    }

    private fun standingStone(rng: Random): Sprite {
        val p = Painter(40, 72)
        for (y in 10 until 72) {
            val halfWidth = (13 - (y - 10) * 0.02f).toInt()
            for (x in (20 - halfWidth)..(20 + halfWidth)) {
                val n = Textures.valueNoise(x * 0.2f, y * 0.2f, 5)
                p.put(x, y, jitter(scale(0x5B5648, 0.7f + n * 0.5f), rng, 16))
            }
        }
        p.rect(15, 26, 25, 28, 0x2A2620, rng, 8)
        p.rect(19, 26, 21, 44, 0x2A2620, rng, 8)
        return p.build()
    }

    private fun grave(rng: Random): Sprite {
        val p = Painter(36, 44)
        p.ellipse(18, 16, 12, 14, 0x4C4638, rng)
        p.rect(6, 16, 30, 42, 0x4C4638, rng)
        p.rect(11, 12, 25, 14, 0x24211B, rng, 8)
        p.rect(11, 20, 25, 22, 0x24211B, rng, 8)
        p.rect(11, 28, 22, 30, 0x24211B, rng, 8)
        return p.build()
    }

    /** The blade in your right hand, seen down its own length. */
    private fun heldSword(rng: Random): Sprite {
        val p = Painter(96, 128)
        val steel = 0x9AA1A6
        // blade running up and to the left, foreshortened
        for (i in 0 until 120) {
            val t = i / 120f
            val x = (74 - t * 58).toInt()
            val y = (108 - t * 104).toInt()
            val halfWidth = (7 - t * 4).toInt().coerceAtLeast(2)
            for (dx in -halfWidth..halfWidth) {
                val edge = abs(dx).toFloat() / halfWidth
                val shade = if (dx < 0) 1.15f - edge * 0.3f else 0.62f + edge * 0.15f
                p.put(x + dx, y, jitter(scale(steel, shade), rng, 12))
            }
        }
        // crossguard, grip and gauntlet
        p.limb(62, 112, 92, 100, 7, 0x6B5A34, rng)
        p.limb(74, 116, 88, 126, 9, 0x3E3225, rng)
        p.ellipse(80, 122, 14, 11, 0x4A3B2A, rng)
        p.ellipse(84, 118, 5, 4, 0x5A4833, rng)
        return p.build()
    }

    /** The torch in your left hand: the only honest light in the vault. */
    private fun heldTorch(rng: Random): Sprite {
        val p = Painter(72, 128)
        p.limb(30, 127, 38, 62, 11, 0x3A2C1D, rng)
        p.rect(24, 54, 46, 66, 0x4A3722, rng)
        for (y in 4 until 58) {
            val spread = ((58 - y) * 0.30f).toInt() + 3
            for (x in (35 - spread)..(35 + spread)) {
                val n = Textures.valueNoise(x * 0.35f, y * 0.28f, 13)
                if (n < 0.38f) continue
                val hot = ((58 - y) / 54f).coerceIn(0f, 1f)
                val color = when {
                    hot > 0.72f -> 0xF6DA96.toInt()
                    hot > 0.45f -> 0xE8A93C.toInt()
                    hot > 0.22f -> 0xC8702A.toInt()
                    else -> 0x8C3A20
                }
                p.put(x, y, jitter(color, rng, 30))
            }
        }
        return p.build()
    }

    private fun brazier(rng: Random): Sprite {
        val p = Painter(32, 64)
        p.rect(14, 30, 18, 63, 0x3A2E1F, rng)
        p.ellipse(16, 28, 10, 5, 0x5A4A2E, rng)
        for (y in 4 until 28) {
            val spread = ((28 - y) * 0.35f).toInt() + 2
            for (x in (16 - spread)..(16 + spread)) {
                val n = Textures.valueNoise(x * 0.4f, y * 0.3f, 3)
                if (n < 0.35f) continue
                val hot = ((28 - y) / 24f).coerceIn(0f, 1f)
                val color = if (hot > 0.6f) 0xF2C55A.toInt() else if (hot > 0.3f) 0xD98A2B.toInt() else 0xA33B28
                p.put(x, y, jitter(color, rng, 26))
            }
        }
        return p.build()
    }

    /** A burial urn: clay, lidded, holding what the living left with the dead. */
    private fun urn(rng: Random): Sprite {
        val p = Painter(28, 36)
        val clay = 0x6B5A3E
        p.ellipse(14, 24, 9, 10, clay, rng)
        p.rect(9, 10, 19, 16, clay, rng)
        p.ellipse(14, 10, 5, 3, 0x2A231A, rng)
        p.limb(4, 20, 9, 17, 2, clay, rng)
        p.limb(24, 20, 19, 17, 2, clay, rng)
        p.rect(8, 18, 20, 20, 0x4C3E28, rng, 8)
        return p.build()
    }

    /** A dropped thing: one humble bundle looks much like another on the floor. */
    private fun itemBundle(rng: Random): Sprite {
        val p = Painter(26, 18)
        val cloth = 0x5C4A34
        p.ellipse(13, 11, 9, 6, cloth, rng)
        p.rect(11, 4, 15, 8, scale(cloth, 0.85f), rng)
        p.limb(4, 12, 22, 9, 2, 0x3E3225, rng)
        p.rect(12, 2, 14, 5, 0x8C6F3A, rng, 10)
        return p.build()
    }

    /** A warband's shelter: stained canvas over a dark mouth. */
    private fun tent(rng: Random): Sprite {
        val p = Painter(56, 52)
        val canvas = 0x5C5140
        // two sloping faces meeting at a ridge
        for (i in 0 until 40) {
            val t = i / 40f
            val halfWidth = (3 + t * 23).toInt()
            for (dx in -halfWidth..halfWidth) {
                val shade = 0.6f + 0.55f * (1f - abs(dx).toFloat() / (halfWidth + 1))
                p.put(28 + dx, 8 + (40 * t).toInt(), jitter(scale(canvas, shade), rng, 16))
            }
        }
        // the dark mouth, and the poles that hold the cloth up
        p.rect(22, 36, 34, 50, 0x1A150F, rng, 6)
        p.limb(6, 50, 28, 8, 2, scale(canvas, 0.8f), rng)
        p.limb(50, 50, 28, 8, 2, scale(canvas, 0.85f), rng)
        p.limb(28, 8, 28, 2, 2, 0x3E3225, rng)
        return p.build()
    }

    /** A living pine: dark boughs stacked to a point, the forest's own roof. */
    private fun pine(rng: Random): Sprite {
        val p = Painter(56, 110)
        val bark = 0x33291C
        p.limb(28, 109, 28, 58, 7, bark, rng)
        // stacked cones of boughs, darker toward the heartwood
        for (ring in 0 until 5) {
            val topY = 6 + ring * 18
            val halfWidth = (4 + ring * 7).toInt().coerceAtMost(24)
            val tone = 0.72f + ring * 0.09f
            for (y in topY until topY + 26) {
                val t = (y - topY) / 26f
                val half = (6 + halfWidth * t).toInt().coerceAtMost(26)
                for (x in (28 - half)..(28 + half)) {
                    val n = Textures.valueNoise(x * 0.3f, y * 0.3f, ring + 61)
                    val shade = if (x < 28) 0.75f else 1f
                    p.put(x, y, jitter(scale(0x24351E, tone * shade * (0.8f + n * 0.4f)), rng, 14))
                }
            }
        }
        return p.build()
    }

    /** A pile of stones raised over the dead: the barrow's marker in the open country. */
    private fun cairn(rng: Random): Sprite {
        val p = Painter(44, 54)
        val stone = 0x55503F
        p.ellipse(22, 44, 20, 9, scale(stone, 0.8f), rng)
        for (tier in 0 until 4) {
            val halfWidth = 16 - tier * 4
            val y0 = 38 - tier * 9
            for (y in (y0 - 9)..y0) {
                for (x in (22 - halfWidth)..(22 + halfWidth)) {
                    val n = Textures.valueNoise(x * 0.4f, y * 0.4f, 67)
                    p.put(x, y, jitter(scale(stone, 0.7f + n * 0.55f), rng, 16))
                }
            }
        }
        return p.build()
    }

    /** A door cut into a hillside: linteled, dark, and shut on the living. */
    private fun hilldoor(rng: Random): Sprite {
        val p = Painter(52, 74)
        val turf = 0x3E3A28
        // the mound the door is cut into
        for (y in 0 until 74) {
            val halfWidth = ((y.toFloat() / 74f) * 26).toInt().coerceAtLeast(10)
            for (x in (26 - halfWidth)..(26 + halfWidth)) {
                val n = Textures.valueNoise(x * 0.2f, y * 0.2f, 71)
                p.put(x, y, jitter(scale(turf, 0.7f + n * 0.5f), rng, 14))
            }
        }
        // the door itself: a dark trapezoid with a stone frame
        for (y in 18 until 72) {
            val t = (y - 18) / 54f
            val half = (4 + t * 9).toInt()
            for (x in (26 - half)..(26 + half)) {
                p.put(x, y, jitter(0x120E0A, rng, 6))
            }
        }
        for (y in 14 until 72) {
            val t = (y - 14) / 58f
            val half = (5 + t * 10).toInt()
            p.put(26 - half, y, jitter(0x4C4638, rng, 10))
            p.put(26 + half, y, jitter(0x4C4638, rng, 10))
        }
        for (x in (26 - 16)..(26 + 16)) p.put(x, 15, jitter(0x5A5442, rng, 10))
        // brass studs of the sealing, long since sprung
        p.put(22, 30, 0xC8952F)
        p.put(30, 30, 0x8C6F3A)
        p.put(26, 40, 0xA3812F)
        return p.build()
    }

    /** A warband's fire in the open: stones, logs, and a lean of flame. */
    private fun watchfire(rng: Random): Sprite {
        val p = Painter(40, 48)
        val ring = 0x4C4438
        p.ellipse(20, 42, 16, 5, ring, rng)
        for (i in 0 until 9) {
            val a = i * 6.28f / 9
            p.put(20 + (kotlin.math.cos(a) * 14).toInt(), 42 + (kotlin.math.sin(a) * 4).toInt(), jitter(0x5C5444, rng, 12))
        }
        p.limb(12, 42, 28, 38, 4, 0x2E261A, rng)
        p.limb(12, 38, 28, 42, 4, 0x332A1D, rng)
        for (y in 4 until 40) {
            val spread = ((40 - y) * 0.28f).toInt() + 2
            for (x in (20 - spread)..(20 + spread)) {
                val n = Textures.valueNoise(x * 0.4f, y * 0.3f, 73)
                if (n < 0.34f) continue
                val hot = ((40 - y) / 36f).coerceIn(0f, 1f)
                val color = when {
                    hot > 0.7f -> 0xF6DA96.toInt()
                    hot > 0.42f -> 0xE8A93C.toInt()
                    hot > 0.2f -> 0xC8702A.toInt()
                    else -> 0x8C3A20
                }
                p.put(x, y, jitter(color, rng, 26))
            }
        }
        return p.build()
    }

    /** A soul on the road: hooded, robed, staff in hand, asking nothing. */
    private fun pilgrim(rng: Random): Sprite {
        val p = Painter(44, 84)
        val robe = 0x5C5140
        // a hooded robe, hem swaying
        for (y in 20 until 84) {
            val t = (y - 20) / 64f
            val half = (7 + t * 9).toInt()
            for (x in (22 - half)..(22 + half)) {
                val n = Textures.valueNoise(x * 0.3f, y * 0.2f, 79)
                val shade = if (x < 22) 0.72f else 1f
                p.put(x, y, jitter(scale(robe, shade * (0.8f + n * 0.35f)), rng, 14))
            }
        }
        // hood and the shadow within it
        p.ellipse(22, 16, 8, 9, scale(robe, 1.08f), rng)
        p.ellipse(22, 18, 5, 6, 0x181410, rng, 8)
        // belt and satchel
        p.rect(15, 38, 29, 41, 0x3E3225, rng, 10)
        p.ellipse(30, 46, 5, 6, 0x4C3E28, rng)
        // the staff, and a hand on it
        p.limb(34, 10, 36, 82, 3, 0x33291C, rng)
        p.limb(27, 44, 34, 42, 4, scale(robe, 1.05f), rng)
        return p.build()
    }

    /** What is left of anyone: the same sprawled shape whatever they were. */
    private fun corpse(rng: Random): Sprite {
        val p = Painter(60, 26)
        val flesh = 0x7A6A52
        p.ellipse(30, 16, 20, 7, flesh, rng)
        p.ellipse(52, 12, 5, 4, scale(flesh, 1.05f), rng)
        p.limb(14, 18, 4, 22, 4, scale(flesh, 0.9f), rng)
        p.limb(30, 20, 34, 25, 4, scale(flesh, 0.9f), rng)
        p.limb(40, 20, 48, 24, 4, scale(flesh, 0.85f), rng)
        for (i in 0 until 10) {
            val x = 12 + rng.nextInt(36)
            val y = 12 + rng.nextInt(10)
            p.rect(x, y, x + 2, y + 1, 0x4A231A, rng, 10)
        }
        return p.build()
    }

    // ------------------------------------------------- the arms of the province
    // One shape per weapon archetype, painted once at startup like every other
    // sprite. Blades, heads and shafts are painted in metal gray so a
    // material's tint can take them; grips, bindings and cord keep their hue.

    /** Metal in gray: tinting multiplies it by the material's hue. */
    private fun metal(shade: Float): Int {
        val v = (240f * shade).roundToInt().coerceIn(0, 255)
        return (v shl 16) or (v shl 8) or v
    }

    /** A blade or point: vertical, tip at the top, tapering, with an optional curve. */
    private fun bladeUp(p: Painter, rng: Random, cx: Int, yTip: Int, yBase: Int, halfW: Int, curve: Float = 0f) {
        val len = (yBase - yTip).coerceAtLeast(3)
        for (i in 0..len) {
            val t = i.toFloat() / len
            val y = yTip + i
            val w = ((halfW * (0.25f + 0.75f * t)).roundToInt()).coerceAtLeast(1)
            val bend = (curve * (1f - t) * 22f).roundToInt()
            for (dx in -w..w) {
                val edge = abs(dx).toFloat() / w
                val shade = if (dx < 0) 1.08f - edge * 0.22f else 0.62f + edge * 0.25f
                p.put(cx + dx + bend, y, jitter(metal(shade), rng, 8))
            }
        }
    }

    /** A wooden pole rising between the given rows. */
    private fun haft(p: Painter, rng: Random, cx: Int, yTop: Int, yBottom: Int, half: Int = 2) {
        for (y in yTop..yBottom) for (dx in -half..half) p.putFixed(cx + dx, y, jitter(0x6B4A2C, rng, 14))
    }

    private fun wrap(p: Painter, rng: Random, cx: Int, y: Int, half: Int = 3) {
        for (dx in -half..half) p.putFixed(cx + dx, y, jitter(0x3E2C1C, rng, 8))
    }

    private fun crossBar(p: Painter, rng: Random, cx: Int, y: Int, half: Int, thick: Int = 4) {
        for (dy in 0 until thick) for (dx in -half..half) p.putFixed(cx + dx, y + dy, jitter(0x54462C, rng, 12))
    }

    private fun grip(p: Painter, rng: Random, cx: Int, y0: Int, len: Int) {
        for (i in 0 until len) for (dx in -2..2) p.putFixed(cx + dx, y0 + i, jitter(0x4A3320, rng, 10))
    }

    private fun pommelBall(p: Painter, rng: Random, cx: Int, cy: Int, r: Int) {
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= r * r) {
            p.put(cx + dx, cy + dy, jitter(metal(0.95f), rng, 8))
        }
    }

    private fun ballHead(p: Painter, rng: Random, cx: Int, cy: Int, r: Int, spikes: Int = 0, ribs: Int = 0) {
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= r * r) {
            p.put(cx + dx, cy + dy, jitter(metal(1.0f - dy.toFloat() / r * 0.3f), rng, 8))
        }
        if (spikes > 0) {
            for (s in 0 until spikes) {
                val ang = Math.PI * 2.0 * s / spikes
                val ex = (cx + kotlin.math.cos(ang) * (r + 7)).roundToInt()
                val ey = (cy + kotlin.math.sin(ang) * (r + 7)).roundToInt()
                for (i in 0..7) {
                    val x = cx + ((ex - cx).toDouble() * i / 7.0).roundToInt()
                    val y = cy + ((ey - cy).toDouble() * i / 7.0).roundToInt()
                    p.put(x, y, jitter(metal(0.9f), rng, 6))
                }
            }
        }
        if (ribs > 0) {
            for (k in 0 until ribs) {
                val dx = -r + k * (2 * r) / (ribs - 1).coerceAtLeast(1)
                for (dy in -r..r) p.put(cx + dx, cy + dy, jitter(metal(0.7f), rng, 6))
            }
        }
    }

    private fun hammerHead(p: Painter, rng: Random, cx: Int, cy: Int, half: Int, halfY: Int) {
        for (dy in -halfY..halfY) for (dx in -half..half) {
            val face = if (abs(dx) == half) 0.75f else 1.0f
            p.put(cx + dx, cy + dy, jitter(metal(face), rng, 8))
        }
    }

    private fun axeHead(p: Painter, rng: Random, cx: Int, cy: Int, reach: Int, span: Int, mirror: Boolean = false) {
        for (i in 0..reach) {
            val edge = i.toFloat() / reach
            val drop = (span * (0.35f + 0.65f * edge)).roundToInt().coerceAtLeast(1)
            for (dy in -drop..drop) {
                p.put(cx + i, cy + dy, jitter(metal(1.05f - edge * 0.4f), rng, 10))
                if (mirror) p.put(cx - i, cy + dy, jitter(metal(1.05f - edge * 0.4f), rng, 10))
            }
        }
    }

    /** A stroke of fixed hue: leather straps, cords, the wooden bones of a haft. */
    private fun fixedLimb(p: Painter, rng: Random, x0: Int, y0: Int, x1: Int, y1: Int, thick: Int, color: Int) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) {
            val x = x0 + (x1 - x0) * i / steps
            val y = y0 + (y1 - y0) * i / steps
            for (ty in -thick / 2..thick / 2) for (tx in -thick / 2..thick / 2) {
                p.putFixed(x + tx, y + ty, jitter(color, rng, 12))
            }
        }
    }

    // --- the straight and curved blades -------------------------------------

    private fun wBlade(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 76, 5)
        crossBar(p, rng, 48, 78, 14)
        grip(p, rng, 48, 82, 26)
        pommelBall(p, rng, 48, 112, 4)
        return p.build()
    }

    /** The one-hand sword family: point, cross, grip, pommel — each its own proportions. */
    private fun swordOf(
        rng: Random, yTip: Int, yBase: Int, halfW: Int, guardHalf: Int, curve: Float, gripLen: Int,
        fuller: Boolean = false, knuckle: Boolean = false, squareGuard: Boolean = false
    ): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, yTip, yBase, halfW, curve)
        if (fuller) for (y in yTip + 6 until yBase - 2) p.put(48, y, jitter(metal(0.6f), rng, 6))
        val gy = yBase + 2
        if (squareGuard) {
            for (dy in 0 until 3) for (dx in -(halfW + 3)..(halfW + 3)) {
                p.putFixed(48 + dx, gy + dy, jitter(0x54462C, rng, 10))
            }
        } else {
            crossBar(p, rng, 48, gy, guardHalf)
        }
        if (knuckle) for (i in 0 until gripLen) p.putFixed(48 + guardHalf + 3, gy + i, jitter(0x54462C, rng, 10))
        grip(p, rng, 48, gy + 4, gripLen)
        pommelBall(p, rng, 48, gy + 6 + gripLen, 4)
        return p.build()
    }

    private fun wShortsword(rng: Random) = swordOf(rng, 30, 70, 4, 10, 0f, 20)
    private fun wArmingSword(rng: Random) = swordOf(rng, 12, 74, 5, 15, 0f, 24)
    private fun wUlfberht(rng: Random) = swordOf(rng, 8, 76, 5, 15, 0f, 26, fuller = true)
    private fun wFalchion(rng: Random) = swordOf(rng, 16, 68, 7, 10, 0.9f, 22)
    private fun wMesser(rng: Random) = swordOf(rng, 18, 70, 5, 9, 0.5f, 22, squareGuard = true)
    private fun wSabre(rng: Random) = swordOf(rng, 12, 72, 4, 9, 1.4f, 24, knuckle = true)
    private fun wScimitar(rng: Random) = swordOf(rng, 10, 68, 4, 8, 2.0f, 22)
    private fun wEstoc(rng: Random) = swordOf(rng, 6, 80, 3, 12, 0f, 26)
    private fun wKatana(rng: Random) = swordOf(rng, 8, 78, 4, 6, 0.8f, 34, squareGuard = true)

    private fun wRapier(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 8, 76, 2)
        // the swept guard: two rings of steel about the hand
        for (dy in -13..13) for (dx in -14..14) {
            val d = dx * dx + dy * dy
            if (d in 130..168) p.putFixed(48 + dx, 78 + dy, jitter(0x54462C, rng, 10))
        }
        grip(p, rng, 48, 92, 26)
        pommelBall(p, rng, 48, 122, 4)
        return p.build()
    }

    // --- the axe family -------------------------------------------------------

    private fun wAxe(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 30, 114, 2)
        axeHead(p, rng, 50, 34, 22, 12)
        wrap(p, rng, 48, 46)
        return p.build()
    }

    private fun wBattleAxe(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 26, 116, 3)
        axeHead(p, rng, 51, 34, 28, 15)
        wrap(p, rng, 48, 50)
        wrap(p, rng, 48, 104)
        return p.build()
    }

    private fun wDaneAxe(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 44, 24, 118, 2)
        axeHead(p, rng, 46, 40, 34, 10)
        wrap(p, rng, 44, 54)
        return p.build()
    }

    // --- the blunt family -----------------------------------------------------

    private fun wMace(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 46, 110, 2)
        ballHead(p, rng, 48, 36, 11, ribs = 3)
        wrap(p, rng, 48, 50)
        return p.build()
    }

    private fun wFlangedMace(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 48, 112, 2)
        for (k in 0 until 4) {
            val y0 = 22 + k * 7
            val spread = 13 - k * 2
            for (dx in -13..13) {
                val x = 48 + dx * spread / 13
                for (dy in 0..4) p.put(x, y0 + dy, jitter(metal(if (abs(dx) > 8) 1.05f else 0.8f), rng, 8))
            }
        }
        wrap(p, rng, 48, 52)
        return p.build()
    }

    private fun wMorningStar(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 48, 112, 2)
        ballHead(p, rng, 48, 36, 10, spikes = 12)
        wrap(p, rng, 48, 52)
        return p.build()
    }

    private fun wShestopyor(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 50, 112, 2)
        // six flanges: three bars crossed through the head
        for (slope in intArrayOf(0, 2, -2)) {
            for (dx in -11..11) {
                val dy = slope * dx / 4
                for (t in -1..1) p.put(48 + dx, 36 + dy + t, jitter(metal(1.0f), rng, 8))
            }
        }
        for (dy in -4..4) for (dx in -4..4) if (dx * dx + dy * dy <= 16) {
            p.put(48 + dx, 36 + dy, jitter(metal(0.9f), rng, 8))
        }
        wrap(p, rng, 48, 54)
        return p.build()
    }

    private fun wWarHammer(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 44, 114, 2)
        hammerHead(p, rng, 48, 29, 20, 6)
        wrap(p, rng, 48, 48)
        return p.build()
    }

    private fun wBecDeCorbin(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 40, 116, 2)
        // the beak: a curved pick drooping from the head of the haft
        for (i in 0..18) {
            val x = 48 + i
            val y = 30 + i * i / 20
            for (t in -2..2) p.put(x, y + t, jitter(metal(1.0f - i / 40f), rng, 8))
        }
        for (dy in -8..0) for (dx in -14..-6) p.put(48 + dx, 34 + dy, jitter(metal(0.85f), rng, 8))
        wrap(p, rng, 48, 46)
        return p.build()
    }

    private fun wHorsemanPick(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 44, 112, 2)
        for (i in 0..22) {
            val x = 48 + i
            val y = 36 + i * i / 30
            for (t in -1..1) p.put(x, y + t, jitter(metal(1.05f - i / 50f), rng, 6))
        }
        wrap(p, rng, 48, 48)
        return p.build()
    }

    private fun wClub(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (i in 0..94) {
            val t = i / 94f
            val w = (3 + t * 9).roundToInt()
            for (dx in -w..w) p.put(44 + dx, 118 - i, jitter(metal(1.0f - t * 0.18f), rng, 12))
        }
        return p.build()
    }

    private fun wBludgeon(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (i in 0..96) {
            val t = i / 96f
            val w = (2 + t * 7).roundToInt()
            for (dx in -w..w) p.put(46 + dx, 118 - i, jitter(metal(1.0f - t * 0.2f), rng, 10))
        }
        for (b in 0 until 3) {
            val y = 34 + b * 8
            for (dx in -9..9) p.putFixed(46 + dx, y, jitter(0x3E2C1C, rng, 8))
        }
        return p.build()
    }

    private fun wFlail(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 40, 78, 2)
        // the chain: linked steel swinging free
        for (i in 0..22) {
            val x = 48 + i / 2
            val y = 80 + i
            p.put(x, y, jitter(metal(1.0f), rng, 8))
            if (i % 3 == 0) p.put(x + 1, y, jitter(metal(0.7f), rng, 8))
        }
        ballHead(p, rng, 62, 110, 8, spikes = 6)
        return p.build()
    }

    private fun wQuarterstaff(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (y in 12..118) for (dx in -3..3) {
            p.put(48 + dx, y, jitter(metal(if (dx < 0) 1.0f else 0.72f), rng, 10))
        }
        wrap(p, rng, 48, 26, 4)
        wrap(p, rng, 48, 62, 4)
        wrap(p, rng, 48, 104, 4)
        return p.build()
    }

    // --- the bow --------------------------------------------------------------

    private fun wBow(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (i in 0..96) {
            val t = i / 96f
            val bend = (30f * 4f * t * (1f - t)).roundToInt()
            val x = 30 + bend
            for (dx in 0..3) p.put(x + dx, 14 + i, jitter(metal(1.0f - 0.3f * t), rng, 10))
        }
        // the string: a cord drawn taut down the belly
        for (y in 16..110) p.putFixed(32, y, jitter(0xC8BC9A, rng, 6))
        return p.build()
    }

    // --- the polearm family -----------------------------------------------------

    private fun wSpear(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 8, 40, 5)
        haft(p, rng, 48, 42, 118, 2)
        wrap(p, rng, 48, 46, 4)
        wrap(p, rng, 48, 54, 4)
        return p.build()
    }

    private fun wPike(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 36, 3)
        haft(p, rng, 48, 38, 118, 2)
        wrap(p, rng, 48, 42)
        return p.build()
    }

    private fun wLance(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (i in 0..22) {
            val w = ((5 * (1f - i / 24f)).roundToInt()).coerceAtLeast(1)
            for (dx in -w..w) p.put(48 + dx, 4 + i, jitter(metal(1.05f - i / 40f), rng, 8))
        }
        haft(p, rng, 48, 28, 118, 3)
        wrap(p, rng, 48, 32, 4)
        return p.build()
    }

    private fun wHarpoon(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 40, 3)
        for (i in 0..9) {
            p.put(48 - i, 24 + i, jitter(metal(1.0f), rng, 8))
            p.put(48 + i, 24 + i, jitter(metal(1.0f), rng, 8))
        }
        haft(p, rng, 48, 42, 114, 2)
        return p.build()
    }

    private fun wTrident(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 12, 44, 3)
        bladeUp(p, rng, 34, 20, 44, 2)
        bladeUp(p, rng, 62, 20, 44, 2)
        for (dx in -14..14) p.put(48 + dx, 46, jitter(metal(1.0f), rng, 8))
        haft(p, rng, 48, 48, 118, 2)
        return p.build()
    }

    private fun wBill(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 30, 3)
        for (i in 0..16) {
            val x = 50 + i
            val y = 26 + i * i / 24
            for (t in -2..2) p.put(x, y + t, jitter(metal(1.0f - i / 40f), rng, 8))
        }
        haft(p, rng, 48, 34, 118, 2)
        return p.build()
    }

    private fun wGlaive(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 12, 48, 6, curve = 0.4f)
        haft(p, rng, 48, 50, 118, 2)
        wrap(p, rng, 48, 54)
        return p.build()
    }

    private fun wGuandao(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 14, 48, 7, curve = -1.0f)
        for (i in 0..10) p.putFixed(48, 50 + i, jitter(0x8A2A22, rng, 10))
        haft(p, rng, 48, 52, 118, 2)
        return p.build()
    }

    private fun wPudao(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 12, 46, 6)
        haft(p, rng, 48, 48, 118, 2)
        wrap(p, rng, 48, 52)
        return p.build()
    }

    private fun wSovnya(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 12, 46, 4, curve = 1.2f)
        haft(p, rng, 48, 48, 118, 2)
        return p.build()
    }

    private fun wNaginata(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 16, 46, 4)
        haft(p, rng, 48, 48, 118, 2)
        wrap(p, rng, 48, 52)
        wrap(p, rng, 48, 108)
        return p.build()
    }

    private fun wBardiche(rng: Random): Sprite {
        val p = Painter(96, 128)
        axeHead(p, rng, 50, 44, 30, 9)
        axeHead(p, rng, 50, 58, 24, 7)
        haft(p, rng, 48, 42, 118, 2)
        return p.build()
    }

    private fun wWarScythe(rng: Random): Sprite {
        val p = Painter(96, 128)
        haft(p, rng, 48, 22, 118, 2)
        for (i in 0..34) {
            val x = 48 - i
            val y = 20 + i * i / 60
            for (t in -2..2) p.put(x, y + t, jitter(metal(1.05f - i / 70f), rng, 8))
        }
        wrap(p, rng, 48, 26)
        return p.build()
    }

    private fun wPoleaxe(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 38, 3)
        axeHead(p, rng, 50, 44, 18, 10)
        for (dy in -7..3) for (dx in -16..-6) p.put(48 + dx, 44 + dy, jitter(metal(0.85f), rng, 8))
        haft(p, rng, 48, 48, 118, 2)
        return p.build()
    }

    private fun wHalberd(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 10, 40, 3)
        axeHead(p, rng, 50, 46, 20, 12)
        for (i in 0..12) p.put(48 - i, 44 - i / 2, jitter(metal(0.9f), rng, 8))
        haft(p, rng, 48, 50, 118, 2)
        return p.build()
    }

    private fun wPlancon(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (y in 22..48) {
            val w = 8 - (y - 22) * 5 / 26
            for (dx in -w..w) p.put(48 + dx, y, jitter(metal(1.0f), rng, 10))
        }
        for (y in 48..74) {
            p.put(42, y, jitter(metal(0.9f), rng, 8))
            p.put(54, y, jitter(metal(0.9f), rng, 8))
        }
        haft(p, rng, 48, 48, 118, 3)
        return p.build()
    }

    private fun wDagger(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 34, 64, 4)
        crossBar(p, rng, 48, 66, 8, 3)
        grip(p, rng, 48, 70, 16)
        pommelBall(p, rng, 48, 90, 3)
        return p.build()
    }

    private fun wKnife(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 42, 66, 3)
        fixedLimb(p, rng, 48, 66, 48, 86, 5, 0x5A4326)
        return p.build()
    }

    // --- the shieldwright's shapes ----------------------------------------------

    private const val BOARD_FACE = 0x9A9A9A
    private const val BOARD_RIM = 0x646464
    private const val BOARD_BOSS = 0x767676

    /** A round board: rim, face, and the boss at the heart of it. */
    private fun roundBoard(w: Int, h: Int, rng: Random, rimWidth: Int, boss: Boolean): Sprite {
        val p = Painter(w, h)
        val rx = w / 2 - 2
        val ry = h / 2 - 2
        p.ellipse(w / 2, h / 2, rx, ry, BOARD_RIM, rng)
        p.ellipse(
            w / 2, h / 2,
            (rx - rimWidth).coerceAtLeast(1), (ry - rimWidth).coerceAtLeast(1),
            BOARD_FACE, rng
        )
        if (boss) p.ellipse(w / 2, h / 2, (w / 8).coerceAtLeast(3), (h / 8).coerceAtLeast(3), BOARD_BOSS, rng)
        return p.build()
    }

    /** A shaped board: a half-width per row, rimmed along both running edges. */
    private fun shapedBoard(w: Int, h: Int, rng: Random, half: (Int) -> Int): Sprite {
        val p = Painter(w, h)
        for (y in 0 until h) {
            val hw = half(y)
            if (hw <= 0) continue
            p.rect(w / 2 - hw, y, w / 2 + hw, y, BOARD_FACE, rng, 16)
            p.rect(w / 2 - hw, y, w / 2 - hw + 1, y, BOARD_RIM, rng, 10)
            p.rect(w / 2 + hw - 1, y, w / 2 + hw, y, BOARD_RIM, rng, 10)
        }
        return p.build()
    }

    private fun circleHalf(cx: Int, cy: Int, r: Int): (Int) -> Int = { y ->
        val dy = y - cy
        val inner = r * r - dy * dy
        if (inner > 0) kotlin.math.sqrt(inner.toFloat()).toInt() else 0
    }

    private fun sBuckler(rng: Random): Sprite = roundBoard(22, 24, rng, 2, true)
    private fun sRound(rng: Random): Sprite = roundBoard(40, 42, rng, 3, true)
    private fun sTarge(rng: Random): Sprite = roundBoard(30, 34, rng, 3, true)
    private fun sRondache(rng: Random): Sprite = roundBoard(46, 46, rng, 4, false)
    private fun sRotella(rng: Random): Sprite = roundBoard(40, 40, rng, 3, true)

    private fun sHeater(rng: Random): Sprite = shapedBoard(38, 44, rng) { y ->
        if (y < 26) 17 else (17 * (44 - y) / 18)
    }

    private fun sKite(rng: Random): Sprite {
        val top = circleHalf(17, 17, 15)
        return shapedBoard(34, 54, rng) { y ->
            if (y < 18) top(y) else ((15 * (54 - y)) / 36)
        }
    }

    private fun sAlmond(rng: Random): Sprite = shapedBoard(32, 48, rng) { y ->
        val t = (y - 24f) / 24f
        (14 * kotlin.math.sqrt((1f - t * t).coerceAtLeast(0f))).toInt()
    }

    private fun sHungarian(rng: Random): Sprite = shapedBoard(26, 50, rng) { y ->
        when {
            y < 9 -> (2 + y).coerceAtMost(11)
            y < 34 -> 11
            else -> (11 * (50 - y) / 16).coerceAtLeast(1)
        }
    }

    private fun sPavise(rng: Random): Sprite = shapedBoard(52, 58, rng) { y ->
        when {
            y < 4 -> (2 + y * 5).coerceAtMost(24)
            y > 53 -> (2 + (57 - y) * 5).coerceAtMost(24)
            else -> 24
        }
    }

    private fun sTower(rng: Random): Sprite = shapedBoard(44, 60, rng) { y ->
        when {
            y < 6 -> (6 + y * 2).coerceAtMost(19)
            y > 53 -> (6 + (59 - y) * 2).coerceAtMost(19)
            else -> 19
        }
    }

    // --- the armswright of ranged pieces ----------------------------------------

    /** A crossbow: steel prod crosswise, wooden tiller below, cord drawn to the nut. */
    private fun wCrossbow(rng: Random): Sprite {
        val p = Painter(96, 128)
        fixedLimb(p, rng, 48, 44, 48, 112, 7, 0x5A4326)
        p.limb(12, 32, 84, 32, 5, 0x8A8A8A, rng)
        fixedLimb(p, rng, 12, 32, 48, 54, 1, 0xD8D0B8)
        fixedLimb(p, rng, 84, 32, 48, 54, 1, 0xD8D0B8)
        p.rect(44, 36, 52, 44, 0x9A9A9A, rng, 12)
        fixedLimb(p, rng, 52, 62, 60, 74, 2, 0x3A2C18)
        return p.build()
    }

    /** An arquebus: a long barrel over a full stock, the serpentine reaching for the pan. */
    private fun wArquebus(rng: Random): Sprite {
        val p = Painter(96, 128)
        p.limb(30, 14, 56, 74, 5, 0x8A8A8A, rng)
        fixedLimb(p, rng, 56, 74, 66, 116, 9, 0x5A4326)
        fixedLimb(p, rng, 40, 46, 52, 62, 3, 0x3A2C18)
        p.ellipse(33, 14, 3, 3, 0x6A6A6A, rng)
        return p.build()
    }

    /** A hand cannon: a thick tube lashed to a short stock, muzzle flared. */
    private fun wHandCannon(rng: Random): Sprite {
        val p = Painter(96, 128)
        p.limb(36, 20, 58, 72, 9, 0x7A7A7A, rng)
        p.limb(36, 20, 40, 28, 12, 0x6A6A6A, rng)
        fixedLimb(p, rng, 58, 72, 62, 106, 8, 0x5A4326)
        for (i in 0 until 5) {
            val t = i / 4f
            val x = 40 + ((58 - 40) * t).toInt()
            val y = 32 + ((62 - 32) * t).toInt()
            p.rect(x - 6, y, x + 6, y + 1, 0x2A2016, rng, 8)
        }
        return p.build()
    }

    /** The three-eye: three short barrels fanned from one lashed stock. */
    private fun wThreeEye(rng: Random): Sprite {
        val p = Painter(96, 128)
        p.limb(28, 26, 52, 74, 7, 0x7A7A7A, rng)
        p.limb(38, 22, 58, 72, 7, 0x7A7A7A, rng)
        p.limb(48, 20, 62, 70, 7, 0x7A7A7A, rng)
        fixedLimb(p, rng, 54, 74, 62, 106, 8, 0x5A4326)
        return p.build()
    }

    /** A throwing knife: a broad balanced blade, no guard to catch the wrist. */
    private fun wThrowingKnife(rng: Random): Sprite {
        val p = Painter(96, 128)
        bladeUp(p, rng, 48, 36, 64, 5)
        fixedLimb(p, rng, 48, 64, 48, 84, 6, 0x5A4326)
        return p.build()
    }

    /** A francisca: a short haft and a bearded head, made to spin. */
    private fun wFrancisca(rng: Random): Sprite {
        val p = Painter(96, 128)
        fixedLimb(p, rng, 48, 34, 48, 96, 5, 0x5A4326)
        p.limb(48, 40, 76, 46, 11, 0x9A9A9A, rng)
        p.limb(48, 56, 76, 50, 8, 0x9A9A9A, rng)
        return p.build()
    }

    /** A chakram: a flat ring of war-steel, rim to rim. */
    private fun wChakram(rng: Random): Sprite {
        val p = Painter(96, 128)
        for (y in 0 until 128) for (x in 0 until 96) {
            val dx = (x - 48).toFloat()
            val dy = (y - 60).toFloat()
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d in 26f..34f) p.put(x, y, jitter(0x9A9A9A, rng, 18))
        }
        return p.build()
    }

    /** A shuriken: a four-pointed star around its riveted heart. */
    private fun wShuriken(rng: Random): Sprite {
        val p = Painter(96, 128)
        p.limb(48, 32, 48, 88, 7, 0x9A9A9A, rng)
        p.limb(20, 60, 76, 60, 7, 0x9A9A9A, rng)
        p.limb(28, 40, 68, 80, 6, 0x9A9A9A, rng)
        p.limb(68, 40, 28, 80, 6, 0x9A9A9A, rng)
        p.ellipse(48, 60, 5, 5, 0x6A6A6A, rng)
        return p.build()
    }

    /** A sling: two cords from the fingers to a pouch, hanging slack. */
    private fun wSling(rng: Random): Sprite {
        val p = Painter(64, 96)
        fixedLimb(p, rng, 20, 8, 30, 52, 2, 0x6B4E2E)
        fixedLimb(p, rng, 44, 8, 34, 52, 2, 0x6B4E2E)
        p.ellipse(32, 58, 9, 6, 0x5A4326, rng)
        p.rect(23, 6, 25, 12, 0x8A6A44, rng, 10)
        return p.build()
    }

    // --- the shot in the air -----------------------------------------------------

    /** An arrow, point right: fixed shaft and fletching, tintable head. */
    private fun pArrow(rng: Random): Sprite {
        val p = Painter(64, 16)
        p.rect(10, 7, 52, 8, 0x7A5A34, rng, 14)
        for (y in 0 until 6) {
            val reach = 20 - y * 3
            p.rect(10, 2 + y, 10 + reach / 3, 2 + y, 0xC9BC9C, rng, 10)
            p.rect(10, 13 - y, 10 + reach / 3, 13 - y, 0xC9BC9C, rng, 10)
        }
        for (x in 52 until 62) {
            val half = (62 - x) * 3 / 10
            p.rect(x, 8 - half, x, 7 + half, 0x9A9A9A, rng, 10)
        }
        return p.build()
    }

    /** A bolt, point right: thicker shaft, a heavier iron head. */
    private fun pBolt(rng: Random): Sprite {
        val p = Painter(56, 16)
        p.rect(8, 6, 42, 9, 0x6B4E2E, rng, 14)
        p.rect(8, 1, 14, 4, 0xC9BC9C, rng, 10)
        p.rect(8, 11, 14, 14, 0xC9BC9C, rng, 10)
        for (x in 42 until 54) {
            val half = (54 - x) * 4 / 12
            p.rect(x, 8 - half, x, 7 + half, 0x9A9A9A, rng, 10)
        }
        return p.build()
    }

    /** A ball: lead, iron, or stone — the tint names the metal. */
    private fun pBall(rng: Random): Sprite {
        val p = Painter(24, 24)
        p.ellipse(12, 12, 8, 8, 0x9A9A9A, rng, 12)
        return p.build()
    }

    /** A sling stone or clay bullet: a small rough sphere. */
    private fun pShot(rng: Random): Sprite {
        val p = Painter(16, 16)
        p.ellipse(8, 8, 5, 5, 0x8A8A8A, rng, 16)
        return p.build()
    }

    // --- the mapping and the tint ----------------------------------------------

    /** The picture of a weapon: every archetype its own shape. */
    fun forWeapon(archetype: ItemArchetype): Int = when (archetype) {
        ItemArchetype.BLADE -> W_BLADE
        ItemArchetype.AXE -> W_AXE
        ItemArchetype.MACE -> W_MACE
        ItemArchetype.SPEAR -> W_SPEAR
        ItemArchetype.DAGGER -> W_DAGGER
        ItemArchetype.BOW -> W_BOW
        ItemArchetype.CLUB -> W_CLUB
        ItemArchetype.BLUDGEON -> W_BLUDGEON
        ItemArchetype.FLAIL -> W_FLAIL
        ItemArchetype.FLANGED_MACE -> W_FLANGED_MACE
        ItemArchetype.MORNING_STAR -> W_MORNING_STAR
        ItemArchetype.WAR_HAMMER -> W_WAR_HAMMER
        ItemArchetype.SHESTOPYOR -> W_SHESTOPYOR
        ItemArchetype.QUARTERSTAFF -> W_QUARTERSTAFF
        ItemArchetype.SHORTSWORD -> W_SHORTSWORD
        ItemArchetype.FALCHION -> W_FALCHION
        ItemArchetype.MESSER -> W_MESSER
        ItemArchetype.SABRE -> W_SABRE
        ItemArchetype.SCIMITAR -> W_SCIMITAR
        ItemArchetype.ARMING_SWORD -> W_ARMING_SWORD
        ItemArchetype.ULFBERHT -> W_ULFBERHT
        ItemArchetype.BATTLE_AXE -> W_BATTLE_AXE
        ItemArchetype.KATANA -> W_KATANA
        ItemArchetype.BILL -> W_BILL
        ItemArchetype.DANE_AXE -> W_DANE_AXE
        ItemArchetype.GLAIVE -> W_GLAIVE
        ItemArchetype.GUANDAO -> W_GUANDAO
        ItemArchetype.PUDAO -> W_PUDAO
        ItemArchetype.SOVNYA -> W_SOVNYA
        ItemArchetype.NAGINATA -> W_NAGINATA
        ItemArchetype.BARDICHE -> W_BARDICHE
        ItemArchetype.WAR_SCYTHE -> W_WAR_SCYTHE
        ItemArchetype.KNIFE -> W_KNIFE
        ItemArchetype.RAPIER -> W_RAPIER
        ItemArchetype.HORSEMANS_PICK -> W_HORSEMANS_PICK
        ItemArchetype.ESTOC -> W_ESTOC
        ItemArchetype.PIKE -> W_PIKE
        ItemArchetype.POLEAXE -> W_POLEAXE
        ItemArchetype.HALBERD -> W_HALBERD
        ItemArchetype.HARPOON -> W_HARPOON
        ItemArchetype.TRIDENT -> W_TRIDENT
        ItemArchetype.BEC_DE_CORBIN -> W_BEC_DE_CORBIN
        ItemArchetype.LANCE -> W_LANCE
        ItemArchetype.PLANCON_A_PICOT -> W_PLANCON
        ItemArchetype.CROSSBOW -> W_CROSSBOW
        ItemArchetype.ARQUEBUS -> W_ARQUEBUS
        ItemArchetype.HAND_CANNON -> W_HAND_CANNON
        ItemArchetype.THREE_EYE_CANNON -> W_THREE_EYE
        ItemArchetype.SLING -> W_SLING
        ItemArchetype.THROWING_KNIFE -> W_THROWING_KNIFE
        ItemArchetype.FRANCISCA -> W_FRANCISCA
        ItemArchetype.CHAKRAM -> W_CHAKRAM
        ItemArchetype.SHURIKEN -> W_SHURIKEN
        else -> ITEM
    }

    /** The picture of a shield: every form its own board. */
    fun forShield(archetype: ItemArchetype): Int = when (archetype) {
        ItemArchetype.BUCKLER -> S_BUCKLER
        ItemArchetype.ROUND_SHIELD -> S_ROUND
        ItemArchetype.TARGE -> S_TARGE
        ItemArchetype.HEATER_SHIELD -> S_HEATER
        ItemArchetype.KITE_SHIELD -> S_KITE
        ItemArchetype.ALMOND_SHIELD -> S_ALMOND
        ItemArchetype.HUNGARIAN_SHIELD -> S_HUNGARIAN
        ItemArchetype.PAVISE -> S_PAVISE
        ItemArchetype.RONDACHE -> S_RONDACHE
        ItemArchetype.ROTELLA -> S_ROTELLA
        ItemArchetype.TOWER_SHIELD -> S_TOWER
        else -> ITEM
    }

    /** What a thing looks like lying on the floor: its own shape in its own metal. */
    fun forDrop(item: Item): Sprite = when {
        item.archetype.isWeapon -> tinted(forWeapon(item.archetype), item.material.tint)
        item.archetype.slot == ItemSlot.SHIELD -> tinted(forShield(item.archetype), item.material.tint)
        item.archetype.slot == ItemSlot.AMMUNITION ->
            tinted(
                Ranged.spriteFor(Ranged.ammoKindOf(item) ?: AmmoKind.STONE_SHOT),
                (item.headMaterial ?: item.material).tint
            )
        item.archetype == ItemArchetype.TORCH -> this[HELD_TORCH]
        else -> this[ITEM]
    }

    private val tintedCache = HashMap<Long, Sprite>()

    /**
     * A sprite washed in a material's hue: pixels painted in metal gray take
     * the tint, pixels marked fixed keep their color, and white changes
     * nothing. Tinted variants are painted once and cached, so the same blade
     * in the same metal costs one pass, ever.
     */
    fun tinted(id: Int, tint: Int): Sprite {
        ensureBuilt()
        val base = sheet[id.coerceIn(0, COUNT - 1)]
        if (tint == 0xFFFFFF) return base
        val key = id.toLong() * 0x1000000L + tint
        return tintedCache.getOrPut(key) {
            val px = IntArray(base.px.size)
            val tr = (tint shr 16) and 0xFF
            val tg = (tint shr 8) and 0xFF
            val tb = tint and 0xFF
            for (i in base.px.indices) {
                val src = base.px[i]
                when (src ushr 24) {
                    0 -> px[i] = 0
                    0xFE -> px[i] = (0xFF shl 24) or (src and 0xFFFFFF)
                    else -> {
                        val r = (((src shr 16) and 0xFF) * tr) / 255
                        val g = (((src shr 8) and 0xFF) * tg) / 255
                        val b = ((src and 0xFF) * tb) / 255
                        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
            Sprite(base.w, base.h, px)
        }
    }
}
