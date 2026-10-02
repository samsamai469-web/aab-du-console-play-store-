import re

file_path = "app/src/main/java/com/example/ui/screens/LearningScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = "    val wordsState by viewModel.startLesson(difficulty).collectAsState(initial = emptyList())"
replacement1 = "    val wordsState by viewModel.startLesson(difficulty).collectAsState(initial = null)"

target2 = "        if (wordsState.isNotEmpty() && currentIndex < wordsState.size) {"
replacement2 = "        if (wordsState != null && wordsState!!.isNotEmpty() && currentIndex < wordsState!!.size) {"

target3 = "            val word = wordsState[currentIndex]"
replacement3 = "            val word = wordsState!![currentIndex]"

target4 = "                    if (currentIndex < wordsState.size - 1) {"
replacement4 = "                    if (currentIndex < wordsState!!.size - 1) {"

target5 = "                    text = if (currentIndex < wordsState.size - 1) \"Mot Suivant\" else \"Terminer la Leçon\","
replacement5 = "                    text = if (currentIndex < wordsState!!.size - 1) \"Mot Suivant\" else \"Terminer la Leçon\","

target6 = "        } else if (wordsState.isEmpty()) {"
replacement6 = """        } else if (wordsState == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (wordsState!!.isEmpty()) {"""

target7 = "                text = if (wordsState.isNotEmpty()) \"Leçon $language ${currentIndex + 1}/${wordsState.size}\" else \"Leçon $language\","
replacement7 = "                text = if (wordsState != null && wordsState!!.isNotEmpty()) \"Leçon $language ${currentIndex + 1}/${wordsState!!.size}\" else \"Leçon $language\","

content = content.replace(target1, replacement1)
content = content.replace(target2, replacement2)
content = content.replace(target3, replacement3)
content = content.replace(target4, replacement4)
content = content.replace(target5, replacement5)
content = content.replace(target6, replacement6)
content = content.replace(target7, replacement7)

with open(file_path, "w") as f:
    f.write(content)
print("Patched LearningScreen.kt for loading state")
