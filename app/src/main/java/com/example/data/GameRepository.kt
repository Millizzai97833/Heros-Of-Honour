package com.example.data

import android.content.Context
import com.example.R
import com.example.game.AntiCheatReport
import com.example.model.GameCatalog
import com.example.model.GearItem
import com.example.model.GearSlot
import com.example.model.GraphicsQuality
import com.example.model.LeaderboardEntry
import com.example.model.MatchLog
import com.example.model.MatchRoom
import com.example.model.PlayerProfile
import com.example.model.ShopBundle
import com.example.model.SkinItem
import com.example.model.WarTheatre
import com.example.model.WeatherType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GameRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun currentUtcDayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun ensurePlayerProfile(userId: String, displayName: String?): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(userId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeCallsign = (displayName?.trim()?.takeIf { it.length >= 2 } ?: "Commander")
                    .take(30)
                val initialPayload = mapOf(
                    "userId" to uid,
                    "callsign" to safeCallsign,
                    "coins" to 1000L,
                    "xp" to 0L,
                    "level" to 1L,
                    "skillPoints" to 2L,
                    "eloRating" to 1000L,
                    "wins" to 0L,
                    "matchesPlayed" to 0L,
                    "loginStreak" to 0L,
                    "lastDailyClaimDay" to "",
                    "unlockedSkins" to listOf("skin_olive"),
                    "unlockedWeapons" to listOf(
                        "wpn_tank_120", "wpn_air_vulcan", "wpn_navy_406", "wpn_inf_m416",
                        "muz_std", "opt_holo", "arm_composite", "amm_apfsds"
                    ),
                    "unlockedSkills" to emptyList<String>(),
                    "completedMissions" to emptyList<String>(),
                    "equippedSkin" to "skin_olive",
                    "equippedTheatre" to WarTheatre.TANK.name,
                    "equippedWeapon" to "wpn_tank_120",
                    "equippedMuzzle" to "muz_std",
                    "equippedOptic" to "opt_holo",
                    "equippedArmor" to "arm_composite",
                    "equippedAmmo" to "amm_apfsds",
                    "graphicsQuality" to GraphicsQuality.HIGH.name,
                    "integrityScore" to 100L,
                    "adCooldownEpochSec" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(initialPayload).await()
                syncLeaderboardEntry(
                    userId = uid,
                    callsign = safeCallsign,
                    eloRating = 1000L,
                    wins = 0L,
                    level = 1L,
                    theatre = WarTheatre.TANK
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observePlayerProfile(userId: String): Flow<PlayerProfile> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Cross-user observation blocked." }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PlayerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: PlayerProfile(userId = userId)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMatchLogs(userId: String): Flow<List<MatchLog>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val colRef = db.collection("users").document(userId).collection("match_logs")
        emitAll(
            colRef.limit(20).snapshots()
                .map { snap ->
                    snap.toObjects(
                        MatchLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeWaitingRooms(): Flow<List<MatchRoom>> = flow {
        val uid = requireUserId()
        val query = db.collection("match_rooms").whereEqualTo("status", "WAITING")
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        MatchRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "match_rooms")
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<MatchRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val query = db.collection("match_rooms").whereArrayContains("memberUids", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        MatchRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "match_rooms")
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<MatchRoom?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("match_rooms").document(roomId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        MatchRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLeaderboard(): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId()
        val query = db.collection("leaderboard")
            .whereEqualTo("verified", true)
            .limit(50)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.eloRating }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "leaderboard")
                    }
                    throw error
                }
        )
    }

    suspend fun claimDailyLoginReward(profile: PlayerProfile): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            val today = currentUtcDayKey()
            if (profile.lastDailyClaimDay == today) {
                return Result.failure(IllegalStateException("Today's daily reward has already been claimed."))
            }
            val nextStreak = if (profile.loginStreak >= 7L) 1L else profile.loginStreak + 1L
            val reward = GameCatalog.dailyRewards[(nextStreak - 1).toInt().coerceIn(0, 6)]
            val newSkins = if (reward.bonusSkinId != null && reward.bonusSkinId !in profile.unlockedSkins) {
                (profile.unlockedSkins + reward.bonusSkinId).take(30)
            } else {
                profile.unlockedSkins
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "coins" to (profile.coins + reward.coins).coerceAtMost(10_000_000L),
                    "skillPoints" to (profile.skillPoints + reward.skillPoints).coerceAtMost(500L),
                    "loginStreak" to nextStreak,
                    "lastDailyClaimDay" to today,
                    "unlockedSkins" to newSkins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val skinMsg = reward.bonusSkinId?.let { " + Rare Skin!" } ?: ""
            Result.success("Day $nextStreak Claimed: +${reward.coins} Coins${if (reward.skillPoints > 0) " +${reward.skillPoints} SP" else ""}$skinMsg")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun claimRewardedAdCoins(profile: PlayerProfile): Result<Long> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            val nowSec = System.currentTimeMillis() / 1000L
            if (nowSec - profile.adCooldownEpochSec < 15L) {
                return Result.failure(IllegalStateException("Please wait a moment before watching another rewarded ad."))
            }
            val rewardCoins = 250L
            db.collection("users").document(uid).update(
                mapOf(
                    "coins" to (profile.coins + rewardCoins).coerceAtMost(10_000_000L),
                    "adCooldownEpochSec" to nowSec,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(rewardCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun purchaseGearItem(profile: PlayerProfile, item: GearItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            if (item.id in profile.unlockedWeapons) return Result.success(Unit)
            if (profile.coins < item.costCoins) {
                return Result.failure(IllegalStateException("Not enough coins. Watch a Rewarded Ad or complete missions!"))
            }
            val updatedWeapons = (profile.unlockedWeapons + item.id).distinct().take(40)
            val updates = mutableMapOf<String, Any>(
                "coins" to (profile.coins - item.costCoins),
                "unlockedWeapons" to updatedWeapons,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            when (item.slot) {
                GearSlot.WEAPON -> updates["equippedWeapon"] = item.id
                GearSlot.MUZZLE -> updates["equippedMuzzle"] = item.id
                GearSlot.OPTIC -> updates["equippedOptic"] = item.id
                GearSlot.ARMOR -> updates["equippedArmor"] = item.id
                GearSlot.AMMO -> updates["equippedAmmo"] = item.id
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun equipGearItem(profile: PlayerProfile, item: GearItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            if (item.id !in profile.unlockedWeapons) {
                return Result.failure(IllegalStateException("Unlock this gear first."))
            }
            val field = when (item.slot) {
                GearSlot.WEAPON -> "equippedWeapon"
                GearSlot.MUZZLE -> "equippedMuzzle"
                GearSlot.OPTIC -> "equippedOptic"
                GearSlot.ARMOR -> "equippedArmor"
                GearSlot.AMMO -> "equippedAmmo"
            }
            db.collection("users").document(uid).update(
                mapOf(
                    field to item.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun selectTheatre(profile: PlayerProfile, theatre: WarTheatre): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            val matchingWeapon = GameCatalog.gearItems
                .firstOrNull { it.theatre == theatre && it.id in profile.unlockedWeapons }?.id
                ?: GameCatalog.defaultWeaponForTheatre(theatre)
            db.collection("users").document(uid).update(
                mapOf(
                    "equippedTheatre" to theatre.name,
                    "equippedWeapon" to matchingWeapon,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun purchaseOrEquipSkin(profile: PlayerProfile, skin: SkinItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            if (skin.id in profile.unlockedSkins) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "equippedSkin" to skin.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success(Unit)
            }
            if (profile.coins < skin.costCoins) {
                return Result.failure(IllegalStateException("Insufficient coins for ${skin.name}."))
            }
            val newSkins = (profile.unlockedSkins + skin.id).distinct().take(30)
            db.collection("users").document(uid).update(
                mapOf(
                    "coins" to (profile.coins - skin.costCoins),
                    "unlockedSkins" to newSkins,
                    "equippedSkin" to skin.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun purchaseBundle(profile: PlayerProfile, bundle: ShopBundle): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            if (profile.coins < bundle.bundleCostCoins) {
                return Result.failure(IllegalStateException("Need ${bundle.bundleCostCoins} coins for ${bundle.title}."))
            }
            val newSkins = (profile.unlockedSkins + bundle.includedSkinId).distinct().take(30)
            val newWeapons = (profile.unlockedWeapons + bundle.includedWeaponId).distinct().take(40)
            val netCoins = (profile.coins - bundle.bundleCostCoins + bundle.bonusCoins).coerceAtLeast(0L)
            db.collection("users").document(uid).update(
                mapOf(
                    "coins" to netCoins,
                    "unlockedSkins" to newSkins,
                    "unlockedWeapons" to newWeapons,
                    "equippedSkin" to bundle.includedSkinId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unlockCombatSkill(profile: PlayerProfile, skillId: String, costSp: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            if (skillId in profile.unlockedSkills) return Result.success(Unit)
            if (profile.skillPoints < costSp) {
                return Result.failure(IllegalStateException("Not enough Skill Points ($costSp SP required)."))
            }
            val updated = (profile.unlockedSkills + skillId).distinct().take(30)
            db.collection("users").document(uid).update(
                mapOf(
                    "skillPoints" to (profile.skillPoints - costSp),
                    "unlockedSkills" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateGraphicsQuality(quality: GraphicsQuality): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "graphicsQuality" to quality.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchOutcome(
        profile: PlayerProfile,
        mode: String,
        theatre: WarTheatre,
        missionId: String,
        won: Boolean,
        kills: Int,
        damageDealt: Int,
        shotsFired: Int,
        accuracyPct: Int,
        durationSec: Int,
        baseCoins: Long,
        baseXp: Long,
        report: AntiCheatReport
    ): Result<Pair<Long, Long>> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val userDocPath = "users/$uid"
        return try {
            val coinsAwarded = if (report.integrityPassed) {
                baseCoins.coerceIn(0L, 5000L)
            } else {
                0L
            }
            val xpAwarded = if (report.integrityPassed) {
                baseXp.coerceIn(0L, 3000L)
            } else {
                0L
            }

            // 1. Write append-only anti-cheat audit log
            val logRef = db.collection("users").document(uid).collection("match_logs").document()
            val logPayload = mapOf(
                "mode" to mode,
                "theatre" to theatre.name,
                "missionId" to missionId.take(64).ifBlank { "skirmish" },
                "kills" to kills.toLong().coerceIn(0L, 200L),
                "damageDealt" to damageDealt.toLong().coerceIn(0L, 50000L),
                "shotsFired" to shotsFired.toLong().coerceIn(0L, 2000L),
                "accuracyPct" to accuracyPct.toLong().coerceIn(0L, 100L),
                "durationSec" to durationSec.toLong().coerceIn(1L, 3600L),
                "integrityPassed" to report.integrityPassed,
                "cheatFlags" to report.flags.take(10),
                "telemetryChecksum" to report.checksum,
                "coinsAwarded" to coinsAwarded,
                "xpAwarded" to xpAwarded,
                "createdAt" to FieldValue.serverTimestamp()
            )
            logRef.set(logPayload).await()

            // 2. Update PlayerProfile progression
            val newXp = (profile.xp + xpAwarded).coerceAtMost(10_000_000L)
            val newLevel = (1L + newXp / 500L).coerceIn(1L, 200L)
            val levelUps = (newLevel - profile.level).coerceAtLeast(0L)
            val newSkillPoints = (profile.skillPoints + levelUps).coerceAtMost(500L)
            val newWins = if (won) profile.wins + 1L else profile.wins
            val eloDelta = if (mode == "MULTIPLAYER") (if (won) 28L else -12L) else (if (won) 8L else 0L)
            val newElo = (profile.eloRating + eloDelta).coerceIn(100L, 10_000L)
            val newCompleted = if (won && mode == "CAMPAIGN" && missionId.isNotBlank()) {
                (profile.completedMissions + missionId).distinct().take(30)
            } else {
                profile.completedMissions
            }
            val newIntegrity = if (report.integrityPassed) {
                ((profile.integrityScore + 2L).coerceAtMost(100L))
            } else {
                report.integrityScore.toLong().coerceIn(0L, 100L)
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "coins" to (profile.coins + coinsAwarded).coerceAtMost(10_000_000L),
                    "xp" to newXp,
                    "level" to newLevel,
                    "skillPoints" to newSkillPoints,
                    "wins" to newWins,
                    "matchesPlayed" to (profile.matchesPlayed + 1L),
                    "eloRating" to newElo,
                    "completedMissions" to newCompleted,
                    "integrityScore" to newIntegrity,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 3. Sync public verified leaderboard
            syncLeaderboardEntry(
                userId = uid,
                callsign = profile.callsign,
                eloRating = newElo,
                wins = newWins,
                level = newLevel,
                theatre = theatre
            )

            Result.success(coinsAwarded to xpAwarded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocPath)
            Result.failure(e)
        }
    }

    private suspend fun syncLeaderboardEntry(
        userId: String,
        callsign: String,
        eloRating: Long,
        wins: Long,
        level: Long,
        theatre: WarTheatre
    ) {
        val docRef = db.collection("leaderboard").document(userId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "callsign" to callsign.take(30).ifBlank { "Commander" },
                        "eloRating" to eloRating.coerceIn(100L, 10_000L),
                        "wins" to wins.coerceAtLeast(0L),
                        "level" to level.coerceIn(1L, 200L),
                        "favouriteTheatre" to theatre.name,
                        "verified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "callsign" to callsign.take(30).ifBlank { "Commander" },
                        "eloRating" to eloRating.coerceIn(100L, 10_000L),
                        "wins" to wins.coerceAtLeast(0L),
                        "level" to level.coerceIn(1L, 200L),
                        "favouriteTheatre" to theatre.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    suspend fun createMatchRoom(
        hostProfile: PlayerProfile,
        theatre: WarTheatre,
        weather: WeatherType,
        antiCheatHash: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("match_rooms").document()
        return try {
            val payload = mapOf(
                "hostUid" to uid,
                "hostName" to hostProfile.callsign.take(30).ifBlank { "Commander" },
                "guestUid" to "",
                "guestName" to "",
                "memberUids" to listOf(uid),
                "theatre" to theatre.name,
                "weather" to weather.name,
                "status" to "WAITING",
                "hostScore" to 0L,
                "guestScore" to 0L,
                "hostHp" to 220L,
                "guestHp" to 220L,
                "hostX" to 0.0,
                "hostZ" to -45.0,
                "hostYaw" to 0.0,
                "guestX" to 0.0,
                "guestZ" to 45.0,
                "guestYaw" to 180.0,
                "winnerUid" to "",
                "antiCheatHash" to antiCheatHash.take(64).ifBlank { "VERIFIED" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinMatchRoom(room: MatchRoom, guestProfile: PlayerProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "match_rooms/${room.id}"
        return try {
            if (room.hostUid == uid) return Result.success(Unit)
            db.collection("match_rooms").document(room.id).update(
                mapOf(
                    "guestUid" to uid,
                    "guestName" to guestProfile.callsign.take(30).ifBlank { "Challenger" },
                    "memberUids" to listOf(room.hostUid, uid),
                    "status" to "ACTIVE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startRoomMatchAsHost(roomId: String): Result<Unit> {
        val docPath = "match_rooms/$roomId"
        return try {
            requireUserId()
            db.collection("match_rooms").document(roomId).update(
                mapOf(
                    "status" to "ACTIVE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncRoomTelemetry(
        roomId: String,
        isHost: Boolean,
        x: Double,
        z: Double,
        yaw: Double,
        hp: Long,
        score: Long,
        antiCheatHash: String
    ): Result<Unit> {
        val docPath = "match_rooms/$roomId"
        return try {
            requireUserId()
            val clampedX = x.coerceIn(-190.0, 190.0)
            val clampedZ = z.coerceIn(-190.0, 190.0)
            val clampedYaw = yaw.coerceIn(-360.0, 360.0)
            val clampedHp = hp.coerceIn(0L, 1000L)
            val clampedScore = score.coerceIn(0L, 500L)
            val updates = if (isHost) {
                mapOf(
                    "hostX" to clampedX,
                    "hostZ" to clampedZ,
                    "hostYaw" to clampedYaw,
                    "hostHp" to clampedHp,
                    "hostScore" to clampedScore,
                    "antiCheatHash" to antiCheatHash.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "guestX" to clampedX,
                    "guestZ" to clampedZ,
                    "guestYaw" to clampedYaw,
                    "guestHp" to clampedHp,
                    "guestScore" to clampedScore,
                    "antiCheatHash" to antiCheatHash.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("match_rooms").document(roomId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun finishRoomMatch(roomId: String, winnerUid: String, antiCheatHash: String): Result<Unit> {
        val docPath = "match_rooms/$roomId"
        return try {
            requireUserId()
            db.collection("match_rooms").document(roomId).update(
                mapOf(
                    "status" to "FINISHED",
                    "winnerUid" to winnerUid.take(128),
                    "antiCheatHash" to antiCheatHash.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
