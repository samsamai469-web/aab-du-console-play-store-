with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    text = f.read()

keywords = ["Section Mooré", "Section Dioula", "Cartes", "Écouter", "Recherche", "Apprendre"]
for kw in keywords:
    count = text.count(kw)
    print(f"Keyword '{kw}': {count} occurrences")
