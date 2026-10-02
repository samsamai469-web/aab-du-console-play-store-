import re

file_path = "app/src/main/java/com/example/util/ReminderWorker.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    private fun showNotification() {
        val channelId = "sam_traduction_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rappels de Révision"
            val descriptionText = "Notifications pour vous rappeler de réviser vos traductions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "C'est l'heure d'apprendre ! 📚",
            "Découvrez de nouveaux mots aujourd'hui.",
            "Révisez vos traductions favorites ! ⭐",
            "Maintenez votre niveau avec une session rapide."
        )
        val title = "Sam Traduction"
        val content = messages.random()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // using a default system icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)"""

replacement = """    private fun showNotification() {
        val prefs = context.getSharedPreferences("SamAiPrefs", Context.MODE_PRIVATE)
        val theme = prefs.getString("notification_theme", "general") ?: "general"
        val soundEnabled = prefs.getBoolean("notification_sound", true)
        
        val channelId = if (soundEnabled) "sam_traduction_reminders_sound" else "sam_traduction_reminders_silent"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rappels de Révision"
            val descriptionText = "Notifications pour vous rappeler de réviser vos traductions"
            val importance = if (soundEnabled) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Sam Traduction"
        val content = when (theme) {
            "favorites" -> "Révisez vos traductions favorites ! ⭐"
            "audio" -> "Pratiquez votre prononciation et écoute aujourd'hui ! 🎧"
            else -> listOf(
                "C'est l'heure d'apprendre ! 📚", 
                "Découvrez de nouveaux mots aujourd'hui.",
                "Maintenez votre niveau avec une session rapide."
            ).random()
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // using a default system icon
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(if (soundEnabled) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        if (!soundEnabled) {
            builder.setSilent(true)
        }"""

content = content.replace(target, replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched ReminderWorker.kt")
