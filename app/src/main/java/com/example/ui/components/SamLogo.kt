package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Composant officiel du Logo Sam Traduction.
 * 
 * Spécifications :
 * - Nouvel emblème officiel dynamique Or Solaire & Flamme Crépusculaire
 * - Rendu haute résolution vectorisé / sans distorsion
 * - Échelle adaptative et fond transparent
 */
@Composable
fun SamLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    elevation: Dp = 4.dp,
    showBorderGlow: Boolean = true
) {
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Logo Officiel SAM AI",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}
