import re

file_path = "app/src/main/java/com/example/ui/SamAiViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "    fun deleteTranslationItem(id: Long) {"
replacement = """    fun toggleTranslationFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            translationRepository.toggleFavorite(id, isFavorite)
        }
    }

""" + target

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched SamAiViewModel")
