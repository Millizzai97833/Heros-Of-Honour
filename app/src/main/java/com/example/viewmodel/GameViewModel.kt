package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameRepository
import com.example.game.AntiCheatEngine
import com.example.game.AntiCheatReport
import com.example.model.CampaignMission
import com.example.model.GameCatalog
import com.example.model.GearItem
import com.example.model.GraphicsQuality
import com.example.model.LeaderboardEntry
import com.example.model.MatchLog
import com.example.model.MatchRoom
import com.example.model.PlayerProfile
import com.example.model.ShopBundle
import com.example.model.SkinItem
import com.example.model.UiState
import com.example.model.WarTheatre
import com.example.model.WeatherType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BattleLaunchConfig(
    val mode: String, // "CAMPAIGN" or "MULTIPLAYER"
    val theatre: WarTheatre,
    val weather: WeatherType,
    val dynamicWeatherShift: Boolean,
    val missionId: String,
    val missionTitle: String,
    val enemyCount: Int,
    val destructibleTargets: Int,
    val coinReward: Long,
    val xpReward: Long,
    val roomId: String? = null,
    val isHost: Boolean = true
)

class GameViewModel(
    private val repository: GameRepository,
    val antiCheatEngine: AntiCheatEngine,
    val currentUserId: String,
    private val displayName: String?
) : ViewModel() {

    val profileState: StateFlow<UiState<PlayerProfile>> =
        repository.observePlayerProfile(currentUserId)
            .map<PlayerProfile, UiState<PlayerProfile>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("observeProfile", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val waitingRoomsState: StateFlow<UiState<List<MatchRoom>>> =
        repository.observeWaitingRooms()
            .map<List<MatchRoom>, UiState<List<MatchRoom>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("observeWaitingRooms", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myRoomsState: StateFlow<UiState<List<MatchRoom>>> =
        repository.observeMyRooms(currentUserId)
            .map<List<MatchRoom>, UiState<List<MatchRoom>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("observeMyRooms", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
        repository.observeLeaderboard()
            .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("observeLeaderboard", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val matchLogsState: StateFlow<UiState<List<MatchLog>>> =
        repository.observeMatchLogs(currentUserId)
            .map<List<MatchLog>, UiState<List<MatchLog>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("observeMatchLogs", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _activeBattle = MutableStateFlow<BattleLaunchConfig?>(null)
    val activeBattle: StateFlow<BattleLaunchConfig?> = _activeBattle.asStateFlow()

    private val _showDailyRewards = MutableStateFlow(false)
    val showDailyRewards: StateFlow<Boolean> = _showDailyRewards.asStateFlow()

    private val _showGraphicsSettings = MutableStateFlow(false)
    val showGraphicsSettings: StateFlow<Boolean> = _showGraphicsSettings.asStateFlow()

    private val _activeRoomFlow = MutableStateFlow<MatchRoom?>(null)
    val activeRoom: StateFlow<MatchRoom?> = _activeRoomFlow.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensurePlayerProfile(currentUserId, displayName)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to initialize profile" }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun setDailyRewardsVisible(visible: Boolean) {
        _showDailyRewards.value = visible
    }

    fun setGraphicsSettingsVisible(visible: Boolean) {
        _showGraphicsSettings.value = visible
    }

    fun currentUtcDay(): String = repository.currentUtcDayKey()

    fun isDailyClaimable(profile: PlayerProfile): Boolean =
        profile.lastDailyClaimDay != repository.currentUtcDayKey()

    fun claimDailyReward(profile: PlayerProfile) {
        viewModelScope.launch {
            repository.claimDailyLoginReward(profile)
                .onSuccess { msg -> _statusBanner.value = msg }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Could not claim daily reward" }
        }
    }

    fun claimRewardedAdCoins(profile: PlayerProfile) {
        viewModelScope.launch {
            repository.claimRewardedAdCoins(profile)
                .onSuccess { coins -> _statusBanner.value = "Rewarded Video Complete! +$coins Coins added." }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Ad reward unavailable" }
        }
    }

    fun selectTheatre(profile: PlayerProfile, theatre: WarTheatre) {
        viewModelScope.launch {
            repository.selectTheatre(profile, theatre)
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun purchaseOrEquipGear(profile: PlayerProfile, item: GearItem) {
        viewModelScope.launch {
            val result = if (item.id in profile.unlockedWeapons) {
                repository.equipGearItem(profile, item)
            } else {
                repository.purchaseGearItem(profile, item)
            }
            result
                .onSuccess {
                    _statusBanner.value = if (item.id in profile.unlockedWeapons) {
                        "Equipped ${item.name}"
                    } else {
                        "Purchased & Equipped ${item.name}"
                    }
                }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun purchaseOrEquipSkin(profile: PlayerProfile, skin: SkinItem) {
        viewModelScope.launch {
            repository.purchaseOrEquipSkin(profile, skin)
                .onSuccess { _statusBanner.value = "Active Camo: ${skin.name}" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun purchaseBundle(profile: PlayerProfile, bundle: ShopBundle) {
        viewModelScope.launch {
            repository.purchaseBundle(profile, bundle)
                .onSuccess { _statusBanner.value = "Unlocked ${bundle.title}! +${bundle.bonusCoins} Bonus Coins" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun unlockSkill(profile: PlayerProfile, skillId: String, costSp: Long) {
        viewModelScope.launch {
            repository.unlockCombatSkill(profile, skillId, costSp)
                .onSuccess { _statusBanner.value = "Combat Skill Unlocked!" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        viewModelScope.launch {
            repository.updateGraphicsQuality(quality)
                .onSuccess { _statusBanner.value = "Graphics preset: ${quality.label}" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun launchMission(mission: CampaignMission) {
        _activeBattle.value = BattleLaunchConfig(
            mode = "CAMPAIGN",
            theatre = mission.theatre,
            weather = mission.weather,
            dynamicWeatherShift = mission.dynamicWeatherShift,
            missionId = mission.id,
            missionTitle = mission.title,
            enemyCount = mission.enemyCount,
            destructibleTargets = mission.destructibleTargets,
            coinReward = mission.coinReward,
            xpReward = mission.xpReward
        )
    }

    fun createAndWatchMultiplayerRoom(
        profile: PlayerProfile,
        theatre: WarTheatre,
        weather: WeatherType
    ) {
        viewModelScope.launch {
            val hash = antiCheatEngine.generateSyncHash(currentUserId, 0L, 220L)
            repository.createMatchRoom(profile, theatre, weather, hash)
                .onSuccess { roomId ->
                    _statusBanner.value = "Battle Room Created — Waiting for Opponent or Deploy Solo"
                    watchRoom(roomId)
                }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun joinAndLaunchRoom(room: MatchRoom, profile: PlayerProfile) {
        viewModelScope.launch {
            repository.joinMatchRoom(room, profile)
                .onSuccess {
                    watchRoom(room.id)
                    launchRoomBattle(room.copy(guestUid = currentUserId, status = "ACTIVE"), isHost = false)
                }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    fun deployHostRoom(room: MatchRoom) {
        viewModelScope.launch {
            repository.startRoomMatchAsHost(room.id)
                .onSuccess {
                    watchRoom(room.id)
                    launchRoomBattle(room, isHost = true)
                }
                .onFailure { e -> _statusBanner.value = e.localizedMessage }
        }
    }

    private fun watchRoom(roomId: String) {
        viewModelScope.launch {
            repository.observeRoom(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Room stream ended", firestoreError ?: error)
                }
                .collect { room ->
                    _activeRoomFlow.value = room
                }
        }
    }

    private fun launchRoomBattle(room: MatchRoom, isHost: Boolean) {
        val theatre = WarTheatre.safeValueOf(room.theatre)
        val weather = WeatherType.safeValueOf(room.weather)
        _activeBattle.value = BattleLaunchConfig(
            mode = "MULTIPLAYER",
            theatre = theatre,
            weather = weather,
            dynamicWeatherShift = true,
            missionId = room.id,
            missionTitle = "Online PvP: ${theatre.displayName}",
            enemyCount = 1,
            destructibleTargets = 6,
            coinReward = 750L,
            xpReward = 600L,
            roomId = room.id,
            isHost = isHost
        )
    }

    fun syncMultiplayerTelemetry(
        roomId: String,
        isHost: Boolean,
        x: Double,
        z: Double,
        yaw: Double,
        hp: Long,
        score: Long
    ) {
        viewModelScope.launch {
            val hash = antiCheatEngine.generateSyncHash(currentUserId, score, hp)
            repository.syncRoomTelemetry(roomId, isHost, x, z, yaw, hp, score, hash)
        }
    }

    fun submitBattleResult(
        profile: PlayerProfile,
        config: BattleLaunchConfig,
        won: Boolean,
        kills: Int,
        damageDealt: Int,
        shotsFired: Int,
        accuracyPct: Int,
        durationSec: Int,
        report: AntiCheatReport
    ) {
        viewModelScope.launch {
            val earnedCoins = if (won) config.coinReward else (config.coinReward / 4L)
            val earnedXp = if (won) config.xpReward else (config.xpReward / 3L)

            if (config.roomId != null) {
                val winnerUid = if (won) currentUserId else "OPPONENT"
                repository.finishRoomMatch(config.roomId, winnerUid, report.checksum)
            }

            repository.recordMatchOutcome(
                profile = profile,
                mode = config.mode,
                theatre = config.theatre,
                missionId = config.missionId,
                won = won,
                kills = kills,
                damageDealt = damageDealt,
                shotsFired = shotsFired,
                accuracyPct = accuracyPct,
                durationSec = durationSec,
                baseCoins = earnedCoins,
                baseXp = earnedXp,
                report = report
            ).onSuccess { (coins, xp) ->
                _statusBanner.value = if (report.integrityPassed) {
                    "Verified Match Logged: +$coins Coins, +$xp XP"
                } else {
                    "Anti-Cheat Alert: Rewards withheld (${report.flags.joinToString()})"
                }
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to record match"
            }
        }
    }

    fun exitBattle() {
        _activeBattle.value = null
    }

    private fun <T> handleStreamError(source: String, error: Throwable): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error in $source", rootError)
        return UiState.Error(rootError.message ?: "Failed to sync $source")
    }

    private companion object {
        const val TAG = "GameVM"
    }
}
