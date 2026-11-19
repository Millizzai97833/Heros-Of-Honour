package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.GameCatalog
import com.example.model.PlayerProfile
import com.example.model.ShopBundle
import com.example.model.SkinItem
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
import kotlinx.coroutines.delay

@Composable
fun ShopScreen(
    profile: PlayerProfile,
    onWatchRewardedAdComplete: () -> Unit,
    onPurchaseBundle: (ShopBundle) -> Unit,
    onPurchaseSkin: (SkinItem) -> Unit
) {
    var playingRewardedAd by remember { mutableStateOf(false) }

    if (playingRewardedAd) {
        RewardedVideoAdModal(
            onCompleted = {
                playingRewardedAd = false
                onWatchRewardedAdComplete()
            },
            onCancel = { playingRewardedAd = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Rewarded Video Ad Station
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GunmetalDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ShieldEmerald.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF102A22), Color(0xFF121720))
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.OndemandVideo, contentDescription = null, tint = ShieldEmerald)
                            Text(
                                text = "REWARDED AD VIDEO",
                                style = MaterialTheme.typography.titleLarge,
                                color = ShieldEmerald
                            )
                        }
                        Text(
                            text = "Watch a short tactical sponsor video to earn +250 Coins instantly for skins & upgrades.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = { playingRewardedAd = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShieldEmerald,
                            contentColor = TacticalBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("watch_rewarded_ad_button")
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("+250 C", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            Text(
                text = "DISCOUNTED WAR BUNDLES",
                style = MaterialTheme.typography.titleLarge,
                color = AmberGoldBright
            )
        }

        items(GameCatalog.bundles, key = { it.id }) { bundle ->
            val ownedAll = bundle.includedSkinId in profile.unlockedSkins &&
                bundle.includedWeaponId in profile.unlockedWeapons
            BundleCard(
                bundle = bundle,
                alreadyOwned = ownedAll,
                onBuy = { onPurchaseBundle(bundle) }
            )
        }

        item {
            Text(
                text = "PREMIUM CHARACTER & VEHICLE SKINS",
                style = MaterialTheme.typography.titleLarge,
                color = TacticalCyan
            )
        }

        items(GameCatalog.skins.filter { it.costCoins > 0 }, key = { it.id }) { skin ->
            val owned = skin.id in profile.unlockedSkins
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(skin.primaryColorHex))
                                .border(2.dp, Color(skin.accentColorHex), RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(skin.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${skin.rarity.label} • ${skin.description}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { onPurchaseSkin(skin) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (owned) ShieldEmerald else AmberGold,
                            contentColor = TacticalBlack
                        ),
                        modifier = Modifier.testTag("shop_skin_${skin.id}")
                    ) {
                        Text(if (owned) "EQUIP" else "${skin.costCoins} C")
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleCard(
    bundle: ShopBundle,
    alreadyOwned: Boolean,
    onBuy: () -> Unit
) {
    val imgRes = if (bundle.drawableTag == "sovereign") {
        R.drawable.img_bundle_sovereign_1795074025960
    } else {
        R.drawable.img_bundle_valkyrie_1795074027115
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                Image(
                    painter = painterResource(id = imgRes),
                    contentDescription = bundle.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonAlert)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Text(
                            text = bundle.discountLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(bundle.title, style = MaterialTheme.typography.titleLarge, color = AmberGoldBright)
                Text(bundle.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = AmberGold)
                        Text(
                            text = "${bundle.originalCostCoins} C",
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                            color = TextSecondary
                        )
                        Text(
                            text = "${bundle.bundleCostCoins} C",
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberGoldBright
                        )
                    }
                    Button(
                        onClick = onBuy,
                        enabled = !alreadyOwned,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGold,
                            contentColor = TacticalBlack
                        ),
                        modifier = Modifier.testTag("buy_bundle_${bundle.id}")
                    ) {
                        Text(
                            text = if (alreadyOwned) "OWNED" else "GET BUNDLE",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardedVideoAdModal(
    onCompleted: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(6) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GunmetalDark,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ShieldEmerald, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SPONSORED BRIEFING VIDEO",
                    style = MaterialTheme.typography.titleLarge,
                    color = AmberGoldBright
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GunmetalElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_bundle_valkyrie_1795074027115),
                        contentDescription = "Rewarded Ad Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "REWARD IN ${secondsLeft}s" else "REWARD READY!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AmberGoldBright
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (6 - secondsLeft) / 6f },
                    color = ShieldEmerald,
                    trackColor = GunmetalElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = GunmetalSurface, contentColor = TextSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLOSE")
                    }
                    Button(
                        onClick = onCompleted,
                        enabled = secondsLeft == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldEmerald, contentColor = TacticalBlack),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("claim_ad_reward_btn")
                    ) {
                        Text("CLAIM +250 C")
                    }
                }
            }
        }
    }
}
