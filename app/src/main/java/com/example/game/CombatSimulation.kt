package com.example.game

import com.example.model.ComputedLoadoutStats
import com.example.model.WeatherType
import com.example.model.WarTheatre
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class StructureKind { BUNKER, FUEL_SILO, WATCHTOWER, RADAR, BARRICADE }

data class DestructibleStructure(
    val id: Int,
    val kind: StructureKind,
    var x: Float,
    var y: Float,
    var z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    var hp: Float,
    val maxHp: Float,
    var destroyed: Boolean = false
)

data class CombatEnemy(
    val id: Int,
    val theatre: WarTheatre,
    var x: Float,
    var y: Float,
    var z: Float,
    var yawDeg: Float,
    var turretYawDeg: Float,
    var hp: Float,
    val maxHp: Float,
    var fireCooldown: Float,
    var strafeDir: Float,
    var isRemotePlayer: Boolean = false,
    var alive: Boolean = true
)

data class Projectile3D(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val fromPlayer: Boolean,
    val damage: Float,
    val isHeavy: Boolean,
    var lifeSec: Float
)

data class Particle3D(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var size: Float,
    var r: Float,
    var g: Float,
    var b: Float,
    var lifeSec: Float,
    val maxLifeSec: Float,
    val gravity: Boolean
)

data class HudSnapshot(
    val hp: Int,
    val maxHp: Int,
    val kills: Int,
    val totalTargets: Int,
    val structuresDestroyed: Int,
    val totalStructures: Int,
    val score: Int,
    val shotsFired: Int,
    val shotsHit: Int,
    val damageDealt: Int,
    val elapsedSec: Int,
    val weather: WeatherType,
    val primaryCooldownRatio: Float,
    val specialCooldownRatio: Float,
    val repairCharges: Int,
    val playerX: Float,
    val playerZ: Float,
    val playerYawDeg: Float,
    val enemyBlips: List<Pair<Float, Float>>,
    val structureBlips: List<Triple<Float, Float, Boolean>>,
    val killFeedText: String,
    val matchFinished: Boolean,
    val victory: Boolean,
    val integrityPassed: Boolean,
    val cheatFlags: List<String>
)

