with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    text = f.read()

print("ProfileScreen size:", len(text))
pos = text.find("showSettings")
print("showSettings pos:", pos)
if pos != -1:
    print(text[pos-100:pos+1500])
else:
    pos2 = text.find("Paramètres")
    print("Paramètres pos:", pos2)
    if pos2 != -1:
        print(text[pos2-100:pos2+1500])

