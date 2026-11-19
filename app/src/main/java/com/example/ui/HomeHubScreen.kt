package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CampaignMission
import com.example.model.GameCatalog
import com.example.model.PlayerProfile
import com.example.model.WarTheatre
import com.example.model.WeatherType
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeHubScreen(
    profile: PlayerProfile,
    onSelectTheatre: (WarTheatre) -> Unit,
    onLaunchMission: (CampaignMission) -> Unit,
    onQuickSkirmish: (WarTheatre, WeatherType) -> Unit
) {
    val activeTheatre = WarTheatre.safeValueOf(profile.equippedTheatre)
    var selectedFilter by remember { mutableStateOf<WarTheatre?>(null) }
    var selectedWeather by remember { mutableStateOf(WeatherType.CLEAR) }

    val filteredMissions = remember(selectedFilter) {
        if (selectedFilter == null) GameCatalog.missions
        else GameCatalog.missions.filter { it.theatre == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Unit Deployment Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GunmetalDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1F2A3A), Color(0xFF121720))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ACTIVE WAR THEATRE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberGold
                                )
                                Text(
                                    text = activeTheatre.unitTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ShieldEmerald.copy(alpha = 0.16f))
                                    .border(1.dp, ShieldEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "INTEGRITY ${profile.integrityScore}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ShieldEmerald
                                )
                            }
                        }

                        // 4 Theatre Selector Pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WarTheatre.entries.forEach { theatre ->
                                val isSelected = theatre == activeTheatre
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectTheatre(theatre) },
                                    label = {
                                        Text(
                                            theatre.displayName,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = theatreIcon(theatre),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = TacticalBlack,
                                        selectedLeadingIconColor = TacticalBlack
                                    ),
                                    modifier = Modifier.testTag("theatre_chip_${theatre.name.lowercase()}")
                                )
                            }
                        }

                        // Weather Selector for Quick 3D Skirmish
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WEATHER:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            WeatherType.entries.forEach { w ->
                                FilterChip(
                                    selected = selectedWeather == w,
                                    onClick = { selectedWeather = w },
                                    label = { Text(w.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Button(
                            onClick = { onQuickSkirmish(activeTheatre, selectedWeather) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = TacticalBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("quick_deploy_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "DEPLOY 3D SKIRMISH (${activeTheatre.shortTag} • ${selectedWeather.displayName.uppercase()})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Campaign Missions Header + Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CAMPAIGN MISSIONS",
                    style = MaterialTheme.typography.titleLarge,
                    color = AmberGoldBright
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All Fronts") }
                    )
                    WarTheatre.entries.forEach { theatre ->
                        FilterChip(
                            selected = selectedFilter == theatre,
                            onClick = { selectedFilter = theatre },
                            label = { Text(theatre.displayName) }
                        )
                    }
                }
            }
        }

        items(filteredMissions, key = { it.id }) { mission ->
            val completed = mission.id in profile.completedMissions
            MissionCard(
                mission = mission,
                completed = completed,
                onDeploy = { onLaunchMission(mission) }
            )
        }
    }
}

@Composable
private fun MissionCard(
    mission: CampaignMission,
    completed: Boolean,
    onDeploy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (completed) ShieldEmerald.copy(alpha = 0.6f) else ArmorBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable { onDeploy() }
            .testTag("mission_card_${mission.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GunmetalElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = theatreIcon(mission.theatre),
                            contentDescription = mission.theatre.displayName,
                            tint = AmberGoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = mission.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${mission.theatre.displayName} • ${mission.difficulty}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                    }
                }
                if (completed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mission Completed",
                        tint = ShieldEmerald
                    )
                }
            }

            Text(
                text = mission.briefing,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoBadge(
                        icon = if (mission.weather == WeatherType.CLEAR) Icons.Default.WbSunny else Icons.Default.Thunderstorm,
                        text = mission.weather.displayName,
                        color = TacticalCyan
                    )
                    InfoBadge(
                        icon = Icons.Default.MonetizationOn,
                        text = "+${mission.coinReward}",
                        color = AmberGoldBright
                    )
                    InfoBadge(
                        icon = Icons.Default.LocalFireDepartment,
                        text = "${mission.enemyCount} Hostiles",
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onDeploy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = TacticalBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("deploy_mission_${mission.id}")
                ) {
                    Text(
                        text = "DEPLOY",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBadge(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

fun theatreIcon(theatre: WarTheatre): ImageVector = when (theatre) {
    WarTheatre.TANK -> Icons.Default.Shield
    WarTheatre.AIRCRAFT -> Icons.Default.AirplanemodeActive
    WarTheatre.NAVY -> Icons.Default.DirectionsBoat
    WarTheatre.INFANTRY -> Icons.Default.Groups
}
