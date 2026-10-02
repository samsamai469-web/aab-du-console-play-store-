import re

file_path = "app/src/main/java/com/example/data/local/Entities.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "val adminNotes: String? = null"
replacement = "val adminNotes: String? = null,\n    val isFavorite: Boolean = false"

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched Entities.kt")
