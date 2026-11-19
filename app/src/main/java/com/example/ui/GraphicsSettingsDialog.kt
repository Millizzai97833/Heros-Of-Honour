package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.game.SoundEffectsEngine
import com.example.model.GraphicsQuality
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ArmorBorder
import com.example.ui.theme.GunmetalDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GraphicsSettingsDialog(
    currentQuality: GraphicsQuality,
    soundEngine: SoundEffectsEngine,
    onSelectQuality: (GraphicsQuality) -> Unit,
    onDismiss: () -> Unit
) {
    var muted by remember { mutableStateOf(soundEngine.muted) }
    var volume by remember { mutableFloatStateOf(soundEngine.masterVolume) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GunmetalDark,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ArmorBorder, RoundedCornerShape(18.dp))
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
                        Icon(Icons.Default.Tune, contentDescription = null, tint = TacticalCyan)
                        Text(
                            text = "GRAPHICS & AUDIO SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "3D RENDERING QUALITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold
                )
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GraphicsQuality.entries.forEach { preset ->
                        val selected = preset == currentQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) GunmetalElevated else GunmetalSurface)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) AmberGoldBright else ArmorBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    soundEngine.playUiClick()
                                    onSelectQuality(preset)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("graphics_preset_${preset.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) AmberGoldBright else TextPrimary
                                )
                                Text(
                                    text = preset.detailHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    soundEngine.playUiClick()
                                    onSelectQuality(preset)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AmberGoldBright)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "COMBAT SOUND EFFECTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                        Text("Master SFX Audio", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                    Switch(
                        checked = !muted,
                        onCheckedChange = { enabled ->
                            muted = !enabled
                            soundEngine.muted = muted
                            if (enabled) soundEngine.playUiClick()
                        },
                        modifier = Modifier.testTag("sfx_mute_switch")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TextSecondary)
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            soundEngine.masterVolume = it
                        },
                        enabled = !muted,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberGold,
                            activeTrackColor = AmberGold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
