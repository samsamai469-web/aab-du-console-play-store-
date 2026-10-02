import re

file_path = "app/src/main/java/com/example/ui/screens/HistoryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "Créer un compte pour voir votre historique"
replacement = "Créer un compte pour voir vos historique"

new_content = content.replace(target, replacement)

if new_content != content:
    with open(file_path, "w") as f:
        f.write(new_content)
    print("Patched text successfully!")
