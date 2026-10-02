with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    text = f.read()

import re
matches = [m.start() for m in re.finditer(r'Screen\.Settings', text)]
for idx in matches:
    print(text[max(0, idx-200):min(len(text), idx+300)])
    print("="*40)
