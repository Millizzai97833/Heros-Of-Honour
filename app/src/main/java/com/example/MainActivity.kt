package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.GameRepository
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.data.signOutUser
import com.example.game.AntiCheatEngine
import com.example.game.SoundEffectsEngine
import com.example.model.GraphicsQuality
import com.example.model.PlayerProfile
import com.example.model.UiState
import com.example.ui.ArmoryScreen
import com.example.ui.AuthScreen
import com.example.ui.BattleScreen
import com.example.ui.DailyRewardsDialog
import com.example.ui.GraphicsSettingsDialog
import com.example.ui.HomeHubScreen
import com.example.ui.MultiplayerScreen
import com.example.ui.ShopScreen
import com.example.ui.SplashTitleScreen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.HerosOfHonourTheme
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.BattleLaunchConfig
import com.example.viewmodel.GameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_MISSIONS = "missions"
private const val ROUTE_ARMORY = "armory"
private const val ROUTE_MULTIPLAYER = "multiplayer"
private const val ROUTE_SHOP = "shop"

class MainActivity : ComponentActivity() {

    private lateinit var soundEngine: SoundEffectsEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        soundEngine = SoundEffectsEngine(applicationContext)
        setContent {
            HerosOfHonourTheme {
                HerosOfHonourRoot(soundEngine = soundEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundEngine.isInitialized) {
            soundEngine.release()
        }
    }
}

@Composable
private fun HerosOfHonourRoot(
    soundEngine: SoundEffectsEngine,
    auth: FirebaseAuth = Firebase.auth
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by remember { mutableStateOf(true) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    if (showSplash) {
        SplashTitleScreen(
            onFinished = {
                soundEngine.playUiClick()
                showSplash = false
            }
        )
    } else if (currentUser == null) {
        AuthScreen(onAuthSuccess = { soundEngine.playCoinReward() })
    } else {
        val user = currentUser!!
        key(user.uid) {
            AuthenticatedGameShell(
                currentUserId = user.uid,
                displayName = user.displayName,
                soundEngine = soundEngine,
                onSignOut = {
                    signOutUser(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedGameShell(
    currentUserId: String,
    displayName: String?,
    soundEngine: SoundEffectsEngine,
    onSignOut: () -> Unit
) {
    val viewModel: GameViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = GameRepository(db)
                val antiCheat = AntiCheatEngine(app.applicationContext)
                GameViewModel(repo, antiCheat, currentUserId, displayName)
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val waitingRoomsState by viewModel.waitingRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by viewModel.myRoomsState.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val matchLogsState by viewModel.matchLogsState.collectAsStateWithLifecycle()
    val activeBattle by viewModel.activeBattle.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val showDailyRewards by viewModel.showDailyRewards.collectAsStateWithLifecycle()
    val showGraphicsSettings by viewModel.showGraphicsSettings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val profile = (profileState as? UiState.Success)?.data

    // Show Daily Rewards automatically once if unclaimed on launch
    var promptedDaily by remember { mutableStateOf(false) }
    LaunchedEffect(profile?.lastDailyClaimDay) {
        if (profile != null && !promptedDaily && viewModel.isDailyClaimable(profile)) {
            promptedDaily = true
            viewModel.setDailyRewardsVisible(true)
        }
    }

    if (showGraphicsSettings && profile != null) {
        GraphicsSettingsDialog(
            currentQuality = GraphicsQuality.safeValueOf(profile.graphicsQuality),
            soundEngine = soundEngine,
            onSelectQuality = { viewModel.setGraphicsQuality(it) },
            onDismiss = { viewModel.setGraphicsSettingsVisible(false) }
        )
    }

    if (showDailyRewards && profile != null) {
        DailyRewardsDialog(
            profile = profile,
            canClaimToday = viewModel.isDailyClaimable(profile),
            onClaim = {
                soundEngine.playCoinReward()
                viewModel.claimDailyReward(profile)
            },
            onDismiss = { viewModel.setDailyRewardsVisible(false) }
        )
    }

    if (activeBattle != null && profile != null) {
        BattleScreen(
            config = activeBattle!!,
            profile = profile,
            currentUserId = currentUserId,
            activeRoom = activeRoom,
            antiCheatEngine = viewModel.antiCheatEngine,
            soundEngine = soundEngine,
            onOpenGraphicsSettings = { viewModel.setGraphicsSettingsVisible(true) },
            onSyncTelemetry = { roomId, isHost, x, z, yaw, hp, score ->
                viewModel.syncMultiplayerTelemetry(roomId, isHost, x, z, yaw, hp, score)
            },
            onSubmitOutcome = { won, kills, dmg, shots, acc, dur, report ->
                viewModel.submitBattleResult(profile, activeBattle!!, won, kills, dmg, shots, acc, dur, report)
            },
            onExitBattle = {
                soundEngine.playUiClick()
                viewModel.exitBattle()
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_MISSIONS

    Scaffold(
        containerColor = TacticalBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (profile != null) {
                CommandTopBar(
                    profile = profile,
                    dailyClaimable = viewModel.isDailyClaimable(profile),
                    onDailyClick = {
                        soundEngine.playUiClick()
                        viewModel.setDailyRewardsVisible(true)
                    },
                    onSettingsClick = {
                        soundEngine.playUiClick()
                        viewModel.setGraphicsSettingsVisible(true)
                    },
                    onSignOut = {
                        soundEngine.playUiClick()
                        onSignOut()
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = GunmetalDark,
                contentColor = TextPrimary,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val items = listOf(
                    Triple(ROUTE_MISSIONS, "Missions", Icons.Default.MilitaryTech),
                    Triple(ROUTE_ARMORY, "Armory", Icons.Default.Build),
                    Triple(ROUTE_MULTIPLAYER, "Online", Icons.Default.Public),
                    Triple(ROUTE_SHOP, "Shop", Icons.Default.ShoppingCart)
                )
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            soundEngine.playUiClick()
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(ROUTE_MISSIONS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TacticalBlack,
                            selectedTextColor = AmberGoldBright,
                            indicatorColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_$route")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = profileState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberGold)
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.message, color = CrimsonAlert)
                    }
                }
                is UiState.Success -> {
                    val p = state.data
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_MISSIONS
                    ) {
                        composable(ROUTE_MISSIONS) {
                            HomeHubScreen(
                                profile = p,
                                onSelectTheatre = {
                                    soundEngine.playUiClick()
                                    viewModel.selectTheatre(p, it)
                                },
                                onLaunchMission = { mission ->
                                    soundEngine.playUiClick()
                                    viewModel.launchMission(mission)
                                },
                                onQuickSkirmish = { theatre, weather ->
                                    soundEngine.playUiClick()
                                    viewModel.launchMission(
                                        com.example.model.CampaignMission(
                                            id = "skirmish_${theatre.name.lowercase()}",
                                            title = "Skirmish: ${theatre.displayName}",
                                            subtitle = "Rapid Deployment",
                                            theatre = theatre,
                                            weather = weather,
                                            dynamicWeatherShift = true,
                                            difficulty = "Custom",
                                            enemyCount = 5,
                                            destructibleTargets = 6,
                                            coinReward = 400L,
                                            xpReward = 300L,
                                            briefing = "Instant combined-arms skirmish."
                                        )
                                    )
                                }
                            )
                        }
                        composable(ROUTE_ARMORY) {
                            ArmoryScreen(
                                profile = p,
                                onGearAction = {
                                    soundEngine.playUiClick()
                                    viewModel.purchaseOrEquipGear(p, it)
                                },
                                onSkinAction = {
                                    soundEngine.playUiClick()
                                    viewModel.purchaseOrEquipSkin(p, it)
                                },
                                onUnlockSkill = { id, cost ->
                                    soundEngine.playCoinReward()
                                    viewModel.unlockSkill(p, id, cost)
                                }
                            )
                        }
                        composable(ROUTE_MULTIPLAYER) {
                            MultiplayerScreen(
                                profile = p,
                                currentUserId = currentUserId,
                                waitingRoomsState = waitingRoomsState,
                                myRoomsState = myRoomsState,
                                leaderboardState = leaderboardState,
                                matchLogsState = matchLogsState,
                                onCreateRoom = { theatre, weather ->
                                    soundEngine.playUiClick()
                                    viewModel.createAndWatchMultiplayerRoom(p, theatre, weather)
                                },
                                onJoinRoom = { room ->
                                    soundEngine.playUiClick()
                                    viewModel.joinAndLaunchRoom(room, p)
                                },
                                onDeployHostRoom = { room ->
                                    soundEngine.playUiClick()
                                    viewModel.deployHostRoom(room)
                                }
                            )
                        }
                        composable(ROUTE_SHOP) {
                            ShopScreen(
                                profile = p,
                                onWatchRewardedAdComplete = {
                                    soundEngine.playCoinReward()
                                    viewModel.claimRewardedAdCoins(p)
                                },
                                onPurchaseBundle = { bundle ->
                                    soundEngine.playCoinReward()
                                    viewModel.purchaseBundle(p, bundle)
                                },
                                onPurchaseSkin = { skin ->
                                    soundEngine.playUiClick()
                                    viewModel.purchaseOrEquipSkin(p, skin)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandTopBar(
    profile: PlayerProfile,
    dailyClaimable: Boolean,
    onDailyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GunmetalDark)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "HEROS OF HONOUR",
                style = MaterialTheme.typography.titleLarge,
                color = AmberGoldBright
            )
            Text(
                text = "${profile.callsign} • LVL ${profile.level} • ${profile.eloRating} ELO",
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GunmetalSurface)
                    .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = AmberGold, modifier = Modifier.size(16.dp))
                Text("${profile.coins}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }

            IconButton(onClick = onDailyClick, modifier = Modifier.testTag("daily_rewards_icon_btn")) {
                BadgedBox(
                    badge = {
                        if (dailyClaimable) {
                            Badge(containerColor = ShieldEmerald)
                        }
                    }
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = "Daily Rewards", tint = AmberGoldBright)
                }
            }

            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("graphics_settings_icon_btn")) {
                Icon(Icons.Default.Settings, contentDescription = "Graphics & Audio Settings", tint = TextPrimary)
            }

            IconButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out_icon_btn")) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = TextSecondary)
            }
        }
    }
}
