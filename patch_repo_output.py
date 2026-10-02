import re

file_path = "app/src/main/java/com/example/data/repository/TranslationRepository.kt"
with open(file_path, "r") as f:
    content = f.read()

# Update TranslationOutput
content = content.replace("val sourceBadge: String = \"✦ Traduction Directe\"", "val sourceBadge: String = \"✦ Traduction Directe\",\n    val translationId: Long? = null")

# Update saveToHistory usages to capture ID and return it
def replace_save_and_return(match):
    return match.group(0) # We'll do it manually since the code might be complex.

with open(file_path, "w") as f:
    f.write(content)
print("Updated TranslationOutput")
