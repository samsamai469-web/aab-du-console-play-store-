import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

imports = """import android.content.Intent
import android.net.Uri
import android.widget.Toast
"""
if "import android.content.Intent" not in content:
    content = content.replace("import android.os.Bundle", imports + "import android.os.Bundle")

on_create = """
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    ttsManager = TtsManager(this)
    setContent {
      MyApplicationTheme {
        MainScreen(viewModel, ttsManager)
      }
    }
    handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null) {
        handleIntent(intent)
    }
  }

  private fun handleIntent(intent: Intent) {
      val data: Uri? = intent.data
      if (data != null && data.toString().contains("access_token=")) {
          viewModel.handleOAuthRedirect(data.toString()) { success, errorMsg ->
              if (success) {
                  Toast.makeText(this, "Connexion OAuth réussie !", Toast.LENGTH_SHORT).show()
              } else {
                  Toast.makeText(this, "Erreur OAuth : $errorMsg", Toast.LENGTH_LONG).show()
              }
          }
      }
  }
"""

# Replace onCreate block
content = re.sub(r"  override fun onCreate\(savedInstanceState: Bundle\?\).*?setContent \{.*?\}\s*\}", on_create, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
