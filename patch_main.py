import sys

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

import_lines = """import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.ui.screens.OnboardingScreen
"""

# Insert imports
content = content.replace("import com.example.ui.theme.MyApplicationTheme", import_lines + "import com.example.ui.theme.MyApplicationTheme")

set_content_old = """    setContent {
      MyApplicationTheme {
        MainScreen(viewModel, ttsManager)
      }
    }"""

set_content_new = """    val prefs = getSharedPreferences("SamAiPrefs", Context.MODE_PRIVATE)
    
    setContent {
      MyApplicationTheme {
        var hasSeenOnboarding by remember { mutableStateOf(prefs.getBoolean("has_seen_onboarding", false)) }
        
        if (!hasSeenOnboarding) {
            OnboardingScreen(
                onFinish = {
                    prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                    hasSeenOnboarding = true
                }
            )
        } else {
            MainScreen(viewModel, ttsManager)
        }
      }
    }"""

content = content.replace(set_content_old, set_content_new)

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
