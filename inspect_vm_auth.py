with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "r") as f:
    text = f.read()

pos = text.find("fun loginWithGoogle")
print(text[pos:pos+1000])
