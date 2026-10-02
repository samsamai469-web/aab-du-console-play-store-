import re

file_path = "app/src/main/java/com/example/ui/MainScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "                composable<Screen.Favorites> { com.example.ui.screens.FavoritesScreen(viewModel, ttsManager, navController) }"
replacement = target + "\n                composable<Screen.Settings> { com.example.ui.screens.SettingsScreen(navController) }"

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched MainScreen.kt with Settings")
