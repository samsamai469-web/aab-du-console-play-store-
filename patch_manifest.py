import re

file_path = "app/src/main/AndroidManifest.xml"
with open(file_path, "r") as f:
    content = f.read()

target = '<uses-permission android:name="android.permission.INTERNET" />'
replacement = target + '\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />'

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched AndroidManifest.xml")
