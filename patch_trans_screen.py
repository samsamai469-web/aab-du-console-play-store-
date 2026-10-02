import re

file_path = "app/src/main/java/com/example/ui/screens/TranslationScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = "    val allKnowledge by viewModel.allKnowledge.collectAsStateWithLifecycle()"
replacement1 = target1 + "\n    val userTranslations by viewModel.userTranslations.collectAsStateWithLifecycle()"
content = content.replace(target1, replacement1)

target2 = """                                            IconButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, translatedText)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Partager la traduction"))
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "Partager",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }"""

replacement2 = """                                            IconButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, translatedText)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Partager la traduction"))
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "Partager",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            val isFav = viewModel.isCurrentTranslationFavorite(sourceText, translatedText)
                                            IconButton(
                                                onClick = { viewModel.toggleCurrentTranslationFavorite(sourceText, translatedText) }
                                            ) {
                                                Icon(
                                                    if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favoris",
                                                    tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }"""

content = content.replace(target2, replacement2)

with open(file_path, "w") as f:
    f.write(content)
print("Patched TranslationScreen")