class CombatSimulation(
    val theatre: WarTheatre,
    initialWeather: WeatherType,
    private val dynamicWeatherShift: Boolean,
    private val stats: ComputedLoadoutStats,
    val playerSkinPrimaryHex: Long,
    val playerSkinAccentHex: Long,
    enemyCount: Int,
    structureCount: Int,
    val isMultiplayer: Boolean,
    private val isHost: Boolean,
    private val hasStormSkill: Boolean,
    private val hasRepairSkill: Boolean,
    private val antiCheatEngine: AntiCheatEngine,
    private val soundEngine: SoundEffectsEngine
) {
    // Player state
    var playerX: Float = 0f
    var playerY: Float = 0f
    var playerZ: Float = if (isMultiplayer && !isHost) 48f else -48f
    var playerYawDeg: Float = if (isMultiplayer && !isHost) 180f else 0f
    var turretYawDeg: Float = playerYawDeg
    var playerPitchDeg: Float = 0f
    var playerRollDeg: Float = 0f
    var playerHp: Float = stats.maxHp.toFloat()
    val maxHp: Float = stats.maxHp.toFloat()

    // Controls from Compose UI (thread-safe volatile)
    @Volatile var moveInputX: Float = 0f
    @Volatile var moveInputY: Float = 0f
    @Volatile var aimDeltaYaw: Float = 0f
    @Volatile var aimDeltaPitch: Float = 0f
    @Volatile var isFiringPrimary: Boolean = false
    @Volatile var adsZoomActive: Boolean = false
    @Volatile var paused: Boolean = false

    // Combat state
    var weather: WeatherType = initialWeather
    var lightningFlash: Float = 0f
    var cameraShake: Float = 0f
    var muzzleFlashTimer: Float = 0f
    var simTimeSec: Float = 0f

    private var primaryCooldown: Float = 0f
    private var specialCooldown: Float = 0f
    private var repairCharges: Int = 3
    private var weatherShiftTimer: Float = 26f
    private var thunderTimer: Float = 9f

    var kills: Int = 0
    var score: Int = 0
    var shotsFired: Int = 0
    var shotsHit: Int = 0
    var damageDealt: Int = 0
    var structuresDestroyed: Int = 0
    private var killFeedText: String = "Mission Active — Engage Hostiles"
    private var matchFinished: Boolean = false
    private var victory: Boolean = false

    val enemies = mutableListOf<CombatEnemy>()
    val structures = mutableListOf<DestructibleStructure>()
    val projectiles = mutableListOf<Projectile3D>()
    val particles = mutableListOf<Particle3D>()

    private val totalTargets: Int = if (isMultiplayer) 1 else enemyCount.coerceAtLeast(3)
    private val totalStructures: Int = structureCount.coerceAtLeast(4)

    init {
        playerY = baseAltitudeFor(playerX, playerZ)
        antiCheatEngine.resetMatchAudit(playerX, playerZ)
        spawnStructures(totalStructures)
        spawnEnemies(enemyCount)
    }

    fun baseAltitudeFor(x: Float, z: Float): Float {
        return when (theatre) {
            WarTheatre.AIRCRAFT -> 16.5f
            WarTheatre.NAVY -> getOceanWaveHeight(x, z, simTimeSec)
            WarTheatre.TANK, WarTheatre.INFANTRY -> getTerrainHeight(x, z)
        }
    }

    fun getTerrainHeight(x: Float, z: Float): Float {
        if (theatre == WarTheatre.NAVY) return 0f
        // Keep central arena smoother, raise tactical ridges near flanks
        val distFromCenter = hypot(x.toDouble(), z.toDouble()).toFloat()
        val ridgeFactor = ((distFromCenter - 28f) / 70f).coerceIn(0f, 1.8f)
        val wave = sin(x * 0.055f) * cos(z * 0.055f) * 3.4f +
            sin(x * 0.13f + z * 0.09f) * 0.9f
        return wave * (0.28f + ridgeFactor)
    }

    fun getOceanWaveHeight(x: Float, z: Float, time: Float): Float {
        val amp = if (weather == WeatherType.RAIN) 1.15f else 0.55f
        return (sin(x * 0.12f + time * 1.8f) * cos(z * 0.10f + time * 1.3f)) * amp
    }

    private fun spawnStructures(count: Int) {
        structures.clear()
        val kinds = StructureKind.entries
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * (2f * PI.toFloat()) + 0.35f
            val radius = 24f + (i % 3) * 18f
            val sx = cos(angle) * radius
            val sz = sin(angle) * radius
            val kind = kinds[i % kinds.size]
            val (w, h, d, hp) = when (kind) {
                StructureKind.BUNKER -> listOf(7.0f, 3.8f, 6.0f, 140f)
                StructureKind.FUEL_SILO -> listOf(4.5f, 6.5f, 4.5f, 80f)
                StructureKind.WATCHTOWER -> listOf(3.6f, 9.5f, 3.6f, 95f)
                StructureKind.RADAR -> listOf(4.8f, 7.2f, 4.8f, 110f)
                StructureKind.BARRICADE -> listOf(8.5f, 2.4f, 2.2f, 75f)
            }
            val sy = if (theatre == WarTheatre.NAVY) 0.2f else getTerrainHeight(sx, sz)
            structures.add(
                DestructibleStructure(
                    id = i + 1,
                    kind = kind,
                    x = sx,
                    y = sy,
                    z = sz,
                    width = w,
                    height = h,
                    depth = d,
                    hp = hp,
                    maxHp = hp
                )
            )
        }
    }

    private fun spawnEnemies(count: Int) {
        enemies.clear()
        if (isMultiplayer) {
            val oppZ = if (isHost) 48f else -48f
            val oppYaw = if (isHost) 180f else 0f
            enemies.add(
                CombatEnemy(
                    id = 999,
                    theatre = theatre,
                    x = 0f,
                    y = baseAltitudeFor(0f, oppZ),
                    z = oppZ,
                    yawDeg = oppYaw,
                    turretYawDeg = oppYaw,
                    hp = stats.maxHp.toFloat(),
                    maxHp = stats.maxHp.toFloat(),
                    fireCooldown = 1.4f,
                    strafeDir = 1f,
                    isRemotePlayer = true
                )
            )
            return
        }
        val safeCount = count.coerceIn(3, 10)
        for (i in 0 until safeCount) {
            val angle = (i.toFloat() / safeCount) * (2f * PI.toFloat())
            val radius = 42f + (i % 2) * 24f
            val ex = cos(angle) * radius
            val ez = sin(angle) * radius + 12f
            val enemyHp = when (theatre) {
                WarTheatre.TANK -> 130f
                WarTheatre.AIRCRAFT -> 95f
                WarTheatre.NAVY -> 165f
                WarTheatre.INFANTRY -> 85f
            }
            enemies.add(
                CombatEnemy(
                    id = i + 1,
                    theatre = theatre,
                    x = ex,
                    y = baseAltitudeFor(ex, ez),
                    z = ez,
                    yawDeg = (i * 55f) % 360f,
                    turretYawDeg = 0f,
                    hp = enemyHp,
                    maxHp = enemyHp,
                    fireCooldown = 1.2f + (i % 3) * 0.6f,
                    strafeDir = if (i % 2 == 0) 1f else -1f
                )
            )
        }
    }

    fun updateRemoteOpponent(rx: Float, rz: Float, ryaw: Float, rhp: Int) {
        val remote = enemies.firstOrNull { it.isRemotePlayer } ?: return
        remote.x = rx
        remote.z = rz
        remote.y = baseAltitudeFor(rx, rz)
        remote.yawDeg = ryaw
        remote.turretYawDeg = ryaw
        if (rhp <= 0 && remote.alive) {
            remote.hp = 0f
            remote.alive = false
            spawnExplosion(remote.x, remote.y + 1.5f, remote.z, heavy = true)
            kills++
            score += 500
            killFeedText = "Opponent Eliminated!"
            victory = true
            matchFinished = true
        } else {
            remote.hp = rhp.toFloat()
        }
    }

    fun triggerSpecialAttack() {
        if (matchFinished || paused || specialCooldown > 0f) return
        specialCooldown = 5.5f
        soundEngine.playMissileLaunch()
        val rad = Math.toRadians(turretYawDeg.toDouble())
        val dirX = sin(rad).toFloat()
        val dirZ = cos(rad).toFloat()
        val salvoCount = if (theatre == WarTheatre.NAVY || theatre == WarTheatre.AIRCRAFT) 3 else 2
        for (i in 0 until salvoCount) {
            val offsetAngle = (i - (salvoCount - 1) / 2f) * 0.08f
            val vx = (dirX * cos(offsetAngle) - dirZ * sin(offsetAngle)) * 78f
            val vz = (dirX * sin(offsetAngle) + dirZ * cos(offsetAngle)) * 78f
            val vy = if (theatre == WarTheatre.AIRCRAFT) -8.5f else 1.8f
            projectiles.add(
                Projectile3D(
                    x = playerX + dirX * 3.5f,
                    y = playerY + 2.0f,
                    z = playerZ + dirZ * 3.5f,
                    vx = vx,
                    vy = vy,
                    vz = vz,
                    fromPlayer = true,
                    damage = stats.damage * 1.45f,
                    isHeavy = true,
                    lifeSec = 2.6f
                )
            )
        }
        shotsFired += salvoCount
        cameraShake = max(cameraShake, 0.45f)
        killFeedText = "Heavy Salvo Launched!"
    }

    fun triggerRepair() {
        if (matchFinished || paused || repairCharges <= 0 || playerHp >= maxHp) return
        repairCharges--
        val healAmt = if (hasRepairSkill) maxHp * 0.45f else maxHp * 0.30f
        playerHp = min(maxHp, playerHp + healAmt)
        soundEngine.playRepair()
        killFeedText = "Field Repair +${healAmt.toInt()} HP"
    }

    fun cycleWeather() {
        val entries = WeatherType.entries
        weather = entries[(weather.ordinal + 1) % entries.size]
        killFeedText = "Weather Shift: ${weather.displayName}"
        if (weather == WeatherType.RAIN) {
            lightningFlash = 1.0f
            soundEngine.playThunder()
        }
    }

    @Synchronized
    fun step(dtRaw: Float) {
        if (paused || matchFinished) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        simTimeSec += dt

        // Cooldowns & timers
        primaryCooldown = max(0f, primaryCooldown - dt)
        specialCooldown = max(0f, specialCooldown - dt)
        muzzleFlashTimer = max(0f, muzzleFlashTimer - dt)
        cameraShake = max(0f, cameraShake - dt * 1.8f)
        lightningFlash = max(0f, lightningFlash - dt * 2.5f)

        // Dynamic weather shifts
        if (dynamicWeatherShift) {
            weatherShiftTimer -= dt
            if (weatherShiftTimer <= 0f) {
                weatherShiftTimer = 28f
                cycleWeather()
            }
        }
        if (weather == WeatherType.RAIN) {
            thunderTimer -= dt
            if (thunderTimer <= 0f) {
                thunderTimer = 8f + Random.nextFloat() * 10f
                lightningFlash = 0.95f
                soundEngine.playThunder()
            }
        }

        // Apply aim delta from touch drag
        if (aimDeltaYaw != 0f || aimDeltaPitch != 0f) {
            turretYawDeg = (turretYawDeg + aimDeltaYaw) % 360f
            playerPitchDeg = (playerPitchDeg + aimDeltaPitch).coerceIn(-18f, 22f)
            aimDeltaYaw = 0f
            aimDeltaPitch = 0f
        }

        // Player locomotion
        updatePlayerMovement(dt)

        // Primary fire
        if (isFiringPrimary && primaryCooldown <= 0f) {
            firePlayerPrimary()
        }

        // Enemy AI
        updateEnemies(dt)

        // Projectiles & collisions
        updateProjectiles(dt)

        // Particles
        updateParticles(dt)

        // Check win/loss
        if (playerHp <= 0f && !matchFinished) {
            playerHp = 0f
            matchFinished = true
            victory = false
            spawnExplosion(playerX, playerY + 1.5f, playerZ, heavy = true)
            soundEngine.playExplosion(heavy = true)
            killFeedText = "Unit Destroyed — Mission Failed"
        } else if (!isMultiplayer && enemies.none { it.alive } && !matchFinished) {
            matchFinished = true
            victory = true
            score += 300
            killFeedText = "All Hostiles Neutralized — Victory!"
        }
    }

    private fun updatePlayerMovement(dt: Float) {
        val turnRate = when (theatre) {
            WarTheatre.AIRCRAFT -> 110f
            WarTheatre.NAVY -> 48f
            WarTheatre.TANK -> 78f
            WarTheatre.INFANTRY -> 160f
        }
        if (theatre == WarTheatre.AIRCRAFT) {
            // Aircraft always flies forward with steering from stick X & throttle from stick Y
            playerYawDeg = (playerYawDeg - moveInputX * turnRate * dt) % 360f
            turretYawDeg = playerYawDeg
            playerRollDeg = (-moveInputX * 32f).coerceIn(-35f, 35f)
            val speed = stats.moveSpeed * (0.85f + moveInputY * 0.45f)
            val rad = Math.toRadians(playerYawDeg.toDouble())
            playerX += sin(rad).toFloat() * speed * dt
            playerZ += cos(rad).toFloat() * speed * dt
            playerY = (16.5f + sin(simTimeSec * 1.4f) * 0.6f + playerPitchDeg * 0.15f).coerceIn(9f, 26f)
        } else {
            // Ground/Naval movement relative to camera/turret heading or stick
            val mag = hypot(moveInputX.toDouble(), moveInputY.toDouble()).toFloat()
            if (mag > 0.08f) {
                val camRad = Math.toRadians(turretYawDeg.toDouble())
                val forwardX = sin(camRad).toFloat()
                val forwardZ = cos(camRad).toFloat()
                val rightX = cos(camRad).toFloat()
                val rightZ = -sin(camRad).toFloat()

                val worldMoveX = forwardX * moveInputY + rightX * moveInputX
                val worldMoveZ = forwardZ * moveInputY + rightZ * moveInputX
                val desiredYaw = Math.toDegrees(atan2(worldMoveX.toDouble(), worldMoveZ.toDouble())).toFloat()
                playerYawDeg = lerpAngle(playerYawDeg, desiredYaw, min(1f, dt * (turnRate / 28f)))

                val nx = playerX + worldMoveX * stats.moveSpeed * dt
                val nz = playerZ + worldMoveZ * stats.moveSpeed * dt
                if (!collidesWithStructure(nx, nz, 2.8f)) {
                    playerX = nx
                    playerZ = nz
                }
            }
            playerY = baseAltitudeFor(playerX, playerZ)
            playerRollDeg = if (theatre == WarTheatre.NAVY) sin(simTimeSec * 1.8f) * 3.5f else 0f
        }

        // Keep within combat zone [-135, 135]
        playerX = playerX.coerceIn(-135f, 135f)
        playerZ = playerZ.coerceIn(-135f, 135f)
        antiCheatEngine.auditMovementStep(playerX, playerZ, stats.moveSpeed)
    }

    private fun collidesWithStructure(nx: Float, nz: Float, radius: Float): Boolean {
        if (theatre == WarTheatre.AIRCRAFT) return false
        for (s in structures) {
            if (s.destroyed) continue
            val halfW = s.width * 0.5f + radius
            val halfD = s.depth * 0.5f + radius
            if (kotlin.math.abs(nx - s.x) < halfW && kotlin.math.abs(nz - s.z) < halfD) {
                return true
            }
        }
        return false
    }

    private fun firePlayerPrimary() {
        val dmg = stats.damage.toInt()
        if (!antiCheatEngine.auditWeaponFire(stats.fireCooldownSec, dmg)) return
        primaryCooldown = stats.fireCooldownSec
        muzzleFlashTimer = 0.09f
        shotsFired++

        when (theatre) {
            WarTheatre.TANK -> soundEngine.playCannonFire()
            WarTheatre.AIRCRAFT, WarTheatre.INFANTRY -> soundEngine.playAutocannon()
            WarTheatre.NAVY -> soundEngine.playNavalSalvo()
        }

        val weatherSpreadPenalty = when (weather) {
            WeatherType.CLEAR -> 1.0f
            WeatherType.RAIN -> if (hasStormSkill) 1.05f else 1.25f
            WeatherType.SANDSTORM -> if (hasStormSkill) 1.1f else 1.45f
            WeatherType.NIGHT -> 1.15f
        }
        val spread = stats.spreadRad * weatherSpreadPenalty
        val yawRad = Math.toRadians(turretYawDeg.toDouble()).toFloat() +
            (Random.nextFloat() - 0.5f) * spread
        val pitchRad = Math.toRadians(playerPitchDeg.toDouble()).toFloat() * 0.35f

        val speed = when (theatre) {
            WarTheatre.TANK -> 92f
            WarTheatre.AIRCRAFT -> 115f
            WarTheatre.NAVY -> 88f
            WarTheatre.INFANTRY -> 96f
        }
        val vx = sin(yawRad) * cos(pitchRad) * speed
        val vz = cos(yawRad) * cos(pitchRad) * speed
        val vy = if (theatre == WarTheatre.AIRCRAFT) -sin(pitchRad) * speed - 6f else sin(pitchRad) * speed * 0.4f

        projectiles.add(
            Projectile3D(
                x = playerX + sin(yawRad) * 3.2f,
                y = playerY + (if (theatre == WarTheatre.INFANTRY) 1.4f else 2.1f),
                z = playerZ + cos(yawRad) * 3.2f,
                vx = vx,
                vy = vy,
                vz = vz,
                fromPlayer = true,
                damage = stats.damage,
                isHeavy = theatre == WarTheatre.TANK || theatre == WarTheatre.NAVY,
                lifeSec = stats.range / speed
            )
        )
        if (theatre == WarTheatre.TANK || theatre == WarTheatre.NAVY) {
            cameraShake = max(cameraShake, 0.22f)
        }
    }

    private fun updateEnemies(dt: Float) {
        for (enemy in enemies) {
            if (!enemy.alive || enemy.isRemotePlayer) continue
            val dx = playerX - enemy.x
            val dz = playerZ - enemy.z
            val dist = hypot(dx.toDouble(), dz.toDouble()).toFloat().coerceAtLeast(0.1f)
            val angleToPlayer = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
            enemy.turretYawDeg = lerpAngle(enemy.turretYawDeg, angleToPlayer, dt * 4f)

            // AI maneuvering
            val preferredRange = when (theatre) {
                WarTheatre.AIRCRAFT -> 32f
                WarTheatre.NAVY -> 48f
                else -> 36f
            }
            val moveSpeed = when (theatre) {
                WarTheatre.AIRCRAFT -> 16f
                WarTheatre.NAVY -> 6f
                WarTheatre.TANK -> 7f
                WarTheatre.INFANTRY -> 8.5f
            }
            val forwardWeight = if (dist > preferredRange) 0.75f else -0.25f
            val rad = Math.toRadians(angleToPlayer.toDouble()).toFloat()
            val strafeRad = rad + (PI.toFloat() / 2f) * enemy.strafeDir
            val vx = (sin(rad) * forwardWeight + sin(strafeRad) * 0.65f) * moveSpeed
            val vz = (cos(rad) * forwardWeight + cos(strafeRad) * 0.65f) * moveSpeed

            enemy.x = (enemy.x + vx * dt).coerceIn(-125f, 125f)
            enemy.z = (enemy.z + vz * dt).coerceIn(-125f, 125f)
            enemy.y = baseAltitudeFor(enemy.x, enemy.z)
            enemy.yawDeg = lerpAngle(enemy.yawDeg, Math.toDegrees(atan2(vx.toDouble(), vz.toDouble())).toFloat(), dt * 3f)

            if (Random.nextFloat() < dt * 0.25f) {
                enemy.strafeDir *= -1f
            }

            // Enemy firing
            enemy.fireCooldown -= dt
            val sightRange = if (weather == WeatherType.SANDSTORM) 65f else 92f
            if (enemy.fireCooldown <= 0f && dist < sightRange) {
                enemy.fireCooldown = when (theatre) {
                    WarTheatre.AIRCRAFT -> 1.1f + Random.nextFloat() * 0.7f
                    WarTheatre.INFANTRY -> 1.0f + Random.nextFloat() * 0.8f
                    else -> 2.1f + Random.nextFloat() * 1.1f
                }
                val aimErr = (Random.nextFloat() - 0.5f) * 0.14f
                val fireYaw = rad + aimErr
                val projSpeed = 62f
                val dy = (playerY + 1.2f) - (enemy.y + 1.8f)
                val timeToHit = (dist / projSpeed).coerceAtLeast(0.1f)
                projectiles.add(
                    Projectile3D(
                        x = enemy.x + sin(fireYaw) * 3.0f,
                        y = enemy.y + 1.8f,
                        z = enemy.z + cos(fireYaw) * 3.0f,
                        vx = sin(fireYaw) * projSpeed,
                        vy = dy / timeToHit,
                        vz = cos(fireYaw) * projSpeed,
                        fromPlayer = false,
                        damage = if (theatre == WarTheatre.TANK || theatre == WarTheatre.NAVY) 18f else 10f,
                        isHeavy = theatre == WarTheatre.TANK || theatre == WarTheatre.NAVY,
                        lifeSec = 2.4f
                    )
                )
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.lifeSec -= dt

            var remove = p.lifeSec <= 0f

            // Ground / water impact
            val groundY = if (theatre == WarTheatre.NAVY) 0f else getTerrainHeight(p.x, p.z)
            if (!remove && p.y <= groundY) {
                spawnImpactParticles(p.x, groundY, p.z, p.isHeavy)
                remove = true
            }

            // Destructible structure collisions
            if (!remove) {
                for (s in structures) {
                    if (s.destroyed) continue
                    if (kotlin.math.abs(p.x - s.x) < s.width * 0.6f &&
                        kotlin.math.abs(p.z - s.z) < s.depth * 0.6f &&
                        p.y <= s.y + s.height + 1.2f
                    ) {
                        s.hp -= p.damage
                        spawnImpactParticles(p.x, p.y, p.z, p.isHeavy)
                        if (p.fromPlayer) {
                            shotsHit++
                            damageDealt += p.damage.toInt()
                            soundEngine.playHitMarker()
                        }
                        if (s.hp <= 0f && !s.destroyed) {
                            s.destroyed = true
                            s.hp = 0f
                            structuresDestroyed++
                            score += if (s.kind == StructureKind.FUEL_SILO) 160 else 100
                            spawnExplosion(s.x, s.y + 2.0f, s.z, heavy = true)
                            soundEngine.playExplosion(heavy = true)
                            cameraShake = max(cameraShake, 0.55f)
                            killFeedText = "${s.kind.name.replace('_', ' ')} Demolished!"
                            // Fuel silo explosive chain damage to nearby enemies
                            if (s.kind == StructureKind.FUEL_SILO) {
                                for (e in enemies) {
                                    if (e.alive && hypot((e.x - s.x).toDouble(), (e.z - s.z).toDouble()) < 18.0) {
                                        damageEnemy(e, 70f)
                                    }
                                }
                            }
                        }
                        remove = true
                        break
                    }
                }
            }

            // Hit enemies or player
            if (!remove && p.fromPlayer) {
                for (e in enemies) {
                    if (!e.alive) continue
                    val hitRadius = when (theatre) {
                        WarTheatre.NAVY -> 4.8f
                        WarTheatre.AIRCRAFT -> 4.2f
                        WarTheatre.TANK -> 3.6f
                        WarTheatre.INFANTRY -> 2.4f
                    }
                    val dy = kotlin.math.abs(p.y - (e.y + 1.6f))
                    if (hypot((p.x - e.x).toDouble(), (p.z - e.z).toDouble()) < hitRadius && dy < 4.5f) {
                        shotsHit++
                        damageDealt += p.damage.toInt()
                        soundEngine.playHitMarker()
                        spawnImpactParticles(p.x, p.y, p.z, p.isHeavy)
                        damageEnemy(e, p.damage)
                        remove = true
                        break
                    }
                }
            } else if (!remove && !p.fromPlayer) {
                val hitRadius = if (theatre == WarTheatre.NAVY) 4.2f else 2.8f
                val dy = kotlin.math.abs(p.y - (playerY + 1.5f))
                if (hypot((p.x - playerX).toDouble(), (p.z - playerZ).toDouble()) < hitRadius && dy < 4.2f) {
                    playerHp = max(0f, playerHp - p.damage)
                    cameraShake = max(cameraShake, 0.35f)
                    spawnImpactParticles(p.x, p.y, p.z, p.isHeavy)
                    remove = true
                }
            }

            if (remove) iterator.remove()
        }
    }

    private fun damageEnemy(enemy: CombatEnemy, dmg: Float) {
        if (enemy.isRemotePlayer) {
            // Remote player HP is authoritative via Firestore sync; we award local hit score
            score += 35
            killFeedText = "Direct Hit on Rival Commander!"
            return
        }
        enemy.hp -= dmg
        if (enemy.hp <= 0f && enemy.alive) {
            enemy.hp = 0f
            enemy.alive = false
            kills++
            score += 220
            spawnExplosion(enemy.x, enemy.y + 1.5f, enemy.z, heavy = true)
            soundEngine.playExplosion(heavy = true)
            cameraShake = max(cameraShake, 0.45f)
            killFeedText = "Hostile ${enemy.theatre.shortTag} Destroyed! ($kills/$totalTargets)"
        }
    }

    private fun spawnImpactParticles(x: Float, y: Float, z: Float, heavy: Boolean) {
        val count = if (heavy) 10 else 5
        for (i in 0 until count) {
            particles.add(
                Particle3D(
                    x = x,
                    y = y + 0.3f,
                    z = z,
                    vx = (Random.nextFloat() - 0.5f) * 14f,
                    vy = 3f + Random.nextFloat() * 8f,
                    vz = (Random.nextFloat() - 0.5f) * 14f,
                    size = if (heavy) 0.55f else 0.3f,
                    r = 1.0f,
                    g = 0.55f + Random.nextFloat() * 0.35f,
                    b = 0.15f,
                    lifeSec = 0.45f + Random.nextFloat() * 0.25f,
                    maxLifeSec = 0.7f,
                    gravity = true
                )
            )
        }
    }

    fun spawnExplosion(x: Float, y: Float, z: Float, heavy: Boolean) {
        val count = if (heavy) 26 else 12
        for (i in 0 until count) {
            val isDebris = i % 3 == 0
            particles.add(
                Particle3D(
                    x = x,
                    y = y,
                    z = z,
                    vx = (Random.nextFloat() - 0.5f) * (if (heavy) 24f else 14f),
                    vy = 5f + Random.nextFloat() * (if (heavy) 16f else 9f),
                    vz = (Random.nextFloat() - 0.5f) * (if (heavy) 24f else 14f),
                    size = if (isDebris) 0.75f else 1.25f,
                    r = if (isDebris) 0.28f else 1.0f,
                    g = if (isDebris) 0.28f else 0.45f + Random.nextFloat() * 0.35f,
                    b = if (isDebris) 0.30f else 0.08f,
                    lifeSec = 0.7f + Random.nextFloat() * 0.6f,
                    maxLifeSec = 1.3f,
                    gravity = isDebris
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val pt = it.next()
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.z += pt.vz * dt
            if (pt.gravity) pt.vy -= 18f * dt
            pt.lifeSec -= dt
            if (pt.lifeSec <= 0f) it.remove()
        }
    }

    fun snapshotHud(): HudSnapshot {
        val report = antiCheatEngine.auditMatchOutcome(
            uid = "live",
            theatre = theatre,
            missionId = "live",
            kills = kills,
            damageDealt = damageDealt,
            shotsFired = shotsFired,
            durationSec = simTimeSec.toInt().coerceAtLeast(1)
        )
        return HudSnapshot(
            hp = playerHp.toInt(),
            maxHp = maxHp.toInt(),
            kills = kills,
            totalTargets = totalTargets,
            structuresDestroyed = structuresDestroyed,
            totalStructures = totalStructures,
            score = score,
            shotsFired = shotsFired,
            shotsHit = shotsHit,
            damageDealt = damageDealt,
            elapsedSec = simTimeSec.toInt(),
            weather = weather,
            primaryCooldownRatio = (primaryCooldown / stats.fireCooldownSec).coerceIn(0f, 1f),
            specialCooldownRatio = (specialCooldown / 5.5f).coerceIn(0f, 1f),
            repairCharges = repairCharges,
            playerX = playerX,
            playerZ = playerZ,
            playerYawDeg = turretYawDeg,
            enemyBlips = enemies.filter { it.alive }.map { it.x to it.z },
            structureBlips = structures.map { Triple(it.x, it.z, it.destroyed) },
            killFeedText = killFeedText,
            matchFinished = matchFinished,
            victory = victory,
            integrityPassed = report.integrityPassed,
            cheatFlags = report.flags
        )
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return a + diff * t.coerceIn(0f, 1f)
    }
}
