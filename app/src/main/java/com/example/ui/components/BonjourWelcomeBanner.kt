package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Bannière d'accueil dynamique avec basculement automatique selon l'heure (Matin, Après-midi, Soir)
 * et défilement automatique des images haute définition sans fond noir.
 */
@Composable
fun BonjourWelcomeBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Heure actuelle pour déterminer le moment de la journée
    val calendar = remember { Calendar.getInstance() }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)

    // Association des images et salutations selon l'heure (Uniquement les photos officielles de salutation)
    val (timeImages, greetingTitle, greetingSubtitle) = remember(hour) {
        when (hour) {
            in 5..11 -> Triple(
                listOf(R.drawable.bonjour_1, R.drawable.bonjour_2, R.drawable.bonjour_3),
                "Bonjour,",
                "Bienvenue ! Matin radieux"
            )
            in 12..17 -> Triple(
                listOf(R.drawable.bon_apres_midi_2),
                "Bon après-midi,",
                "Bienvenue ! Bel après-midi"
            )
            else -> Triple(
                listOf(R.drawable.bonsoir_1, R.drawable.bonsoir_2),
                "Bonsoir,",
                "Bienvenue ! Douce soirée"
            )
        }
    }

    var currentIndex by remember(timeImages) { mutableStateOf(0) }

    // Défilement automatique toutes les 30 secondes entre les images du moment
    LaunchedEffect(timeImages) {
        while (true) {
            delay(30000)
            currentIndex = (currentIndex + 1) % timeImages.size
        }
    }

    val activeImage = timeImages[currentIndex.coerceIn(0, timeImages.size - 1)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.7f)
            .clip(RoundedCornerShape(16.dp))
            .testTag("banner_bonjour_welcome")
    ) {
        // 1. Image dynamique haute résolution avec transition crossfade
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(activeImage)
                .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                .precision(coil.size.Precision.EXACT)
                .allowHardware(true)
                .crossfade(600)
                .build(),
            contentDescription = greetingTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Dégradé latéral carbone profond pour un contraste parfait du texte
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0A0B0D).copy(alpha = 0.96f), // ⚫ Fond sombre : #0A0B0D
                            Color(0xFF16181D).copy(alpha = 0.85f),
                            Color(0xFF16181D).copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Textes de salutation dynamiques haute définition
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = greetingTitle.uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 22.sp,
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = Color.White // ⚪ Blanc
                )

                Text(
                    text = "BIENVENUE !",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = com.example.ui.theme.SamPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Apprenons et comprenons\nnos langues locales.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFECEAEB) // 🩶 Surfaces/pastilles claires : #ECEAEB
                )
            }

            Spacer(modifier = Modifier.weight(0.85f))
        }
    }
}
