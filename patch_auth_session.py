import re

file_path = "app/src/main/java/com/example/data/local/AuthSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

injection = """
    /**
     * Récupère la préférence de thème de l'utilisateur
     */
    fun getThemePreference(): String {
        return prefs.getString(KEY_THEME_PREFERENCE, "system") ?: "system"
    }

    /**
     * Enregistre la préférence de thème
     */
    fun setThemePreference(theme: String) {
        prefs.edit()
            .putString(KEY_THEME_PREFERENCE, theme)
            .apply()
    }
"""

target = "    fun isOnboardingCompleted(): Boolean {"
replacement = injection + "\n" + target

content = content.replace(target, replacement)
content = content.replace("private const val KEY_ONBOARDING_COMPLETED =", "private const val KEY_THEME_PREFERENCE = \"app_theme_preference\"\n        private const val KEY_ONBOARDING_COMPLETED =")

with open(file_path, "w") as f:
    f.write(content)
print("Patched AuthSessionManager")
