import re

file_path = "app/src/main/java/com/example/ui/screens/HistoryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add onToggleFavorite to TranslationHistoryCard signature
target1 = """fun TranslationHistoryCard(
    item: TranslationEntity,
    dateFormatter: SimpleDateFormat,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenInTranslation: (String, String) -> Unit,
    onDelete: () -> Unit
) {"""

replacement1 = """fun TranslationHistoryCard(
    item: TranslationEntity,
    dateFormatter: SimpleDateFormat,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenInTranslation: (String, String) -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {"""

content = content.replace(target1, replacement1)

# Add onToggleFavorite in HistoryScreen lazycolumn
target2 = """                                    onOpenInTranslation = { orig, tgtLang ->
                                        navController.navigate(Screen.Translation(initialText = orig, initialTargetLang = tgtLang))
                                    },
                                    onDelete = { viewModel.deleteTranslationItem(item.entity.id) }
                                )"""

replacement2 = """                                    onOpenInTranslation = { orig, tgtLang ->
                                        navController.navigate(Screen.Translation(initialText = orig, initialTargetLang = tgtLang))
                                    },
                                    onDelete = { viewModel.deleteTranslationItem(item.entity.id) },
                                    onToggleFavorite = { viewModel.toggleTranslationFavorite(item.entity.id, !item.entity.isFavorite) }
                                )"""

content = content.replace(target2, replacement2)

with open(file_path, "w") as f:
    f.write(content)
print("Patched TranslationHistoryCard signature")
