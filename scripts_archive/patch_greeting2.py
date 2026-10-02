import re

file_path = "app/src/main/java/com/example/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """@Composable
fun TimeBasedGreeting(viewModel: com.example.ui.SamAiViewModel) {"""
start_idx = content.find(target)

new_function = """@Composable
fun TimeBasedGreeting(viewModel: com.example.ui.SamAiViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    
    // Détection de l'heure locale
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    
    // Variables de configuration
    val greetingText: String
    val imageUrl: String
    
    when (hour) {
        in 5..11 -> {
            // Matin: Lever de soleil réaliste, ciel doré, nature africaine, sans être humain
            greetingText = "Bonjour"
            imageUrl = "https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1600&q=80"
        }
        in 12..17 -> {
            // Après-midi: Grand ciel bleu, lumière naturelle, paysage lumineux, sans personne
            greetingText = "Bon après-midi"
            imageUrl = "https://images.unsplash.com/photo-1516026672322-bc52d61a55d5?auto=format&fit=crop&w=1600&q=80"
        }
        else -> {
            // Soir (18:00 - 04:59): Lune, étoiles, paysage nocturne paisible, sans être humain
            greetingText = "Bonsoir"
            imageUrl = "https://images.unsplash.com/photo-1513628253939-010e64ac66cd?auto=format&fit=crop&w=1600&q=80"
        }
    }
    
    val finalGreeting = if (userProfile.isLoggedIn && userProfile.name.isNotBlank() && userProfile.name != "Invité") {
        val firstName = userProfile.name.split(" ").firstOrNull() ?: userProfile.name
        "$greetingText, $firstName"
    } else {
        greetingText
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = finalGreeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Carte d'image ultra réaliste
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Paysage du moment de la journée",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}"""

content = content[:start_idx] + new_function
with open(file_path, "w") as f:
    f.write(content)

print("Replaced TimeBasedGreeting")
