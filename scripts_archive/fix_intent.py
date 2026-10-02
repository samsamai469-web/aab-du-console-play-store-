import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("override fun onNewIntent(intent: Intent?)", "override fun onNewIntent(intent: Intent)")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
