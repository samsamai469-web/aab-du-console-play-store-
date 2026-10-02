with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "r") as f:
    text = f.read()

import re
matches = [m.start() for m in re.finditer(r'PreferencesSection|Préférences|Paramètres|Notifications|A propos|About', text, re.IGNORECASE)]
for idx in matches[:5]:
    print(text[max(0, idx-100):min(len(text), idx+200)])
    print("="*40)
