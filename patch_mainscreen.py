import re

file_path = "app/src/main/java/com/example/ui/MainScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Make sure we import navDeepLink
if "import androidx.navigation.navDeepLink" not in content:
    content = content.replace("import androidx.navigation.compose.rememberNavController", "import androidx.navigation.compose.rememberNavController\nimport androidx.navigation.navDeepLink")

# Add deep links
content = content.replace(
    "composable<Screen.Home> { HomeScreen(viewModel, ttsManager, navController) }",
    'composable<Screen.Home>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://home" })) { HomeScreen(viewModel, ttsManager, navController) }'
)

content = content.replace(
    "composable<Screen.Translation> { backStackEntry ->",
    'composable<Screen.Translation>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://translation" })) { backStackEntry ->'
)

content = content.replace(
    "composable<Screen.Learning> { LearningScreen(viewModel, ttsManager) }",
    'composable<Screen.Learning>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://learning" })) { LearningScreen(viewModel, ttsManager) }'
)

content = content.replace(
    "composable<Screen.History> { HistoryScreen(viewModel, ttsManager, navController) }",
    'composable<Screen.History>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://history" })) { HistoryScreen(viewModel, ttsManager, navController) }'
)

with open(file_path, "w") as f:
    f.write(content)
print("Patched MainScreen.kt")
