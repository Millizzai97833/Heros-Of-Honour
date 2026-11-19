package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "Commander",
    val coins: Long = 1000L,
    val xp: Long = 0L,
    val level: Long = 1L,
    val skillPoints: Long = 2L,
    val eloRating: Long = 1000L,
    val wins: Long = 0L,
    val matchesPlayed: Long = 0L,
    val loginStreak: Long = 0L,
    val lastDailyClaimDay: String = "",
    val unlockedSkins: List<String> = listOf("skin_olive"),
    val unlockedWeapons: List<String> = listOf(
        "wpn_tank_120", "wpn_air_vulcan", "wpn_navy_406", "wpn_inf_m416",
        "muz_std", "opt_holo", "arm_composite", "amm_apfsds"
    ),
    val unlockedSkills: List<String> = emptyList(),
    val completedMissions: List<String> = emptyList(),
    val equippedSkin: String = "skin_olive",
    val equippedTheatre: String = WarTheatre.TANK.name,
    val equippedWeapon: String = "wpn_tank_120",
    val equippedMuzzle: String = "muz_std",
    val equippedOptic: String = "opt_holo",
    val equippedArmor: String = "arm_composite",
    val equippedAmmo: String = "amm_apfsds",
    val graphicsQuality: String = GraphicsQuality.HIGH.name,
    val integrityScore: Long = 100L,
    val adCooldownEpochSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchLog(
    @DocumentId val id: String = "",
    val mode: String = "CAMPAIGN",
    val theatre: String = WarTheatre.TANK.name,
    val missionId: String = "",
    val kills: Long = 0L,
    val damageDealt: Long = 0L,
    val shotsFired: Long = 0L,
    val accuracyPct: Long = 0L,
    val durationSec: Long = 1L,
    val integrityPassed: Boolean = true,
    val cheatFlags: List<String> = emptyList(),
    val telemetryChecksum: String = "",
    val coinsAwarded: Long = 0L,
    val xpAwarded: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchRoom(
    @DocumentId val id: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val guestUid: String = "",
    val guestName: String = "",
    val memberUids: List<String> = emptyList(),
    val theatre: String = WarTheatre.TANK.name,
    val weather: String = WeatherType.CLEAR.name,
    val status: String = "WAITING",
    val hostScore: Long = 0L,
    val guestScore: Long = 0L,
    val hostHp: Long = 100L,
    val guestHp: Long = 100L,
    val hostX: Double = 0.0,
    val hostZ: Double = -45.0,
    val hostYaw: Double = 0.0,
    val guestX: Double = 0.0,
    val guestZ: Double = 45.0,
    val guestYaw: Double = 180.0,
    val winnerUid: String = "",
    val antiCheatHash: String = "VERIFIED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "",
    val eloRating: Long = 1000L,
    val wins: Long = 0L,
    val level: Long = 1L,
    val favouriteTheatre: String = WarTheatre.TANK.name,
    val verified: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
