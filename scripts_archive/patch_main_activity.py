import re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add a state for target screen
if "var targetScreen by remember { mutableStateOf<String?>(null) }" not in content:
    content = content.replace(
        "val hasCompletedOnboarding by viewModel.isOnboardingCompleted.collectAsState()",
        """val hasCompletedOnboarding by viewModel.isOnboardingCompleted.collectAsState()
                var targetScreen by remember { mutableStateOf(intent.getStringExtra("target_screen")) }"""
    )
    
    # We can just let the intent data handle deep links if they work.
    # Actually, Compose Navigation automatically handles intent.data if it matches a deepLink.
    # Let's ensure the Intent ACTION_VIEW is used in the Worker.
