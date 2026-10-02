with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "r") as f:
    text = f.read()

print("ViewModel line count:", len(text.splitlines()))
import re
methods = re.findall(r'fun \w+\(', text)
for m in methods[:30]:
    print(m)
