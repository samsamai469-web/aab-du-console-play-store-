import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Replace contentScale = ContentScale.Crop with alignment = Alignment.TopCenter added
    old_str = 'contentScale = ContentScale.Crop\n'
    new_str = 'contentScale = ContentScale.Crop,\n                            alignment = Alignment.TopCenter\n'
    if old_str in content and 'Alignment.TopCenter' not in content:
        content = content.replace(old_str, new_str)
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")

patch_file('/app/applet/app/src/main/java/com/example/ui/screens/AboutScreen.kt')
patch_file('/app/applet/app/src/main/java/com/example/ui/screens/OnboardingScreen.kt')
