import re

file_path = "app/src/main/java/com/example/data/repository/LinguisticRepository.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "    suspend fun getLessonWords(difficulty: String, limit: Int) = linguisticDao.getLessonWords(difficulty, limit)"
replacement = "    suspend fun getLessonWords(difficulty: String, userId: String, limit: Int) = linguisticDao.getLessonWords(difficulty, userId, limit)"

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found in repo!")

with open(file_path, "w") as f:
    f.write(content)
print("Patched LinguisticRepository.kt")
