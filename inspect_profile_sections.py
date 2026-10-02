with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    text = f.read()

import re
print("Length of ProfileScreen:", len(text))
matches = re.findall(r'(fun \w+|// --- .*? ---|Card\(|Button\()', text)
for m in matches[:30]:
    print(m)
