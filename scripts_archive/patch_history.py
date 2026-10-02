import re

file_path = "app/src/main/java/com/example/ui/screens/HistoryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# We want to replace from "// Search Bar" to the end of the Column that wraps the history content.
# Actually, the easiest way is to modify the `allHistoryItems` list directly.
# Wait, if allHistoryItems is empty, it shows "Historique vide".
# We want "Créer un compte pour voir vos historique" if not logged in.
