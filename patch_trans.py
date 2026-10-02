import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 1. Remove viewModel.translate from speechLauncher callback
    old_cb = '''            if (!spokenText.isNullOrBlank()) {
                viewModel.setSourceText(spokenText)
                viewModel.translate(source = sourceLang, target = targetLang, text = spokenText)
            }'''
    new_cb = '''            if (!spokenText.isNullOrBlank()) {
                viewModel.setSourceText(spokenText)
            }'''
    content = content.replace(old_cb, new_cb)

    # 2. Add microphone icon to the TextField
    old_tf = '''                                TextField(
                                    value = sourceText,
                                    onValueChange = { newText ->
                                        viewModel.setSourceText(newText)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp),
                                    placeholder = {
                                        Text(
                                            "Entrez votre mot, phrase ou nombre en Français, Mooré, Dioula, Anglais...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    },
                                    colors = TextFieldDefaults.colors('''
    new_tf = '''                                TextField(
                                    value = sourceText,
                                    onValueChange = { newText ->
                                        viewModel.setSourceText(newText)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp),
                                    placeholder = {
                                        Text(
                                            "Entrez votre mot, phrase ou nombre en Français, Mooré, Dioula, Anglais...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant...")
                                                }
                                                try {
                                                    speechLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    // Handle exception
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = "Dicter",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    colors = TextFieldDefaults.colors('''
    content = content.replace(old_tf, new_tf)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

patch_file('/app/applet/app/src/main/java/com/example/ui/screens/TranslationScreen.kt')
