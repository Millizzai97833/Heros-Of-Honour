package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TacticalBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashTitleScreen(
    onFinished: () -> Unit
) {
    val fadeIn = remember { Animatable(0f) }
    val loadProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fadeIn.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        loadProgress.animateTo(1f, animationSpec = tween(1900, easing = FastOutSlowInEasing))
        delay(250)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBlack)
            .clickable { onFinished() }
            .testTag("splash_title_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash_hero_1795074026980),
            contentDescription = "Heros Of Honour Key Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Cinematic vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xBB0A0D12),
                            Color(0x550A0D12),
                            Color(0xEE0A0D12)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .alpha(fadeIn.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA121720))
                    .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Anti-Cheat Active",
                    tint = ShieldEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "3D COMBINED-ARMS ENGINE • ANTI-CHEAT PROTECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MilitaryTech,
                    contentDescription = "Heros Of Honour Crest",
                    tint = AmberGoldBright,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "HEROS OF HONOUR",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 38.sp,
                        letterSpacing = 3.sp
                    ),
                    color = AmberGoldBright,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberGold.copy(alpha = 0.16f))
                        .border(1.dp, AmberGold.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "MADE BY SHAHAB MILLIZAI",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INITIALIZING 3D SHADERS & TACTICAL AUDIO...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { loadProgress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AmberGold,
                    trackColor = Color(0xFF232D3F)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap anywhere to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
