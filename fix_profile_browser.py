import re

with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    ps = f.read()

old_dialog = r"    // Dialogue Officiel Web Google OAuth[\s\S]*?    if \(currentWebAuthUrl != null\) \{[\s\S]*?GoogleOAuthWebDialog\([\s\S]*?onDismiss = \{[\s\S]*?viewModel\.dismissGoogleWebAuth\(\)[\s\S]*?\}[\s\S]*?\)[\s\S]*?\}"

new_dialog = """    // Lancement du navigateur externe pour Google OAuth
    val currentWebAuthUrl = googleWebAuthUrl
    if (currentWebAuthUrl != null) {
        LaunchedEffect(currentWebAuthUrl) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(currentWebAuthUrl))
                context.startActivity(intent)
                viewModel.dismissGoogleWebAuth()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Impossible d'ouvrir le navigateur", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.dismissGoogleWebAuth()
            }
        }
    }"""

ps = re.sub(old_dialog, new_dialog, ps)

with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "w") as f:
    f.write(ps)
