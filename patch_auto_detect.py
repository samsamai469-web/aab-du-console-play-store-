import re

# TranslationScreen.kt
file_path = "app/src/main/java/com/example/ui/screens/TranslationScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace(
    'val sourceLanguages = listOf("Français", "Auto-détection", "Haoussa", "Mooré", "Dioula", "Allemand", "Anglais", "Arabe", "Espagnol")',
    'val sourceLanguages = listOf("Français", "Haoussa", "Mooré", "Dioula", "Allemand", "Anglais", "Arabe", "Espagnol")'
)
content = content.replace(
    'if (sourceLang == "Haoussa" || sourceLang == "Auto-détection") {',
    'if (sourceLang == "Haoussa") {'
)
content = content.replace(
    'if (sourceLang == "Mooré" || sourceLang == "Auto-détection") {',
    'if (sourceLang == "Mooré") {'
)
content = content.replace(
    'if (sourceLang == "Dioula" || sourceLang == "Auto-détection") {',
    'if (sourceLang == "Dioula") {'
)
content = content.replace(
    'onClick = { ttsManager.speak(sourceText, if (sourceLang == "Auto-détection") null else sourceLang) }',
    'onClick = { ttsManager.speak(sourceText, sourceLang) }'
)

with open(file_path, "w") as f:
    f.write(content)

# SamAiViewModel.kt
file_path = "app/src/main/java/com/example/ui/SamAiViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

# We can leave SamAiViewModel as is because without the UI element, the viewmodel code just won't be triggered for Auto-détection.
# Or we can just remove the specific checks. It doesn't hurt.

print("Patched TranslationScreen")
