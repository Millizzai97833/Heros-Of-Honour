package com.example.game

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.model.GraphicsQuality
import com.example.model.WeatherType
import com.example.model.WarTheatre
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hardware-accelerated OpenGL ES 3.0 3D Combat Renderer.
 * Renders procedural 3D terrain/ocean, PBR-inspired directional sunlight + specular reflections,
 * detailed multi-part 3D Tanks, Fighter Jets, Naval Warships, Infantry Squads,
 * Destructible Structures, 3D Weather Particles, and Explosive VFX.
 */
class GlBattleRenderer(
    private val sim: CombatSimulation,
    @Volatile var graphicsQuality: GraphicsQuality,
    private val onFpsUpdated: (Int) -> Unit
) : GLSurfaceView.Renderer {

    private var programId: Int = 0
    private var mvpMatrixLoc: Int = 0
    private var modelMatrixLoc: Int = 0
    private var colorLoc: Int = 0
    private var sunDirLoc: Int = 0
    private var camPosLoc: Int = 0
    private var fogColorLoc: Int = 0
    private var fogDensityLoc: Int = 0
    private var ambientStrengthLoc: Int = 0
    private var specularStrengthLoc: Int = 0

    private lateinit var cubeBuffer: FloatBuffer
    private var cubeVertexCount: Int = 0

    private lateinit var terrainBuffer: FloatBuffer
    private var terrainVertexCount: Int = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var lastFrameNs: Long = System.nanoTime()
    private var fpsCounter: Int = 0
    private var fpsTimerMs: Long = System.currentTimeMillis()
    private var widthPx: Int = 1280
    private var heightPx: Int = 720

    private val weatherDrops = Array(140) {
        floatArrayOf(
            (Random.nextFloat() - 0.5f) * 65f,
            Random.nextFloat() * 35f,
            (Random.nextFloat() - 0.5f) * 65f
        )
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        programId = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        mvpMatrixLoc = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        modelMatrixLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        colorLoc = GLES30.glGetUniformLocation(programId, "uColor")
        sunDirLoc = GLES30.glGetUniformLocation(programId, "uSunDir")
        camPosLoc = GLES30.glGetUniformLocation(programId, "uCamPos")
        fogColorLoc = GLES30.glGetUniformLocation(programId, "uFogColor")
        fogDensityLoc = GLES30.glGetUniformLocation(programId, "uFogDensity")
        ambientStrengthLoc = GLES30.glGetUniformLocation(programId, "uAmbientStrength")
        specularStrengthLoc = GLES30.glGetUniformLocation(programId, "uSpecularStrength")

        buildCubeMesh()
        buildTerrainMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, widthPx, heightPx)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        sim.step(dt)

        fpsCounter++
        val nowMs = System.currentTimeMillis()
        if (nowMs - fpsTimerMs >= 500L) {
            val fps = ((fpsCounter * 1000L) / (nowMs - fpsTimerMs).coerceAtLeast(1L)).toInt()
            onFpsUpdated(fps)
            fpsCounter = 0
            fpsTimerMs = nowMs
        }

        // Dynamic sky & weather atmosphere
        val flash = sim.lightningFlash
        val (skyR, skyG, skyB) = when (sim.weather) {
            WeatherType.CLEAR -> Triple(0.24f, 0.48f, 0.72f)
            WeatherType.RAIN -> Triple(0.14f + flash * 0.55f, 0.19f + flash * 0.55f, 0.26f + flash * 0.6f)
            WeatherType.SANDSTORM -> Triple(0.68f, 0.49f, 0.28f)
            WeatherType.NIGHT -> Triple(0.03f, 0.05f, 0.11f)
        }
        GLES30.glClearColor(skyR, skyG, skyB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = widthPx.toFloat() / heightPx.toFloat()
        val fov = if (sim.adsZoomActive) 34f else 56f
        Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.5f, 360f)

        // Third-person PUBG-style tactical chase camera
        val camDist = when (sim.theatre) {
            WarTheatre.NAVY -> 26f
            WarTheatre.AIRCRAFT -> 19f
            WarTheatre.TANK -> 16f
            WarTheatre.INFANTRY -> 11.5f
        } * (if (sim.adsZoomActive) 0.68f else 1.0f)
        val camHeight = when (sim.theatre) {
            WarTheatre.NAVY -> 11f
            WarTheatre.AIRCRAFT -> 6.5f
            WarTheatre.TANK -> 6.8f
            WarTheatre.INFANTRY -> 4.6f
        }
        val aimRad = Math.toRadians(sim.turretYawDeg.toDouble()).toFloat()
        val shakeX = if (sim.cameraShake > 0f) (Random.nextFloat() - 0.5f) * sim.cameraShake * 1.1f else 0f
        val shakeY = if (sim.cameraShake > 0f) (Random.nextFloat() - 0.5f) * sim.cameraShake * 0.9f else 0f

        val camX = sim.playerX - sin(aimRad) * camDist + shakeX
        val camZ = sim.playerZ - cos(aimRad) * camDist
        val camY = sim.playerY + camHeight + shakeY - sim.playerPitchDeg * 0.18f

        val lookX = sim.playerX + sin(aimRad) * 18f
        val lookZ = sim.playerZ + cos(aimRad) * 18f
        val lookY = sim.playerY + 1.8f + sim.playerPitchDeg * 0.25f

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES30.glUseProgram(programId)
        GLES30.glUniform3f(sunDirLoc, 0.45f, 0.82f, 0.35f)
        GLES30.glUniform3f(camPosLoc, camX, camY, camZ)
        GLES30.glUniform3f(fogColorLoc, skyR, skyG, skyB)

        val qualityFogFactor = if (graphicsQuality == GraphicsQuality.LOW) 1.15f else 1.0f
        GLES30.glUniform1f(fogDensityLoc, sim.weather.fogDensity * qualityFogFactor)

        val ambient = when (sim.weather) {
            WeatherType.NIGHT -> 0.24f
            WeatherType.RAIN -> 0.36f + flash * 0.35f
            else -> 0.44f
        }
        GLES30.glUniform1f(ambientStrengthLoc, ambient)

        // 1. Render Terrain / Ocean
        drawTerrain()

        // 2. Render Destructible Structures
        drawStructures()

        // 3. Render Player Unit
        val (pPrimR, pPrimG, pPrimB) = hexToRgb(sim.playerSkinPrimaryHex)
        val (pAccR, pAccG, pAccB) = hexToRgb(sim.playerSkinAccentHex)
        drawUnit(
            theatre = sim.theatre,
            x = sim.playerX,
            y = sim.playerY,
            z = sim.playerZ,
            bodyYaw = sim.playerYawDeg,
            turretYaw = sim.turretYawDeg,
            roll = sim.playerRollDeg,
            primR = pPrimR, primG = pPrimG, primB = pPrimB,
            accR = pAccR, accG = pAccG, accB = pAccB,
            muzzleGlow = sim.muzzleFlashTimer > 0f
        )

        // 4. Render Enemies / Remote Multiplayer Opponent
        for (e in sim.enemies) {
            if (!e.alive) continue
            drawUnit(
                theatre = e.theatre,
                x = e.x,
                y = e.y,
                z = e.z,
                bodyYaw = e.yawDeg,
                turretYaw = e.turretYawDeg,
                roll = 0f,
                primR = if (e.isRemotePlayer) 0.78f else 0.42f,
                primG = if (e.isRemotePlayer) 0.22f else 0.18f,
                primB = if (e.isRemotePlayer) 0.20f else 0.16f,
                accR = 0.95f, accG = 0.25f, accB = 0.20f,
                muzzleGlow = false
            )
        }

        // 5. Render Projectiles
        GLES30.glUniform1f(specularStrengthLoc, 0.9f)
        for (p in sim.projectiles) {
            val scale = if (p.isHeavy) 0.55f else 0.26f
            val r = if (p.fromPlayer) 1.0f else 1.0f
            val g = if (p.fromPlayer) 0.85f else 0.22f
            val b = if (p.fromPlayer) 0.25f else 0.15f
            drawTransformedCube(p.x, p.y, p.z, 0f, scale, scale, scale * 2.6f, r, g, b)
        }

        // 6. Render Explosion & Debris Particles
        val maxParticles = (sim.particles.size * graphicsQuality.particleMultiplier).toInt().coerceAtLeast(1)
        val stepSkip = if (sim.particles.size > maxParticles) 2 else 1
        for (i in sim.particles.indices step stepSkip) {
            val pt = sim.particles[i]
            val fade = (pt.lifeSec / pt.maxLifeSec).coerceIn(0.2f, 1.0f)
            drawTransformedCube(
                pt.x, pt.y, pt.z,
                (pt.lifeSec * 180f) % 360f,
                pt.size * fade, pt.size * fade, pt.size * fade,
                pt.r, pt.g * fade, pt.b
            )
        }

        // 7. Render 3D Weather FX (Rain streaks or Sandstorm particles)
        if (sim.weather == WeatherType.RAIN || sim.weather == WeatherType.SANDSTORM) {
            drawWeatherParticles(dt, camX, camY, camZ)
        }
    }

    private fun drawTerrain() {
        Matrix.setIdentityM(modelMatrix, 0)
        if (sim.theatre == WarTheatre.NAVY) {
            Matrix.translateM(modelMatrix, 0, 0f, sin(sim.simTimeSec * 1.4f) * 0.25f, 0f)
            GLES30.glUniform3f(colorLoc, 0.06f, 0.22f, 0.38f)
            GLES30.glUniform1f(specularStrengthLoc, if (graphicsQuality == GraphicsQuality.LOW) 0.25f else 0.85f)
        } else {
            val (r, g, b) = when (sim.weather) {
                WeatherType.SANDSTORM -> Triple(0.56f, 0.44f, 0.28f)
                WeatherType.NIGHT -> Triple(0.14f, 0.18f, 0.16f)
                WeatherType.RAIN -> Triple(0.22f, 0.28f, 0.20f)
                WeatherType.CLEAR -> Triple(0.32f, 0.37f, 0.24f)
            }
            GLES30.glUniform3f(colorLoc, r, g, b)
            GLES30.glUniform1f(specularStrengthLoc, 0.15f)
        }
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(modelMatrixLoc, 1, false, modelMatrix, 0)
        bindAndDrawBuffer(terrainBuffer, terrainVertexCount)
    }

    private fun drawStructures() {
        GLES30.glUniform1f(specularStrengthLoc, 0.3f)
        for (s in sim.structures) {
            if (s.destroyed) {
                // Charred rubble base
                drawTransformedCube(
                    s.x, s.y + 0.35f, s.z, 12f,
                    s.width * 0.9f, 0.7f, s.depth * 0.9f,
                    0.18f, 0.17f, 0.16f
                )
                continue
            }
            val hpRatio = (s.hp / s.maxHp).coerceIn(0f, 1f)
            val dmgDarken = 0.45f + 0.55f * hpRatio
            when (s.kind) {
                StructureKind.BUNKER -> {
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.5f, s.z, 0f,
                        s.width, s.height, s.depth,
                        0.45f * dmgDarken, 0.44f * dmgDarken, 0.42f * dmgDarken
                    )
                    // Firing slit accent
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.7f, s.z, 0f,
                        s.width * 1.03f, 0.45f, s.depth * 0.55f,
                        0.12f, 0.12f, 0.14f
                    )
                }
                StructureKind.FUEL_SILO -> {
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.5f, s.z, 0f,
                        s.width, s.height, s.depth,
                        0.76f * dmgDarken, 0.24f * dmgDarken, 0.18f * dmgDarken
                    )
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.85f, s.z, 0f,
                        s.width * 1.06f, 0.5f, s.depth * 1.06f,
                        0.92f, 0.65f, 0.15f
                    )
                }
                StructureKind.WATCHTOWER -> {
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.4f, s.z, 0f,
                        s.width * 0.55f, s.height * 0.8f, s.depth * 0.55f,
                        0.38f * dmgDarken, 0.35f * dmgDarken, 0.30f * dmgDarken
                    )
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.85f, s.z, 0f,
                        s.width, 1.8f, s.depth,
                        0.48f * dmgDarken, 0.45f * dmgDarken, 0.38f * dmgDarken
                    )
                }
                StructureKind.RADAR -> {
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.35f, s.z, 0f,
                        s.width * 0.7f, s.height * 0.7f, s.depth * 0.7f,
                        0.32f * dmgDarken, 0.38f * dmgDarken, 0.42f * dmgDarken
                    )
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.85f, s.z, (sim.simTimeSec * 70f) % 360f,
                        s.width * 0.9f, 1.6f, 0.5f,
                        0.75f * dmgDarken, 0.78f * dmgDarken, 0.80f * dmgDarken
                    )
                }
                StructureKind.BARRICADE -> {
                    drawTransformedCube(
                        s.x, s.y + s.height * 0.5f, s.z, 0f,
                        s.width, s.height, s.depth,
                        0.52f * dmgDarken, 0.46f * dmgDarken, 0.36f * dmgDarken
                    )
                }
            }
        }
    }

    private fun drawUnit(
        theatre: WarTheatre,
        x: Float, y: Float, z: Float,
        bodyYaw: Float, turretYaw: Float, roll: Float,
        primR: Float, primG: Float, primB: Float,
        accR: Float, accG: Float, accB: Float,
        muzzleGlow: Boolean
    ) {
        GLES30.glUniform1f(specularStrengthLoc, if (graphicsQuality == GraphicsQuality.LOW) 0.2f else 0.75f)
        when (theatre) {
            WarTheatre.TANK -> {
                // Armored Hull
                drawTransformedCube(x, y + 0.9f, z, bodyYaw, 3.2f, 1.1f, 5.2f, primR, primG, primB)
                // Left & Right Heavy Treads
                val rad = Math.toRadians(bodyYaw.toDouble()).toFloat()
                val rx = cos(rad) * 1.75f
                val rz = -sin(rad) * 1.75f
                drawTransformedCube(x + rx, y + 0.65f, z + rz, bodyYaw, 0.8f, 0.95f, 5.5f, 0.15f, 0.16f, 0.17f)
                drawTransformedCube(x - rx, y + 0.65f, z - rz, bodyYaw, 0.8f, 0.95f, 5.5f, 0.15f, 0.16f, 0.17f)
                // Rotating Turret + Accent Armor
                drawTransformedCube(x, y + 1.85f, z, turretYaw, 2.3f, 0.95f, 2.9f, primR * 0.9f, primG * 0.9f, primB * 0.9f)
                drawTransformedCube(x, y + 2.35f, z, turretYaw, 1.3f, 0.35f, 1.4f, accR, accG, accB)
                // Main 120mm Cannon Barrel
                val trad = Math.toRadians(turretYaw.toDouble()).toFloat()
                drawTransformedCube(
                    x + sin(trad) * 3.1f, y + 1.9f, z + cos(trad) * 3.1f,
                    turretYaw, 0.32f, 0.32f, 4.2f,
                    0.26f, 0.28f, 0.30f
                )
                if (muzzleGlow) {
                    drawTransformedCube(
                        x + sin(trad) * 5.4f, y + 1.9f, z + cos(trad) * 5.4f,
                        turretYaw, 1.1f, 1.1f, 1.4f, 1.0f, 0.78f, 0.2f
                    )
                }
            }
            WarTheatre.AIRCRAFT -> {
                // Sleek Fuselage
                drawTransformedCubeRoll(x, y, z, bodyYaw, roll, 1.2f, 0.85f, 5.8f, primR, primG, primB)
                // Swept Delta Wings
                drawTransformedCubeRoll(x, y - 0.1f, z, bodyYaw, roll, 6.8f, 0.18f, 2.2f, primR * 0.88f, primG * 0.88f, primB * 0.88f)
                // Cockpit Canopy
                val rad = Math.toRadians(bodyYaw.toDouble()).toFloat()
                drawTransformedCubeRoll(
                    x + sin(rad) * 1.1f, y + 0.55f, z + cos(rad) * 1.1f,
                    bodyYaw, roll, 0.75f, 0.5f, 1.8f, accR, accG, accB
                )
                // Twin Tail Fins
                drawTransformedCubeRoll(
                    x - sin(rad) * 2.2f, y + 0.85f, z - cos(rad) * 2.2f,
                    bodyYaw, roll, 1.9f, 1.1f, 0.9f, primR, primG, primB
                )
                // Glowing Afterburners
                drawTransformedCubeRoll(
                    x - sin(rad) * 3.1f, y, z - cos(rad) * 3.1f,
                    bodyYaw, roll, 0.85f, 0.45f, 0.8f, 0.35f, 0.75f, 1.0f
                )
            }
            WarTheatre.NAVY -> {
                // Armored Destroyer Hull
                drawTransformedCubeRoll(x, y + 0.8f, z, bodyYaw, roll, 3.6f, 1.8f, 11.5f, primR, primG, primB)
                // Bridge Superstructure
                drawTransformedCubeRoll(x, y + 2.4f, z, bodyYaw, roll, 2.4f, 2.0f, 4.2f, primR * 0.85f, primG * 0.85f, primB * 0.85f)
                drawTransformedCubeRoll(x, y + 3.8f, z, bodyYaw, roll, 0.7f, 1.6f, 0.7f, accR, accG, accB)
                // Forward & Aft Naval Turrets
                val rad = Math.toRadians(bodyYaw.toDouble()).toFloat()
                val trad = Math.toRadians(turretYaw.toDouble()).toFloat()
                val fx = x + sin(rad) * 3.4f
                val fz = z + cos(rad) * 3.4f
                drawTransformedCube(fx, y + 2.0f, fz, turretYaw, 1.4f, 0.85f, 1.8f, accR, accG, accB)
                drawTransformedCube(fx + sin(trad) * 1.8f, y + 2.1f, fz + cos(trad) * 1.8f, turretYaw, 0.35f, 0.25f, 2.5f, 0.25f, 0.25f, 0.28f)
                if (muzzleGlow) {
                    drawTransformedCube(fx + sin(trad) * 3.2f, y + 2.1f, fz + cos(trad) * 3.2f, turretYaw, 1.2f, 1.2f, 1.2f, 1.0f, 0.7f, 0.2f)
                }
            }
            WarTheatre.INFANTRY -> {
                // Tactical Spec-Ops Soldier
                // Legs / Boots
                drawTransformedCube(x, y + 0.65f, z, bodyYaw, 0.9f, 1.3f, 0.6f, 0.18f, 0.20f, 0.18f)
                // Armored Vest Torso
                drawTransformedCube(x, y + 1.75f, z, turretYaw, 1.05f, 1.15f, 0.7f, primR, primG, primB)
                // Tactical Helmet + Visor
                drawTransformedCube(x, y + 2.65f, z, turretYaw, 0.68f, 0.68f, 0.68f, accR, accG, accB)
                // Assault Rifle
                val trad = Math.toRadians(turretYaw.toDouble()).toFloat()
                drawTransformedCube(
                    x + sin(trad) * 0.95f, y + 1.8f, z + cos(trad) * 0.95f,
                    turretYaw, 0.18f, 0.22f, 1.7f, 0.15f, 0.15f, 0.17f
                )
                if (muzzleGlow) {
                    drawTransformedCube(
                        x + sin(trad) * 1.95f, y + 1.8f, z + cos(trad) * 1.95f,
                        turretYaw, 0.45f, 0.45f, 0.5f, 1.0f, 0.82f, 0.25f
                    )
                }
            }
        }
    }

    private fun drawWeatherParticles(dt: Float, camX: Float, camY: Float, camZ: Float) {
        val density = (weatherDrops.size * graphicsQuality.particleMultiplier).toInt().coerceAtMost(weatherDrops.size)
        val isRain = sim.weather == WeatherType.RAIN
        val r = if (isRain) 0.65f else 0.85f
        val g = if (isRain) 0.78f else 0.66f
        val b = if (isRain) 0.95f else 0.36f

        for (i in 0 until density) {
            val d = weatherDrops[i]
            if (isRain) {
                d[1] -= 38f * dt
                d[0] += 4f * dt
            } else {
                d[0] += 22f * dt
                d[1] -= 2.5f * dt
            }
            if (d[1] < -2f || d[0] > 32f) {
                d[0] = (Random.nextFloat() - 0.5f) * 65f
                d[1] = 28f + Random.nextFloat() * 10f
                d[2] = (Random.nextFloat() - 0.5f) * 65f
            }
            drawTransformedCube(
                camX + d[0], max(0.5f, d[1]), camZ + d[2], 0f,
                if (isRain) 0.08f else 0.25f,
                if (isRain) 1.3f else 0.18f,
                0.08f,
                r, g, b
            )
        }
    }

    private fun drawTransformedCube(
        x: Float, y: Float, z: Float, yawDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(modelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(colorLoc, r, g, b)
        bindAndDrawBuffer(cubeBuffer, cubeVertexCount)
    }

    private fun drawTransformedCubeRoll(
        x: Float, y: Float, z: Float, yawDeg: Float, rollDeg: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(modelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(colorLoc, r, g, b)
        bindAndDrawBuffer(cubeBuffer, cubeVertexCount)
    }

    private fun bindAndDrawBuffer(buffer: FloatBuffer, vertexCount: Int) {
        val stride = 6 * 4
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, buffer)

        buffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, buffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    }

    private fun buildCubeMesh() {
        // 36 vertices, each: x, y, z, nx, ny, nz
        val v = floatArrayOf(
            // Front (+Z)
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, 0.5f, 0.5f, 0f, 0f, 1f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
            // Back (-Z)
            0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, 0.5f, -0.5f, 0f, 0f, -1f,
            0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0.5f, 0.5f, -0.5f, 0f, 0f, -1f,
            // Left (-X)
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, -0.5f, -0.5f, 0.5f, -1f, 0f, 0f, -0.5f, 0.5f, 0.5f, -1f, 0f, 0f,
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f,
            // Right (+X)
            0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 0.5f, 0.5f, -0.5f, 1f, 0f, 0f,
            0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f,
            // Top (+Y)
            -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f, -0.5f, 0.5f, -0.5f, 0f, 1f, 0f,
            // Bottom (-Y)
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f,
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f, -0.5f, -0.5f, 0.5f, 0f, -1f, 0f
        )
        cubeVertexCount = 36
        cubeBuffer = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        cubeBuffer.put(v).position(0)
    }

    private fun buildTerrainMesh() {
        val grid = 36
        val size = 300f
        val step = size / grid
        val start = -size / 2f
        val floats = ArrayList<Float>(grid * grid * 6 * 6)

        fun h(x: Float, z: Float): Float = sim.getTerrainHeight(x, z)
        fun addVert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            floats.add(x); floats.add(y); floats.add(z)
            floats.add(nx); floats.add(ny); floats.add(nz)
        }

        for (gx in 0 until grid) {
            for (gz in 0 until grid) {
                val x0 = start + gx * step
                val z0 = start + gz * step
                val x1 = x0 + step
                val z1 = z0 + step

                val y00 = h(x0, z0)
                val y10 = h(x1, z0)
                val y01 = h(x0, z1)
                val y11 = h(x1, z1)

                // Triangle 1: (x0,z0), (x0,z1), (x1,z1)
                val (n1x, n1y, n1z) = computeNormal(x0, y00, z0, x0, y01, z1, x1, y11, z1)
                addVert(x0, y00, z0, n1x, n1y, n1z)
                addVert(x0, y01, z1, n1x, n1y, n1z)
                addVert(x1, y11, z1, n1x, n1y, n1z)

                // Triangle 2: (x0,z0), (x1,z1), (x1,z0)
                val (n2x, n2y, n2z) = computeNormal(x0, y00, z0, x1, y11, z1, x1, y10, z0)
                addVert(x0, y00, z0, n2x, n2y, n2z)
                addVert(x1, y11, z1, n2x, n2y, n2z)
                addVert(x1, y10, z0, n2x, n2y, n2z)
            }
        }
        val arr = floats.toFloatArray()
        terrainVertexCount = arr.size / 6
        terrainBuffer = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        terrainBuffer.put(arr).position(0)
    }

    private fun computeNormal(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Triple<Float, Float, Float> {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx /= len; ny /= len; nz /= len
        return Triple(nx, ny, nz)
    }

    private fun hexToRgb(hex: Long): Triple<Float, Float, Float> {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return Triple(r, g, b)
    }

    private fun createShaderProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            out vec3 vWorldPos;
            out vec3 vNormal;
            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPos, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize(mat3(uModelMatrix) * aNormal);
                gl_Position = uMvpMatrix * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vNormal;
            uniform vec3 uColor;
            uniform vec3 uSunDir;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uAmbientStrength;
            uniform float uSpecularStrength;
            out vec4 fragColor;
            void main() {
                vec3 norm = normalize(vNormal);
                vec3 sun = normalize(uSunDir);
                float diff = max(dot(norm, sun), 0.0);
                vec3 viewDir = normalize(uCamPos - vWorldPos);
                vec3 halfDir = normalize(sun + viewDir);
                float spec = pow(max(dot(norm, halfDir), 0.0), 24.0) * uSpecularStrength;

                // Subtle tactical terrain grid lines on ground
                float grid = 1.0;
                if (abs(vNormal.y) > 0.85 && vWorldPos.y < 3.5) {
                    float gx = abs(fract(vWorldPos.x * 0.1) - 0.5);
                    float gz = abs(fract(vWorldPos.z * 0.1) - 0.5);
                    if (gx > 0.47 || gz > 0.47) {
                        grid = 0.88;
                    }
                }

                vec3 lit = uColor * grid * (uAmbientStrength + diff * 0.82) + vec3(1.0, 0.96, 0.88) * spec;
                float dist = length(uCamPos - vWorldPos);
                float fogFactor = clamp(exp(-pow(dist * uFogDensity, 1.6)), 0.0, 1.0);
                vec3 finalCol = mix(uFogColor, lit, fogFactor);
                fragColor = vec4(finalCol, 1.0);
            }
        """
    }
}
