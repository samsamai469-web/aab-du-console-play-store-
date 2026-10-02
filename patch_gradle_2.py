import re

file_path = "app/build.gradle.kts"
with open(file_path, "r") as f:
    content = f.read()

target = "  implementation(libs.androidx.core.ktx)"
replacement = target + "\n  implementation(\"androidx.work:work-runtime-ktx:2.9.0\")"

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched build.gradle.kts properly")
