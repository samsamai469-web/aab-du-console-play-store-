import re

file_path = "app/src/main/java/com/example/ui/screens/ProfileScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Icons.AutoMirrored.Filled.ArrowForwardIos", "Icons.Default.KeyboardArrowRight")

with open(file_path, "w") as f:
    f.write(content)
