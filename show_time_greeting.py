with open("./app/src/main/java/com/example/ui/screens/HomeScreen.kt") as f:
    lines = f.readlines()
for line in lines[-80:]:
    print(line, end="")
