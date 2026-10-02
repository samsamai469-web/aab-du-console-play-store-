import re

file_path = "app/src/main/java/com/example/data/local/Daos.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    @Query("SELECT * FROM linguistic_knowledge WHERE difficulty = :difficulty AND status = 'validated' LIMIT :limit")
    suspend fun getLessonWords(difficulty: String, limit: Int): List<LinguisticKnowledge>"""

replacement = """    @Query("SELECT * FROM linguistic_knowledge WHERE difficulty = :difficulty AND status = 'validated' AND id NOT IN (SELECT wordId FROM learned_words WHERE userId = :userId) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getLessonWords(difficulty: String, userId: String, limit: Int): List<LinguisticKnowledge>"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found in Daos.kt!")

with open(file_path, "w") as f:
    f.write(content)
print("Patched Daos.kt")
