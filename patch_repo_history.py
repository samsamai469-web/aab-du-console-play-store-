import re

file_path = "app/src/main/java/com/example/data/repository/TranslationRepository.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = """    suspend fun saveToHistory(
        originalText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String,
        userId: String = "guest",
        isValidated: Boolean = false,
        correctedText: String? = null
    ) {"""

replacement1 = """    suspend fun saveToHistory(
        originalText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String,
        userId: String = "guest",
        isValidated: Boolean = false,
        correctedText: String? = null
    ): Long? {"""

target2 = """        try {
            translationDao.insertTranslation(
                TranslationEntity(
                    userId = userId,
                    originalText = originalText,
                    translatedText = translatedText,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    isValidatedCorrection = isValidated,
                    correctedText = correctedText,
                    correctionDate = if (isValidated) System.currentTimeMillis() else null
                )
            )
        } catch (e: Exception) {
            // Ignore insert failure
        }"""

replacement2 = """        return try {
            translationDao.insertTranslation(
                TranslationEntity(
                    userId = userId,
                    originalText = originalText,
                    translatedText = translatedText,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    isValidatedCorrection = isValidated,
                    correctedText = correctedText,
                    correctionDate = if (isValidated) System.currentTimeMillis() else null
                )
            )
        } catch (e: Exception) {
            null
        }"""

content = content.replace(target1, replacement1)
content = content.replace(target2, replacement2)

with open(file_path, "w") as f:
    f.write(content)
print("Patched TranslationRepository saveToHistory")
