import re

file_path = "app/src/main/java/com/example/ui/screens/ProfileScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profil & Paramètres",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },"""

replacement = """        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched ProfileScreen topbar")
