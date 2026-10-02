import re

file_path = "app/src/main/java/com/example/ui/screens/HistoryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "if (conversations.isNotEmpty() || translations.isNotEmpty()) {"
replacement = "if (userProfile.isLoggedIn && (conversations.isNotEmpty() || translations.isNotEmpty())) {"

new_content = content.replace(target, replacement)

if new_content != content:
    with open(file_path, "w") as f:
        f.write(new_content)
    print("Patched top bar successfully!")
else:
    print("Target not found.")
