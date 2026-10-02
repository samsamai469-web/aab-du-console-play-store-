import re

file_path = "app/src/main/java/com/example/ui/screens/SettingsScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = """    var selectedNotificationType by remember {
        mutableStateOf(sharedPrefs.getString("notification_frequency", "none") ?: "none")
    }"""

replacement1 = """    var selectedNotificationType by remember {
        mutableStateOf(sharedPrefs.getString("notification_frequency", "none") ?: "none")
    }
    
    var selectedNotificationTheme by remember {
        mutableStateOf(sharedPrefs.getString("notification_theme", "general") ?: "general")
    }
    
    var soundEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("notification_sound", true))
    }"""

target2 = """                        options.forEach { (value, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = (value == selectedNotificationType),
                                        onClick = { updateNotificationPref(value) },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (value == selectedNotificationType),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }"""

replacement2 = """                        options.forEach { (value, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = (value == selectedNotificationType),
                                        onClick = { updateNotificationPref(value) },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (value == selectedNotificationType),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                    
                    if (selectedNotificationType != "none") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        Text(
                            text = "Thème de la notification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val themes = listOf(
                            "general" to "Général (Motivation)",
                            "favorites" to "Rappel de Favoris ⭐",
                            "audio" to "Pratique Audio & Écoute 🎧"
                        )
                        
                        themes.forEach { (value, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = (value == selectedNotificationTheme),
                                        onClick = { 
                                            selectedNotificationTheme = value
                                            sharedPrefs.edit().putString("notification_theme", value).apply()
                                        },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (value == selectedNotificationTheme),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Son des notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Jouer un son à la réception",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = { 
                                    soundEnabled = it
                                    sharedPrefs.edit().putBoolean("notification_sound", it).apply()
                                }
                            )
                        }
                    }
                }
            }"""

content = content.replace(target1, replacement1)
content = content.replace(target2, replacement2)

with open(file_path, "w") as f:
    f.write(content)
print("Patched SettingsScreen.kt")
