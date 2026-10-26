package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * Bannière d'accueil dynamique élégante et 100% native avec les couleurs nationales du Faso,
 * sans aucune image avec texte incrusté.
 */
@Composable
fun BonjourWelcomeBanner(
    modifier: Modifier = Modifier
) {
    val calendar = remember { Calendar.getInstance() }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)

    val (greetingTitle, greetingSubtitle, timeIcon, timeLabel) = remember(hour) {
        when (hour) {
            in 5..11 -> Quadruple(
                "Bonjour,",
                "Matin radieux au Faso",
                Icons.Default.Brightness5,
                "Matin"
            )
            in 12..17 -> Quadruple(
                "Bon après-midi,",
                "Bel après-midi d'apprentissage",
                Icons.Default.WbSunny,
                "Après-midi"
            )
            else -> Quadruple(
                "Bonsoir,",
                "Douce soirée et paix",
                Icons.Default.Brightness4,
                "Soirée"
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.6f)
            .testTag("banner_bonjour_welcome"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10131A)),
        border = BorderStroke(1.dp, Color(0xFF262C38)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fond dégradé artistique aux nuances rouge & vert Faso subtiles
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF10131A),
                                Color(0xFF161B26),
                                Color(0xFF1B2330),
                                Color(0xFF10281E).copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Liseré stylisé aux couleurs du drapeau burkinabè (Rouge / Étoile Or / Vert) sur le bord droit
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE53935)) // Rouge Faso
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "★",
                        color = Color(0xFFFFD54F), // Étoile dorée
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2E7D32)) // Vert Faso
                    )
                }
            }

            // Contenu texte principal haute définition
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge Période de la journée
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = timeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Salutation & Titre
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = greetingTitle.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                letterSpacing = 0.5.sp
                            ),
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BIENVENUE !",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                letterSpacing = 0.5.sp
                            ),
                            fontWeight = FontWeight.Black,
                            color = com.example.ui.theme.SamPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Apprenons et comprenons nos langues locales.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFC7CBD6)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
