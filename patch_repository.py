import re

file_path = "app/src/main/java/com/example/data/repository/TranslationRepository.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "    fun getHistoryForUser(userId: String): Flow<List<TranslationEntity>> =\n        translationDao.getTranslationsForUser(userId)"
replacement = target + """

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        translationDao.updateFavorite(id, isFavorite)
    }
"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched TranslationRepository")
