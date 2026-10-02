import re

file_path = "app/src/main/java/com/example/data/repository/ProgressRepository.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """        val newProgress = currentProgress?.copy(
            userId = userId,
            totalWordsLearned = currentProgress.totalWordsLearned + 1,
            points = currentProgress.points + points,
            lastLessonDate = System.currentTimeMillis()
        ) ?: UserProgress(
            userId = userId,
            language = language,
            totalWordsLearned = 1,
            points = points,
            lastLessonDate = System.currentTimeMillis()
        )"""

replacement = """        val newTotalWords = (currentProgress?.totalWordsLearned ?: 0) + 1
        val newPoints = (currentProgress?.points ?: 0) + points
        // Level up every 10 words
        val newLevel = (newTotalWords / 10) + 1
        
        val newProgress = currentProgress?.copy(
            userId = userId,
            totalWordsLearned = newTotalWords,
            points = newPoints,
            level = newLevel,
            lastLessonDate = System.currentTimeMillis()
        ) ?: UserProgress(
            userId = userId,
            language = language,
            totalWordsLearned = newTotalWords,
            points = newPoints,
            level = newLevel,
            lastLessonDate = System.currentTimeMillis()
        )"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found in ProgressRepository!")

with open(file_path, "w") as f:
    f.write(content)
print("Patched ProgressRepository.kt")
