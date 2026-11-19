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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CombatSkill
import com.example.model.GameCatalog
import com.example.model.GearItem
import com.example.model.GearSlot
import com.example.model.PlayerProfile
import com.example.model.SkinItem
import com.example.model.WarTheatre
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
fun ArmoryScreen(
    profile: PlayerProfile,
    onGearAction: (GearItem) -> Unit,
    onSkinAction: (SkinItem) -> Unit,
    onUnlockSkill: (String, Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedSlot by remember { mutableStateOf(GearSlot.WEAPON) }
    val activeTheatre = WarTheatre.safeValueOf(profile.equippedTheatre)
    val stats = remember(profile) { GameCatalog.computeLoadoutStats(profile, activeTheatre) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Live Loadout Stat Card
        Card(
            colors = CardDefaults.cardColors(containerColor = GunmetalDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .border(1.dp, ArmorBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${activeTheatre.unitTitle.uppercase()} LOADOUT",
                        style = MaterialTheme.typography.titleLarge,
                        color = AmberGoldBright
                    )
                    Text(
                        text = "LVL ${profile.level} • ${profile.skillPoints} SP",
                        style = MaterialTheme.typography.labelSmall,
                        color = ShieldEmerald
                    )
                }
                StatBar("FIREPOWER", (stats.damage / 110f).coerceIn(0.1f, 1f), "${stats.damage.toInt()} DMG", AmberGold)
                StatBar("ARMOR HP", (stats.maxHp / 420f).coerceIn(0.1f, 1f), "${stats.maxHp} HP", ShieldEmerald)
                StatBar("MOBILITY", (stats.moveSpeed / 30f).coerceIn(0.1f, 1f), "${stats.moveSpeed.toInt()} SPD", TacticalCyan)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = GunmetalDark,
            contentColor = AmberGoldBright
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("WEAPONS & GEAR") },
                icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("SKINS") },
                icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("SKILLS") },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GearSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text(slot.label) }
                        )
                    }
                }
                val items = remember(selectedSlot, activeTheatre) {
                    GameCatalog.gearItems.filter {
                        it.slot == selectedSlot && (it.theatre == null || it.theatre == activeTheatre)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.id }) { gear ->
                        val owned = gear.id in profile.unlockedWeapons
                        val equipped = gear.id == when (gear.slot) {
                            GearSlot.WEAPON -> profile.equippedWeapon
                            GearSlot.MUZZLE -> profile.equippedMuzzle
                            GearSlot.OPTIC -> profile.equippedOptic
                            GearSlot.ARMOR -> profile.equippedArmor
                            GearSlot.AMMO -> profile.equippedAmmo
                        }
                        GearCard(gear, owned, equipped, onClick = { onGearAction(gear) })
                    }
                }
            }
            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(GameCatalog.skins, key = { it.id }) { skin ->
                        val owned = skin.id in profile.unlockedSkins
                        val equipped = skin.id == profile.equippedSkin
                        SkinCard(skin, owned, equipped, onClick = { onSkinAction(skin) })
                    }
                }
            }
            2 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(GameCatalog.combatSkills, key = { it.id }) { skill ->
                        val unlocked = skill.id in profile.unlockedSkills
                        SkillCard(skill, unlocked, profile.skillPoints) {
                            onUnlockSkill(skill.id, skill.costSkillPoints)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBar(label: String, fraction: Float, valueText: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.weight(0.28f)
        )
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = GunmetalElevated,
            modifier = Modifier
                .weight(0.52f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.weight(0.20f)
        )
    }
}

@Composable
private fun GearCard(
    item: GearItem,
    owned: Boolean,
    equipped: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (equipped) AmberGoldBright else ArmorBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(
                    text = buildString {
                        if (item.damageBonus != 0) append("+${item.damageBonus} DMG  ")
                        if (item.armorBonus != 0) append("+${item.armorBonus} ARM  ")
                        if (item.precisionBonus != 0) append("+${item.precisionBonus} ACC  ")
                        if (item.fireRateBonus != 0) append("+${item.fireRateBonus}% RPM")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }
            Spacer(Modifier.size(10.dp))
            Button(
                onClick = onClick,
                enabled = !equipped,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (owned) ShieldEmerald else AmberGold,
                    contentColor = TacticalBlack
                ),
                modifier = Modifier.testTag("gear_btn_${item.id}")
            ) {
                if (equipped) {
                    Icon(Icons.Default.Check, contentDescription = "Equipped", modifier = Modifier.size(16.dp))
                } else {
                    Text(if (owned) "EQUIP" else "${item.costCoins} C")
                }
            }
        }
    }
}

@Composable
private fun SkinCard(
    skin: SkinItem,
    owned: Boolean,
    equipped: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (equipped) AmberGoldBright else ArmorBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(skin.primaryColorHex))
                    .border(3.dp, Color(skin.accentColorHex), CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(skin.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${skin.rarity.label} • ${skin.description}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Button(
                onClick = onClick,
                enabled = !equipped,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (owned) ShieldEmerald else AmberGold,
                    contentColor = TacticalBlack
                ),
                modifier = Modifier.testTag("skin_btn_${skin.id}")
            ) {
                Text(if (equipped) "ACTIVE" else if (owned) "EQUIP" else "${skin.costCoins} C")
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: CombatSkill,
    unlocked: Boolean,
    availableSp: Long,
    onUnlock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (unlocked) ShieldEmerald else ArmorBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(skill.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(skill.statEffect, style = MaterialTheme.typography.labelSmall, color = ShieldEmerald)
            }
            Spacer(Modifier.size(10.dp))
            Button(
                onClick = onUnlock,
                enabled = !unlocked && availableSp >= skill.costSkillPoints,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (unlocked) ShieldEmerald else AmberGold,
                    contentColor = TacticalBlack
                ),
                modifier = Modifier.testTag("skill_btn_${skill.id}")
            ) {
                Text(if (unlocked) "ACTIVE" else "${skill.costSkillPoints} SP")
            }
        }
    }
}
