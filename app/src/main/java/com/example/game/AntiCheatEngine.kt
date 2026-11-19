package com.example.game

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.WarTheatre
import java.security.MessageDigest
import kotlin.math.hypot

data class AntiCheatReport(
    val integrityPassed: Boolean,
    val integrityScore: Int,
    val flags: List<String>,
    val checksum: String,
    val signatureVerified: Boolean
)

class AntiCheatEngine(private val appContext: Context? = null) {

    private val detectedFlags = mutableSetOf<String>()
    private var lastPositionTimeMs: Long = 0L
    private var lastX: Float = 0f
    private var lastZ: Float = 0f
    private var lastShotTimeMs: Long = 0L
    private var totalShotsAudited: Int = 0
    private var totalDamageAudited: Int = 0
    private var signatureVerified: Boolean = true

    fun resetMatchAudit(startX: Float, startZ: Float) {
        detectedFlags.clear()
        lastPositionTimeMs = System.currentTimeMillis()
        lastX = startX
        lastZ = startZ
        lastShotTimeMs = 0L
        totalShotsAudited = 0
        totalDamageAudited = 0
        signatureVerified = verifyAppPackageIntegrity()
        if (!signatureVerified) {
            detectedFlags.add("PACKAGE_SIGNATURE_MISMATCH")
        }
    }

    private fun verifyAppPackageIntegrity(): Boolean {
        val ctx = appContext ?: return true
        return try {
            val expectedPkg = "shahab.herosofhonour.com"
            if (ctx.packageName != expectedPkg && ctx.packageName != "com.example") {
                return false
            }
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
            }
            pkgInfo != null
        } catch (_: Exception) {
            true
        }
    }

    fun auditMovementStep(x: Float, z: Float, maxAllowedSpeed: Float) {
        val now = System.currentTimeMillis()
        val dtSec = ((now - lastPositionTimeMs) / 1000f).coerceAtLeast(0.016f)
        val dist = hypot((x - lastX).toDouble(), (z - lastZ).toDouble()).toFloat()
        val observedSpeed = dist / dtSec
        // Allow generous tolerance for frame spikes & jet boost, flag extreme teleport hacks
        if (observedSpeed > maxAllowedSpeed * 2.4f && dist > 18f) {
            detectedFlags.add("SPEED_ANOMALY")
        }
        lastX = x
        lastZ = z
        lastPositionTimeMs = now
    }

    fun auditWeaponFire(minCooldownSec: Float, damageDealt: Int): Boolean {
        val now = System.currentTimeMillis()
        var valid = true
        if (lastShotTimeMs > 0L) {
            val deltaSec = (now - lastShotTimeMs) / 1000f
            if (deltaSec < minCooldownSec * 0.45f) {
                detectedFlags.add("FIRE_RATE_EXPLOIT")
                valid = false
            }
        }
        if (damageDealt > 220) {
            detectedFlags.add("DAMAGE_TAMPER")
            valid = false
        }
        if (!valid) return false
        lastShotTimeMs = now
        totalShotsAudited++
        totalDamageAudited += damageDealt
        return true
    }

    fun auditMatchOutcome(
        uid: String,
        theatre: WarTheatre,
        missionId: String,
        kills: Int,
        damageDealt: Int,
        shotsFired: Int,
        durationSec: Int
    ): AntiCheatReport {
        if (durationSec < 1) {
            detectedFlags.add("CLOCK_SKEW")
        }
        if (kills > 120 || damageDealt > 40000) {
            detectedFlags.add("STAT_OVERFLOW")
        }
        if (shotsFired == 0 && kills > 2) {
            detectedFlags.add("GHOST_KILL_ANOMALY")
        }
        val passed = detectedFlags.isEmpty()
        val score = (100 - detectedFlags.size * 20).coerceIn(0, 100)
        val rawPayload = "$uid|${theatre.name}|$missionId|$kills|$damageDealt|$shotsFired|$durationSec|$passed|HerosOfHonour_AC_v2"
        val checksum = sha256Hex(rawPayload).take(24).uppercase()
        return AntiCheatReport(
            integrityPassed = passed,
            integrityScore = score,
            flags = detectedFlags.toList(),
            checksum = checksum,
            signatureVerified = signatureVerified
        )
    }

    fun generateSyncHash(uid: String, score: Long, hp: Long): String {
        return sha256Hex("$uid:$score:$hp:HOH_SYNC").take(16).uppercase()
    }

    private fun sha256Hex(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString(16)
        }
    }
}
