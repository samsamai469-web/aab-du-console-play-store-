with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    text = f.read()

pos = text.find("Icons.Default.Settings")
print(text[pos-200:pos+800])
