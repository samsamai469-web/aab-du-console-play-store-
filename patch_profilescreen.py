import re

file_path = "app/src/main/java/com/example/ui/screens/ProfileScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Add `val themePreference by viewModel.themePreference.collectAsState()`
target1 = "    val userProfile by viewModel.userProfile.collectAsState()"
replacement1 = target1 + "\n    val themePreference by viewModel.themePreference.collectAsState()"
content = content.replace(target1, replacement1)

# 2. Add the Theme ListItem
injection2 = """                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var showThemeMenu by remember { mutableStateOf(false) }
                    val themeLabel = when(themePreference) {
                        "light" -> "Clair"
                        "dark" -> "Sombre"
                        else -> "Système"
                    }
                    ListItem(
                        headlineContent = { Text("Apparence") },
                        supportingContent = { Text(themeLabel) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { showThemeMenu = true }) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "Changer le thème",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showThemeMenu,
                                    onDismissRequest = { showThemeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Clair") },
                                        onClick = {
                                            viewModel.setThemePreference("light")
                                            showThemeMenu = false
                                        },
                                        leadingIcon = {
                                            if (themePreference == "light") {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sombre") },
                                        onClick = {
                                            viewModel.setThemePreference("dark")
                                            showThemeMenu = false
                                        },
                                        leadingIcon = {
                                            if (themePreference == "dark") {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Système") },
                                        onClick = {
                                            viewModel.setThemePreference("system")
                                            showThemeMenu = false
                                        },
                                        leadingIcon = {
                                            if (themePreference == "system" || (themePreference != "light" && themePreference != "dark")) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
"""

target2 = "                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)"

content = content.replace(target2, injection2 + target2, 1)

with open(file_path, "w") as f:
    f.write(content)
print("Patched ProfileScreen")
