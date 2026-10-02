import re

file_path = "app/src/main/java/com/example/ui/screens/SettingsScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("androidx.compose.foundation.background(MaterialTheme.colorScheme.primary)", "Modifier.background(MaterialTheme.colorScheme.primary)")
content = content.replace("androidx.compose.foundation.background(MaterialTheme.colorScheme.surfaceVariant)", "Modifier.background(MaterialTheme.colorScheme.surfaceVariant)")

# Make sure we have import androidx.compose.foundation.background
if "import androidx.compose.foundation.background" not in content:
    content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.background")

with open(file_path, "w") as f:
    f.write(content)
print("Patched SettingsScreen.kt background")
