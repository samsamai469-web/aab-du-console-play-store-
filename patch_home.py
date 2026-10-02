import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 1. Remove voicebox state
    content = content.replace('    var showVoiceBox by remember { mutableStateOf(false) }\n', '')

    # 2. Remove the top bar microphone action
    old_action = '''                actions = {
                    IconButton(onClick = { showVoiceBox = true }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Boîte Vocale",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.About) }) {'''
    new_action = '''                actions = {
                    IconButton(onClick = { navController.navigate(Screen.About) }) {'''
    content = content.replace(old_action, new_action)

    # 3. Remove the FloatingActionButton
    old_fab = '''        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showVoiceBox = true },
                icon = { Icon(Icons.Default.Mic, contentDescription = "Boîte Vocale") },
                text = { Text("Boîte Vocale", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            )
        },'''
    new_fab = '''        floatingActionButton = {},'''
    content = content.replace(old_fab, new_fab)

    # 4. Remove the VoiceBoxSheet instantiation
    old_sheet = '''    if (showVoiceBox) {
        VoiceBoxSheet(
            viewModel = viewModel,
            ttsManager = ttsManager,
            onDismiss = { showVoiceBox = false },
            targetLanguage = "Mooré"
        )
    }'''
    new_sheet = ''
    content = content.replace(old_sheet, new_sheet)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

patch_file('/app/applet/app/src/main/java/com/example/ui/screens/HomeScreen.kt')
