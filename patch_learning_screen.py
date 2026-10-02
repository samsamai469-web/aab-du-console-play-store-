import re

file_path = "app/src/main/java/com/example/ui/screens/LearningScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """        } else if (questions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}"""

replacement = """        } else if (wordsState.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Félicitations ! Vous avez appris tous les mots de cette catégorie pour le moment.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("Retourner à l'accueil")
                    }
                }
            }
        }
    }
}"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found in LearningScreen!")

with open(file_path, "w") as f:
    f.write(content)
print("Patched LearningScreen empty state")
