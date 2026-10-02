import re

file_path = "app/src/main/java/com/example/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """            // Header Description
            Text(
                text = "Chaque mot français est illustré par son image exacte et traduit fidèlement en langues locales avec prononciation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )"""

replacement = """            // Header Description : Salutation Intelligente
            TimeBasedGreeting(viewModel)"""

content = content.replace(target, replacement)

# Check if TimeBasedGreeting already exists
if "fun TimeBasedGreeting" not in content:
    # Append the function
    time_based_greeting = """
@Composable
fun TimeBasedGreeting(viewModel: com.example.ui.SamAiViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    
    // Détection de l'heure locale
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    
    // Variables de configuration
    val greetingText: String
    val emoji: String
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val gradientColors: List<Color>
    
    when (hour) {
        in 5..11 -> {
            // Matin
            greetingText = "Bonjour"
            emoji = "👋"
            icon = Icons.Default.WbSunny
            gradientColors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300))
        }
        in 12..17 -> {
            // Après-midi
            greetingText = "Bon après-midi"
            emoji = "🌿"
            icon = Icons.Default.Nature
            gradientColors = listOf(Color(0xFF81C784), Color(0xFF388E3C))
        }
        else -> {
            // Soir (18:00 - 04:59)
            greetingText = "Bonsoir"
            emoji = "🌙"
            icon = Icons.Default.Bedtime
            gradientColors = listOf(Color(0xFF7986CB), Color(0xFF303F9F))
        }
    }
    
    val finalGreeting = if (userProfile.isLoggedIn && userProfile.name.isNotBlank() && userProfile.name != "Invité") {
        val firstName = userProfile.name.split(" ").firstOrNull() ?: userProfile.name
        "$greetingText, $firstName $emoji"
    } else {
        "$greetingText $emoji"
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = finalGreeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Image non humaine qui change
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = gradientColors
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Un petit effet de halo (cercles semi-transparents)
            Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
            Box(modifier = Modifier.size(90.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
            
            Icon(
                imageVector = icon,
                contentDescription = "Illustration du moment de la journée",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}
"""
    content = content + time_based_greeting

with open(file_path, "w") as f:
    f.write(content)

print("HomeScreen.kt patched")
