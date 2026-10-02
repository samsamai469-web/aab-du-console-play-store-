import re

file_path = "app/src/main/java/com/example/ui/screens/HistoryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """                    IconButton(
                        onClick = { onShare(effectiveResult) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Partager",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }"""

replacement = """                    IconButton(
                        onClick = { onShare(effectiveResult) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Partager",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favoris",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched TranslationHistoryCard actions")
