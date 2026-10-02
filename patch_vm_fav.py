import re

file_path = "app/src/main/java/com/example/ui/SamAiViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

injection = """
    fun toggleCurrentTranslationFavorite(original: String, translated: String) {
        viewModelScope.launch {
            val latest = userTranslations.value.firstOrNull { 
                it.originalText.trim() == original.trim() && it.translatedText.trim() == translated.trim() 
            }
            if (latest != null) {
                translationRepository.toggleFavorite(latest.id, !latest.isFavorite)
            }
        }
    }

    fun isCurrentTranslationFavorite(original: String, translated: String): Boolean {
        return userTranslations.value.firstOrNull { 
            it.originalText.trim() == original.trim() && it.translatedText.trim() == translated.trim() 
        }?.isFavorite ?: false
    }
"""

content = content.replace("    fun toggleTranslationFavorite(id: Long, isFavorite: Boolean) {", injection + "\n    fun toggleTranslationFavorite(id: Long, isFavorite: Boolean) {")

with open(file_path, "w") as f:
    f.write(content)
print("Patched VM")
