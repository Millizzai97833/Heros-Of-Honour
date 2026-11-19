package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.GameCatalog
import com.example.model.PlayerProfile
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DailyRewardsDialog(
    profile: PlayerProfile,
    canClaimToday: Boolean,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentDayIndex = (profile.loginStreak % 7L).toInt() + 1

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GunmetalDark,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = AmberGoldBright)
                        Text(
                            text = "DAILY DEPLOYMENT REWARDS",
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberGoldBright
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Text(
                    text = "7-Day Command Streak: ${profile.loginStreak}/7 Days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(310.dp)
                ) {
                    items(GameCatalog.dailyRewards) { reward ->
                        val claimed = reward.day <= profile.loginStreak
                        val isNext = canClaimToday && reward.day == currentDayIndex
                        val borderColor = when {
                            isNext -> AmberGoldBright
                            claimed -> ShieldEmerald.copy(alpha = 0.6f)
                            else -> ArmorBorder
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isNext) GunmetalElevated else GunmetalSurface)
                                .border(if (isNext) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reward.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isNext) AmberGoldBright else TextPrimary
                                    )
                                    if (claimed) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Claimed",
                                            tint = ShieldEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "+${reward.coins} Coins",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }
                                if (reward.skillPoints > 0) {
                                    Text(
                                        text = "+${reward.skillPoints} Skill Point",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ShieldEmerald
                                    )
                                }
                                if (reward.bonusSkinId != null) {
                                    Text(
                                        text = "+ Rare Camo Skin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGoldBright
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onClaim,
                    enabled = canClaimToday,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = TacticalBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("claim_daily_reward_button")
                ) {
                    Text(
                        text = if (canClaimToday) "CLAIM DAY $currentDayIndex REWARD" else "TODAY'S REWARD CLAIMED",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
