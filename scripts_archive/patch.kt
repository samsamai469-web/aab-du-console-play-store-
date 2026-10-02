    fun isFirebaseReady(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setProjectId("sam-traduction")
                    .setApplicationId("1:438563104660:android:adc0f63c861d666ba45181")
                    .setApiKey("AIzaSyCM2nqPMvLMYxCjHFlLiVXArBbxv1RtPNk")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            auth != null
        } catch (e: Exception) {
            Log.w(TAG, "Vérification Firebase échouée: ${e.message}")
            false
        }
    }
