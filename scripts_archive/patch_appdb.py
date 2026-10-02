import re

file_path = "app/src/main/java/com/example/data/local/AppDatabase.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("version = 13,", "version = 14,")

with open(file_path, "w") as f:
    f.write(content)
print("Patched AppDatabase.kt")
