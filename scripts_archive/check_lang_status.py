import os

files_to_check = [
    "app/src/main/java/com/example/util/AppStrings.kt",
    "app/src/main/java/com/example/data/local/AuthSessionManager.kt",
    "app/src/main/java/com/example/ui/SamAiViewModel.kt",
    "app/src/main/java/com/example/ui/screens/ProfileScreen.kt",
    "app/src/main/java/com/example/ui/MainScreen.kt"
]

for fpath in files_to_check:
    print(f"=== {fpath} ===")
    if os.path.exists(fpath):
        with open(fpath) as f:
            content = f.read()
            if "appLanguage" in content or "AppStrings" in content or "getAppLanguage" in content:
                print("Found language keywords in file.")
            else:
                print("NO language keywords found in file!")
    else:
        print("FILE DOES NOT EXIST!")

