package com.example.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.game.AntiCheatEngine
import com.example.game.AntiCheatReport
import com.example.game.CombatSimulation
import com.example.game.GlBattleRenderer
import com.example.game.SoundEffectsEngine
import com.example.model.GameCatalog
import com.example.model.GraphicsQuality
import com.example.model.MatchRoom
import com.example.model.PlayerProfile
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.BattleLaunchConfig
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BattleScreen(
    config: BattleLaunchConfig,
    profile: PlayerProfile,
    currentUserId: String,
    activeRoom: MatchRoom?,
    antiCheatEngine: AntiCheatEngine,
    soundEngine: SoundEffectsEngine,
    onOpenGraphicsSettings: () -> Unit,
    onSyncTelemetry: (String, Boolean, Double, Double, Double, Long, Long) -> Unit,
    onSubmitOutcome: (Boolean, Int, Int, Int, Int, Int, AntiCheatReport) -> Unit,
    onExitBattle: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val quality = GraphicsQuality.safeValueOf(profile.graphicsQuality)
    val loadoutStats = remember(profile, config.theatre) {
        GameCatalog.computeLoadoutStats(profile, config.theatre)
    }
    val skin = remember(profile.equippedSkin) {
        GameCatalog.skins.find { it.id == profile.equippedSkin } ?: GameCatalog.skins.first()
    }

    val sim = remember(config.missionId) {
        CombatSimulation(
            theatre = config.theatre,
            initialWeather = config.weather,
            dynamicWeatherShift = config.dynamicWeatherShift,
            stats = loadoutStats,
            playerSkinPrimaryHex = skin.primaryColorHex,
            playerSkinAccentHex = skin.accentColorHex,
            enemyCount = config.enemyCount,
            structureCount = config.destructibleTargets,
            isMultiplayer = config.roomId != null,
            isHost = config.isHost,
            hasStormSkill = "skl_storm" in profile.unlockedSkills,
            hasRepairSkill = "skl_repair" in profile.unlockedSkills,
            antiCheatEngine = antiCheatEngine,
            soundEngine = soundEngine
        )
    }

    var currentFps by remember { mutableIntStateOf(60) }
    val renderer = remember(sim) {
        GlBattleRenderer(sim, quality) { fps -> currentFps = fps }
    }
    LaunchedEffect(quality) {
        renderer.graphicsQuality = quality
    }

    var hud by remember { mutableStateOf(sim.snapshotHud()) }
    var pausedModal by remember { mutableStateOf(false) }
    var outcomeSubmitted by remember { mutableStateOf(false) }
    var finalReport by remember { mutableStateOf<AntiCheatReport?>(null) }

    BackHandler {
        pausedModal = !pausedModal
        sim.paused = pausedModal
    }

    // Update remote opponent from Firestore MatchRoom
    LaunchedEffect(activeRoom) {
        val room = activeRoom ?: return@LaunchedEffect
        if (config.roomId == room.id && room.guestUid.isNotBlank()) {
            if (config.isHost) {
                sim.updateRemoteOpponent(
                    room.guestX.toFloat(),
                    room.guestZ.toFloat(),
                    room.guestYaw.toFloat(),
                    room.guestHp.toInt()
                )
            } else {
                sim.updateRemoteOpponent(
                    room.hostX.toFloat(),
                    room.hostZ.toFloat(),
                    room.hostYaw.toFloat(),
                    room.hostHp.toInt()
                )
            }
        }
    }

    // Periodic HUD refresh & Multiplayer telemetry sync
    LaunchedEffect(sim) {
        var tick = 0
        while (true) {
            hud = sim.snapshotHud()
            if (config.roomId != null && tick % 4 == 0 && !hud.matchFinished) {
                onSyncTelemetry(
                    config.roomId,
                    config.isHost,
                    hud.playerX.toDouble(),
                    hud.playerZ.toDouble(),
                    hud.playerYawDeg.toDouble(),
                    hud.hp.toLong(),
                    hud.score.toLong()
                )
            }
            if (hud.matchFinished && !outcomeSubmitted) {
                outcomeSubmitted = true
                val acc = if (hud.shotsFired > 0) ((hud.shotsHit * 100) / hud.shotsFired).coerceIn(0, 100) else 0
                val report = antiCheatEngine.auditMatchOutcome(
                    uid = currentUserId,
                    theatre = config.theatre,
                    missionId = config.missionId,
                    kills = hud.kills,
                    damageDealt = hud.damageDealt,
                    shotsFired = hud.shotsFired,
                    durationSec = hud.elapsedSec.coerceAtLeast(1)
                )
                finalReport = report
                if (hud.victory) soundEngine.playCoinReward()
                onSubmitOutcome(
                    hud.victory,
                    hud.kills,
                    hud.damageDealt,
                    hud.shotsFired,
                    acc,
                    hud.elapsedSec.coerceAtLeast(1),
                    report
                )
            }
            tick++
            delay(100L)
        }
    }

    val glViewRef = remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> glViewRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glViewRef.value?.onPause()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TacticalBlack)) {
        // 1. Hardware OpenGL ES 3.0 3D Viewport
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glViewRef.value = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sim) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        sim.aimDeltaYaw += dragAmount.x * 0.24f
                        sim.aimDeltaPitch += dragAmount.y * 0.12f
                    }
                }
        )

        // 2. Center Tactical Crosshair Reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = if (sim.adsZoomActive) 22f else 14f
            drawCircle(
                color = if (hud.primaryCooldownRatio > 0.1f) AmberGold else ShieldEmerald,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.2f)
            )
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx - r - 10f, cy), Offset(cx - r + 4f, cy), 2f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx + r - 4f, cy), Offset(cx + r + 10f, cy), 2f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx, cy - r - 10f), Offset(cx, cy - r + 4f), 2f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx, cy + r - 4f), Offset(cx, cy + r + 10f), 2f)
        }

        // 3. Top Tactical HUD Bar & Radar Minimap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Mission, Kills, HP, Integrity
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xB80A0D12))
                    .border(1.dp, ArmorBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Anti-Cheat",
                        tint = if (hud.integrityPassed) ShieldEmerald else CrimsonAlert,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${config.missionTitle} • ${currentFps} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "KILLS: ${hud.kills}/${hud.totalTargets} • DEMOLISHED: ${hud.structuresDestroyed}/${hud.totalStructures} • SCORE: ${hud.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGoldBright
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ARMOR", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    LinearProgressIndicator(
                        progress = { (hud.hp.toFloat() / hud.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = if (hud.hp > hud.maxHp * 0.35f) ShieldEmerald else CrimsonAlert,
                        trackColor = GunmetalElevated,
                        modifier = Modifier
                            .size(width = 140.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text("${hud.hp}/${hud.maxHp}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
                Text(
                    text = hud.killFeedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }

            // Right: Radar Minimap + Weather + Pause/Settings
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { sim.cycleWeather() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xB80A0D12))
                            .border(1.dp, TacticalCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .testTag("battle_weather_toggle")
                    ) {
                        Icon(Icons.Default.Thunderstorm, contentDescription = "Cycle Weather", tint = TacticalCyan)
                    }
                    IconButton(
                        onClick = {
                            pausedModal = true
                            sim.paused = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xB80A0D12))
                            .border(1.dp, ArmorBorder, RoundedCornerShape(10.dp))
                            .testTag("battle_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = TextPrimary)
                    }
                }

                // Live Tactical Radar
                RadarMinimap(hud = hud)
            }
        }

        // 4. Bottom Combat Controls (Virtual Joystick Left, Fire/Special/Zoom/Repair Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            VirtualJoystick(
                onMove = { mx, my ->
                    sim.moveInputX = mx
                    sim.moveInputY = my
                }
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ADS Zoom Toggle
                    CombatActionButton(
                        icon = Icons.Default.CenterFocusStrong,
                        label = if (sim.adsZoomActive) "ZOOM ON" else "SCOPE",
                        color = if (sim.adsZoomActive) TacticalCyan else GunmetalElevated,
                        contentColor = if (sim.adsZoomActive) TacticalBlack else TextPrimary,
                        sizeDp = 54,
                        testTag = "battle_scope_btn",
                        onClick = { sim.adsZoomActive = !sim.adsZoomActive }
                    )
                    // Field Repair
                    CombatActionButton(
                        icon = Icons.Default.Build,
                        label = "FIX (${hud.repairCharges})",
                        color = ShieldEmerald,
                        contentColor = TacticalBlack,
                        sizeDp = 54,
                        testTag = "battle_repair_btn",
                        onClick = { sim.triggerRepair() }
                    )
                    // Heavy Missile / Salvo
                    CombatActionButton(
                        icon = Icons.Default.RocketLaunch,
                        label = if (hud.specialCooldownRatio > 0.01f) "WAIT" else "SALVO",
                        color = if (hud.specialCooldownRatio > 0.01f) GunmetalSurface else AmberGoldBright,
                        contentColor = if (hud.specialCooldownRatio > 0.01f) TextSecondary else TacticalBlack,
                        sizeDp = 58,
                        testTag = "battle_salvo_btn",
                        onClick = { sim.triggerSpecialAttack() }
                    )
                }

                // Primary Hold-to-Fire Trigger
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(CrimsonAlert.copy(alpha = 0.88f))
                        .border(3.dp, AmberGoldBright, CircleShape)
                        .pointerInput(sim) {
                            detectTapGestures(
                                onPress = {
                                    sim.isFiringPrimary = true
                                    tryAwaitRelease()
                                    sim.isFiringPrimary = false
                                }
                            )
                        }
                        .testTag("battle_fire_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = "Fire", tint = TextPrimary, modifier = Modifier.size(30.dp))
                        Text("FIRE", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Pause Menu Modal
        if (pausedModal && !hud.matchFinished) {
            Dialog(onDismissRequest = {
                pausedModal = false
                sim.paused = false
            }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GunmetalDark,
                    modifier = Modifier.border(1.dp, ArmorBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("BATTLE PAUSED", style = MaterialTheme.typography.headlineMedium, color = AmberGoldBright)
                        Text(
                            "Weather: ${hud.weather.displayName} • Quality: ${quality.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Button(
                            onClick = {
                                pausedModal = false
                                sim.paused = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = TacticalBlack),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("RESUME COMBAT")
                        }
                        Button(
                            onClick = onOpenGraphicsSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = GunmetalElevated, contentColor = TextPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("GRAPHICS & AUDIO SETTINGS")
                        }
                        Button(
                            onClick = onExitBattle,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = TextPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("retreat_battle_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("RETREAT TO COMMAND HUB")
                        }
                    }
                }
            }
        }

        // 6. Victory / Defeat Debriefing Modal
        if (hud.matchFinished) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GunmetalDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.5.dp,
                            if (hud.victory) ShieldEmerald else CrimsonAlert,
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (hud.victory) "MISSION VICTORY" else "UNIT DESTROYED",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (hud.victory) ShieldEmerald else CrimsonAlert
                        )
                        Text(
                            text = "${config.missionTitle} • ${hud.elapsedSec}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GunmetalSurface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Hostiles Neutralized: ${hud.kills}/${hud.totalTargets}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Structures Demolished: ${hud.structuresDestroyed}/${hud.totalStructures}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Damage Dealt: ${hud.damageDealt}", color = AmberGoldBright, style = MaterialTheme.typography.bodyMedium)
                            Text("Combat Score: ${hud.score}", color = TacticalCyan, style = MaterialTheme.typography.bodyMedium)
                            finalReport?.let { rep ->
                                Text(
                                    text = "Anti-Cheat: ${if (rep.integrityPassed) "PASSED" else "FLAGGED"} (${rep.checksum})",
                                    color = if (rep.integrityPassed) ShieldEmerald else CrimsonAlert,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Button(
                            onClick = onExitBattle,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = TacticalBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("finish_debrief_btn")
                        ) {
                            Text("RETURN TO COMMAND HUB", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarMinimap(hud: com.example.game.HudSnapshot) {
    Box(
        modifier = Modifier
            .size(98.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xC40A0D12))
            .border(1.dp, TacticalCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val scale = size.width / 220f
            drawCircle(TacticalCyan.copy(alpha = 0.25f), radius = size.width * 0.45f, center = Offset(cx, cy), style = Stroke(1.5f))
            drawCircle(TacticalCyan.copy(alpha = 0.15f), radius = size.width * 0.25f, center = Offset(cx, cy), style = Stroke(1f))

            // Structures
            for ((sx, sz, destroyed) in hud.structureBlips) {
                if (destroyed) continue
                val dx = (sx - hud.playerX) * scale
                val dz = (sz - hud.playerZ) * scale
                val px = (cx + dx).coerceIn(4f, size.width - 4f)
                val py = (cy - dz).coerceIn(4f, size.height - 4f)
                drawCircle(Color(0xFF94A3B8), radius = 2.5f, center = Offset(px, py))
            }

            // Enemies
            for ((ex, ez) in hud.enemyBlips) {
                val dx = (ex - hud.playerX) * scale
                val dz = (ez - hud.playerZ) * scale
                val px = (cx + dx).coerceIn(4f, size.width - 4f)
                val py = (cy - dz).coerceIn(4f, size.height - 4f)
                drawCircle(CrimsonAlert, radius = 4f, center = Offset(px, py))
            }

            // Player center + heading tick
            val rad = Math.toRadians(hud.playerYawDeg.toDouble()).toFloat()
            drawCircle(ShieldEmerald, radius = 4.5f, center = Offset(cx, cy))
            drawLine(
                ShieldEmerald,
                Offset(cx, cy),
                Offset(cx + sin(rad) * 12f, cy - cos(rad) * 12f),
                strokeWidth = 2.5f
            )
        }
        Text(
            text = hud.weather.name,
            style = MaterialTheme.typography.labelSmall,
            color = TacticalCyan,
            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp)
        )
    }
}

@Composable
private fun VirtualJoystick(
    onMove: (Float, Float) -> Unit
) {
    var knobX by remember { mutableStateOf(0f) }
    var knobY by remember { mutableStateOf(0f) }
    val maxRadiusPx = 110f

    Box(
        modifier = Modifier
            .size(126.dp)
            .clip(CircleShape)
            .background(Color(0x88121720))
            .border(2.dp, AmberGold.copy(alpha = 0.55f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobX = 0f
                        knobY = 0f
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobX = 0f
                        knobY = 0f
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val nx = knobX + dragAmount.x
                        val ny = knobY + dragAmount.y
                        val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()
                        if (dist > maxRadiusPx) {
                            knobX = (nx / dist) * maxRadiusPx
                            knobY = (ny / dist) * maxRadiusPx
                        } else {
                            knobX = nx
                            knobY = ny
                        }
                        onMove(knobX / maxRadiusPx, -knobY / maxRadiusPx)
                    }
                )
            }
            .testTag("battle_joystick"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(AmberGold.copy(alpha = 0.9f))
                .border(2.dp, TextPrimary, CircleShape)
        )
    }
}

@Composable
private fun CombatActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    contentColor: Color,
    sizeDp: Int,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.9f))
            .border(1.5.dp, TextPrimary.copy(alpha = 0.4f), CircleShape)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}
