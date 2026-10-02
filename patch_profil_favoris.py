import re

file_path = "app/src/main/java/com/example/ui/screens/ProfileScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = "            // Settings & Preferences Section"
replacement = """            // Favorites Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Favorites) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                ListItem(
                    headlineContent = { Text("Phrases Favorites", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Traductions que vous avez sauvegardées") },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Voir",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Settings & Preferences Section"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched ProfileScreen favorites card")
