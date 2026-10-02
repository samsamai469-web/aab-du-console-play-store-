import re

file_path = "app/src/main/java/com/example/ui/navigation/NavGraph.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "    @Serializable\n    data object Favorites : Screen()"
replacement = target + "\n    @Serializable\n    data object Settings : Screen()"

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched NavGraph.kt with Settings")
