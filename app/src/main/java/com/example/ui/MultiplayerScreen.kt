package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.LeaderboardEntry
import com.example.model.MatchLog
import com.example.model.MatchRoom
import com.example.model.PlayerProfile
import com.example.model.UiState
import com.example.model.WarTheatre
import com.example.model.WeatherType
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MultiplayerScreen(
    profile: PlayerProfile,
    currentUserId: String,
    waitingRoomsState: UiState<List<MatchRoom>>,
    myRoomsState: UiState<List<MatchRoom>>,
    leaderboardState: UiState<List<LeaderboardEntry>>,
    matchLogsState: UiState<List<MatchLog>>,
    onCreateRoom: (WarTheatre, WeatherType) -> Unit,
    onJoinRoom: (MatchRoom) -> Unit,
    onDeployHostRoom: (MatchRoom) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var createTheatre by remember { mutableStateOf(WarTheatre.safeValueOf(profile.equippedTheatre)) }
    var createWeather by remember { mutableStateOf(WeatherType.CLEAR) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = GunmetalDark,
            contentColor = AmberGoldBright
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("ARENA LOBBY") },
                icon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("RANKINGS") },
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("ANTI-CHEAT") },
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GunmetalDark),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AmberGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("HOST COMPETITIVE MATCH", style = MaterialTheme.typography.titleLarge, color = AmberGoldBright)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WarTheatre.entries.forEach { t ->
                                        FilterChip(
                                            selected = createTheatre == t,
                                            onClick = { createTheatre = t },
                                            label = { Text(t.displayName) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WeatherType.entries.forEach { w ->
                                        FilterChip(
                                            selected = createWeather == w,
                                            onClick = { createWeather = w },
                                            label = { Text(w.displayName) }
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onCreateRoom(createTheatre, createWeather) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = TacticalBlack),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("create_multiplayer_room_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("CREATE ONLINE BATTLE ROOM", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    // My hosted/joined rooms
                    if (myRoomsState is UiState.Success && myRoomsState.data.any { it.status != "FINISHED" }) {
                        item {
                            Text("MY ACTIVE ROOMS", style = MaterialTheme.typography.labelLarge, color = TacticalCyan)
                        }
                        items(myRoomsState.data.filter { it.status != "FINISHED" }, key = { "my_${it.id}" }) { room ->
                            RoomCard(
                                room = room,
                                isMine = room.hostUid == currentUserId,
                                onJoin = { onJoinRoom(room) },
                                onDeploy = { onDeployHostRoom(room) }
                            )
                        }
                    }

                    item {
                        Text("OPEN MULTIPLAYER ROOMS", style = MaterialTheme.typography.labelLarge, color = AmberGoldBright)
                    }

                    when (waitingRoomsState) {
                        is UiState.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AmberGold)
                            }
                        }
                        is UiState.Error -> item {
                            Text(waitingRoomsState.message, color = CrimsonAlert)
                        }
                        is UiState.Success -> {
                            val openRooms = waitingRoomsState.data.filter { it.hostUid != currentUserId }
                            if (openRooms.isEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "No open rival rooms right now. Create a room above and deploy immediately or wait for a challenger!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else {
                                items(openRooms, key = { it.id }) { room ->
                                    RoomCard(
                                        room = room,
                                        isMine = false,
                                        onJoin = { onJoinRoom(room) },
                                        onDeploy = { onDeployHostRoom(room) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (leaderboardState) {
                        is UiState.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AmberGold)
                            }
                        }
                        is UiState.Error -> item {
                            Text(leaderboardState.message, color = CrimsonAlert)
                        }
                        is UiState.Success -> {
                            items(leaderboardState.data.withIndex().toList(), key = { it.value.userId }) { (idx, entry) ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (entry.userId == currentUserId) GunmetalDark else GunmetalSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            if (entry.userId == currentUserId) AmberGoldBright else ArmorBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = "#${idx + 1}",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = if (idx == 0) AmberGoldBright else TacticalCyan
                                            )
                                            Column {
                                                Text(entry.callsign, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text(
                                                    text = "LVL ${entry.level} • ${entry.wins} Wins • ${WarTheatre.safeValueOf(entry.favouriteTheatre).shortTag}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${entry.eloRating} ELO",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = AmberGold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GunmetalDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ShieldEmerald.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = ShieldEmerald, modifier = Modifier.size(34.dp))
                                Column {
                                    Text(
                                        text = "HEROS GUARD ANTI-CHEAT ACTIVE",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ShieldEmerald
                                    )
                                    Text(
                                        text = "Trust Score: ${profile.integrityScore}/100 • Real-time speed, fire-rate, damage cap, APK signature & SHA-256 checksum audit.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    when (matchLogsState) {
                        is UiState.Loading -> item { CircularProgressIndicator(color = AmberGold) }
                        is UiState.Error -> item { Text(matchLogsState.message, color = CrimsonAlert) }
                        is UiState.Success -> {
                            if (matchLogsState.data.isEmpty()) {
                                item {
                                    Text(
                                        text = "Complete a campaign mission or online match to view verified telemetry checksums.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                items(matchLogsState.data, key = { it.id }) { log ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ArmorBorder, RoundedCornerShape(12.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${log.mode} • ${log.theatre}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = if (log.integrityPassed) "VERIFIED" else "FLAGGED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (log.integrityPassed) ShieldEmerald else CrimsonAlert
                                                )
                                            }
                                            Text(
                                                text = "${log.kills} Kills • ${log.damageDealt} DMG • ${log.accuracyPct}% Acc • +${log.coinsAwarded} Coins",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "SHA256: ${log.telemetryChecksum}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TacticalCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: MatchRoom,
    isMine: Boolean,
    onJoin: () -> Unit,
    onDeploy: () -> Unit
) {
    val theatre = WarTheatre.safeValueOf(room.theatre)
    val weather = WeatherType.safeValueOf(room.weather)
    Card(
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ArmorBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${room.hostName}'s ${theatre.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${weather.displayName} • Status: ${room.status} • Players: ${room.memberUids.size}/2",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = if (isMine) onDeploy else onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMine) ShieldEmerald else AmberGold,
                    contentColor = TacticalBlack
                ),
                modifier = Modifier.testTag("room_action_${room.id}")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (isMine) "DEPLOY" else "JOIN")
            }
        }
    }
}
