import re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """          if (uriString.startsWith("sam-ai://") ||
              uriString.startsWith("com.aistudio.samai.eltbev://") ||
              uriString.contains("/auth/callback") ||
              uriString.contains("access_token=") ||
              uriString.contains("code=")
          ) {"""

replacement = """          if (uriString.contains("/auth/callback") ||
              uriString.contains("access_token=") ||
              uriString.contains("code=")
          ) {"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched MainActivity.kt Intent handling")
