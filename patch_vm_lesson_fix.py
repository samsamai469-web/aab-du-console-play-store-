import re

file_path = "app/src/main/java/com/example/ui/SamAiViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    fun getLessonWords(difficulty: String): Flow<List<LinguisticKnowledge>> = flow {
        var words = linguisticRepository.getLessonWords(difficulty, currentUserId.value, 5)
        // Fallback if all words learned
        if (words.isEmpty()) {
            // Just get random words ignoring learned status if they finished the level
            words = linguisticRepository.search("").take(5) 
        }
        emit(words)
    }"""

replacement = """    fun getLessonWords(difficulty: String): Flow<List<LinguisticKnowledge>> = flow {
        var words = linguisticRepository.getLessonWords(difficulty, currentUserId.value, 5)
        emit(words)
    }"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found in VM!")

with open(file_path, "w") as f:
    f.write(content)
print("Patched SamAiViewModel.kt fixed")
