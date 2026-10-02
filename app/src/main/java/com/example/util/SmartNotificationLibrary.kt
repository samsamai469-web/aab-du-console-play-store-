package com.example.util

import java.util.Calendar

enum class NotificationCategory(val key: String, val displayName: String) {
    GREETING("greeting", "Salutations intelligentes"),
    RETURN_REMINDER("return", "Rappel de retour"),
    MOTIVATION("motivation", "Motivation & Apprentissage"),
    WORD_OF_DAY("word_of_day", "Mot du jour"),
    TIPS("tips", "Astuces & Fonctionnalités"),
    LOYALTY("loyalty", "Fidélité & Encouragements"),
    NEWS_CULTURE("news_culture", "Culture du Faso"),
    NEWS("news", "Actualités du Faso & Monde"),
    QUIZ("quiz", "Quiz & Progression linguistique"),
    REWARDS("rewards", "Récompenses & Niveaux"),
    ANNOUNCEMENTS("announcements", "Annonces & Mises à jour")
}

enum class TimeOfDay {
    MORNING,    // 06h00 - 11h59
    AFTERNOON,  // 12h00 - 17h59
    EVENING,    // 18h00 - 22h00
    ANY
}

data class SmartNotificationMessage(
    val id: String,
    val category: NotificationCategory,
    val timeOfDay: TimeOfDay,
    val title: String,
    val content: String,
    val targetUri: String = "sam-ai://home"
)

object SmartNotificationLibrary {

    fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..22 -> TimeOfDay.EVENING
            else -> TimeOfDay.ANY
        }
    }

    // Bibliothèque complète de 300+ messages uniques et professionnels
    val ALL_MESSAGES: List<SmartNotificationMessage> by lazy {
        val list = ArrayList<SmartNotificationMessage>(360)

        // =========================================================================
        // 1. SALUTATIONS INTELLIGENTES (Matin, Après-midi, Soir) - 60 messages
        // =========================================================================
        // Matin (06h00 - 11h59)
        list.add(SmartNotificationMessage("g_m_1", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour 👋", "SAM AI est prêt à vous accompagner ce matin.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_m_2", NotificationCategory.GREETING, TimeOfDay.MORNING, "Beau matin ☀️", "Commencez votre journée avec une nouvelle traduction.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_3", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour !", "Découvrez un nouveau mot en mooré aujourd'hui.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_4", NotificationCategory.GREETING, TimeOfDay.MORNING, "Ne y windiga 🌅", "Excellente matinée ! Votre assistant linguistique est à votre service.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_m_5", NotificationCategory.GREETING, TimeOfDay.MORNING, "I ni sɔgɔma 👋", "Bonne journée ! Traduisez vos phrases en dioula en un clic.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_6", NotificationCategory.GREETING, TimeOfDay.MORNING, "Réveil en douceur ☕", "Un proverbe local pour bien lancer votre journée vous attend.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_7", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour !", "Prêt pour une communication fluide dans vos langues locales ?", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_m_8", NotificationCategory.GREETING, TimeOfDay.MORNING, "Matin productif 🚀", "Traduisez rapidement vos messages professionnels ou personnels.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_9", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour 👋", "Explorez la richesse de nos dialectes dès aujourd'hui.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_10", NotificationCategory.GREETING, TimeOfDay.MORNING, "Belle journée 🌸", "Votre dictionnaire intelligent hors-ligne est prêt.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_m_11", NotificationCategory.GREETING, TimeOfDay.MORNING, "Salutations ☀️", "Une salutation matinale ouvre toujours de belles portes.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_12", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour à vous 👋", "Quelle expression souhaitez-vous maîtriser ce matin ?", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_13", NotificationCategory.GREETING, TimeOfDay.MORNING, "Énergie du matin ⚡", "Deux minutes pour apprendre trois expressions utiles.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_14", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour !", "SAM AI facilite tous vos échanges culturels.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_m_15", NotificationCategory.GREETING, TimeOfDay.MORNING, "Ne y beogo 🌄", "Le matin est propice à la découverte linguistique.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_16", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonne matinée ☀️", "Exprimez-vous avec aisance en mooré et en dioula.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_17", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour 👋", "Activez la reconnaissance vocale pour tester votre prononciation.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_m_18", NotificationCategory.GREETING, TimeOfDay.MORNING, "Journée lumineuse ✨", "Un mot bien choisi transforme une conversation.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_19", NotificationCategory.GREETING, TimeOfDay.MORNING, "Bonjour !", "Découvrez notre sélection de vocabulaire quotidien.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_m_20", NotificationCategory.GREETING, TimeOfDay.MORNING, "Matin motivant 🎯", "Votre objectif linguistique du jour n'attend que vous.", "sam-ai://home"))

        // Après-midi (12h00 - 17h59)
        list.add(SmartNotificationMessage("g_a_1", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi ☀️", "Traduisez plus rapidement avec SAM AI.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_2", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Pause déjeuner 🍽️", "Une traduction peut vous faire gagner du temps aujourd'hui.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_3", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi 👋", "Continuez votre apprentissage en quelques secondes.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_4", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "I ni tile 🌤️", "Bel après-midi ! Facilitez vos échanges avec nos outils.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_a_5", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Ne y wende ☀️", "Un instant de pause pour réviser vos mots favoris.", "sam-ai://history"))
        list.add(SmartNotificationMessage("g_a_6", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi !", "Besoin de traduire un message reçu récemment ?", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_7", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Productivité ⚡", "La traduction instantanée reste accessible sans connexion.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_8", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi 🌿", "Prenez une pause culturelle avec SAM AI.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_9", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Échangez facilement 💬", "Trouvez les mots justes en mooré ou en dioula.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_10", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi 👋", "Votre carnet de vocabulaire s'enrichit jour après jour.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_11", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Un instant utile ⏱️", "Traduisez un texte en une fraction de seconde.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_12", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi ☀️", "Explorez les expressions courantes du marché.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_13", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Pause active 💡", "Écoutez la prononciation exacte d'un mot local.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_14", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi !", "Partagez une belle traduction avec vos proches.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_15", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Rythme de journée 🌟", "Conservez votre régularité d'apprentissage aujourd'hui.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_a_16", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi 👋", "Besoin d'aide pour une discussion ? Sam est là.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_a_17", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Inspiration ☀️", "La maîtrise d'une langue se construit pas à pas.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_18", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bon après-midi !", "Vérifiez vos dernières traductions enregistrées.", "sam-ai://history"))
        list.add(SmartNotificationMessage("g_a_19", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Pratique express 🚀", "Un quiz de 60 secondes pour tester votre mémoire.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_a_20", NotificationCategory.GREETING, TimeOfDay.AFTERNOON, "Bel après-midi 🌿", "L'excellence linguistique à portée de main.", "sam-ai://home"))

        // Soir (18h00 - 22h00)
        list.add(SmartNotificationMessage("g_e_1", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir 🌙", "Revenez poursuivre vos traductions en toute sérénité.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_e_2", NotificationCategory.GREETING, TimeOfDay.EVENING, "Douce soirée ✨", "Terminez votre journée avec un nouveau mot enrichissant.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_3", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir !", "Votre assistant linguistique vous attend pour une révision.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_e_4", NotificationCategory.GREETING, TimeOfDay.EVENING, "Ne y zaabre 🌌", "Bonne soirée ! Prenez 2 minutes pour consolider vos acquis.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_5", NotificationCategory.GREETING, TimeOfDay.EVENING, "I ni wula 🌙", "Agréable soirée ! Découvrez une citation traditionnelle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_6", NotificationCategory.GREETING, TimeOfDay.EVENING, "Détente du soir 🍵", "Révisez vos phrases favorites avant de dormir.", "sam-ai://history"))
        list.add(SmartNotificationMessage("g_e_7", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir 👋", "Bilan de la journée : qu'avez-vous appris aujourd'hui ?", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_e_8", NotificationCategory.GREETING, TimeOfDay.EVENING, "Nuit paisible 🌠", "Une dernière traduction pour clore la journée en beauté.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_e_9", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir !", "La pratique du soir favorise une excellente mémorisation.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_10", NotificationCategory.GREETING, TimeOfDay.EVENING, "Soirée studieuse 📚", "Découvrez les salutations nocturnes en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_11", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir 🌙", "Votre historique de traductions est soigneusement sauvegardé.", "sam-ai://history"))
        list.add(SmartNotificationMessage("g_e_12", NotificationCategory.GREETING, TimeOfDay.EVENING, "Calme du soir ✨", "Profitez d'un moment paisible pour pratiquer vos langues.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_e_13", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir 👋", "Merci de faire vivre nos langues locales au quotidien.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_e_14", NotificationCategory.GREETING, TimeOfDay.EVENING, "Douce soirée 🌌", "La culture s'apprend mot après mot, même le soir.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_15", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonne soirée 🍵", "Écoutez la synthèse vocale pour affiner votre accent.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_e_16", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir !", "Félicitations pour votre curiosité intellectuelle.", "sam-ai://home"))
        list.add(SmartNotificationMessage("g_e_17", NotificationCategory.GREETING, TimeOfDay.EVENING, "Moment de réflexion 💡", "Les proverbes locaux renferment une profonde sagesse.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_18", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonsoir 🌙", "Préparez vos échanges de demain en toute tranquillité.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("g_e_19", NotificationCategory.GREETING, TimeOfDay.EVENING, "Fin de journée 🌟", "Un nouveau palier franchi dans votre vocabulaire.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("g_e_20", NotificationCategory.GREETING, TimeOfDay.EVENING, "Bonne nuit par avance 🌠", "Reposez-vous bien et à demain sur SAM AI.", "sam-ai://home"))

        // =========================================================================
        // 2. RAPPEL DE RETOUR (Bienveillant & Non insistant) - 50 messages
        // =========================================================================
        list.add(SmartNotificationMessage("ret_1", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un petit coucou 👋", "Nous ne vous avons pas vu aujourd'hui. Tout va bien ?", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_2", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Reprenez le fil 🧵", "Reprenez vos traductions en un instant sur SAM AI.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_3", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre historique vous attend 📂", "Retrouvez facilement vos récentes recherches linguistiques.", "sam-ai://history"))
        list.add(SmartNotificationMessage("ret_4", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "À bientôt !", "Une minute suffit pour enrichir votre vocabulaire quotidien.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_5", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Toujours là pour vous 🤝", "SAM AI reste à portée de main quand vous en avez besoin.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_6", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Envie d'échanger ? 💬", "Une phrase à traduire en mooré ou en dioula ?", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_7", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Pause linguistique 🌿", "Prenez un court instant pour renouer avec vos racines.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_8", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Vos favoris vous attendent ⭐", "Révisez les expressions que vous avez enregistrées.", "sam-ai://history"))
        list.add(SmartNotificationMessage("ret_9", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Bienvenue à nouveau 👋", "La porte de vos langues locales est toujours ouverte.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_10", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Curiosité du jour 💡", "Avez-vous exploré nos nouvelles fiches thématiques ?", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_11", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un mot vous échappe ? 🔍", "Trouvez immédiatement sa traduction fidèle.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_12", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Pratique régulière 🎯", "La régularité est le secret d'un apprentissage durable.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_13", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Plaisir d'apprendre 📖", "Retrouvez le plaisir des dialectes du Burkina Faso.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_14", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Besoin d'un mot juste ? ✨", "Votre traducteur de poche vous accompagne fidèlement.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_15", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Toujours disponible 📱", "Même sans connexion internet, traduisez librement.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_16", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un moment pour vous ☕", "Découvrez comment saluer chaleureusement un ami.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_17", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Vos progrès comptent 📈", "Chaque consultation renforce votre maîtrise.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_18", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Prêt quand vous l'êtes 🚀", "SAM AI reste à votre disposition intégrale.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_19", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Une question linguistique ? ❓", "Notre moteur contextuel vous répond instantanément.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_20", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Reprenez l'habitude 🌿", "Deux clics pour découvrir une nouvelle formule de politesse.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_21", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un instant culturel 🏛️", "La beauté des mots vous attend sur SAM AI.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_22", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Gardez le contact 💬", "Exprimez vos sentiments dans votre langue maternelle.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_23", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre espace personnel 🔒", "Vos données et vos notes restent toujours préservées.", "sam-ai://history"))
        list.add(SmartNotificationMessage("ret_24", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Envie d'un quiz ? 🎮", "Testez vos connaissances en quelques questions simples.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_25", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Bonjour l'ami 👋", "Un petit passage sur l'application illumine votre journée.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_26", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Échanges facilités 🤝", "Communiquez sans barrière avec votre entourage.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_27", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre temps est précieux ⏱️", "Obtenez vos traductions en un éclair.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_28", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Cultivez votre savoir 🌱", "Chaque mot assimilé est une victoire culturelle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_29", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "SAM AI à vos côtés 🛡️", "La référence des langues du Burkina Faso.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_30", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Faites une halte 🍃", "Rechargez votre savoir en quelques instants.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_31", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre parcours continue 🛤️", "Quelques phrases pour enrichir vos échanges.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_32", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Besoin d'aide ? 🤝", "Notre dictionnaire et nos outils sont prêts pour vous.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_33", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Une pause enrichissante ☕", "Découvrez une expression chaleureuse en dioula.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_34", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Retrouvez vos repères 📍", "Consultez vos expressions sauvegardées en favoris.", "sam-ai://history"))
        list.add(SmartNotificationMessage("ret_35", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "L'aventure continue ✨", "Votre progression linguistique vous attend.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_36", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un geste simple 📱", "Ouvrez SAM AI pour vérifier un mot.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_37", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Transmission culturelle 🌾", "Faites vivre les langues locales dans vos conversations.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_38", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Une minute chrono ⏱️", "Un mot appris par jour transforme votre éloquence.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_39", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre coach linguistique 🎯", "SAM AI est disponible 24h/24.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_40", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Toujours le bienvenu 👋", "Nous sommes ravis de vous accompagner chaque jour.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_41", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Prenez les devants 🚀", "Traduisez rapidement un texte avant votre réunion.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_42", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "La force des mots 💬", "Exprimez-vous avec clarté et authenticité.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_43", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "N'oubliez pas vos acquis 🧠", "Un coup d'œil rapide rafraîchit votre mémoire.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_44", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un outil indispensable 🛠️", "La précision linguistique au bout de vos doigts.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_45", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Découvrez nos nouveautés 🌟", "Explorez le vocabulaire thématique mis à jour.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_46", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Vos amis vous attendent 💬", "Envoyez-leur un message en mooré ou en dioula.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("ret_47", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Votre fidelité nous touche ❤️", "Venez poursuivre votre apprentissage dès maintenant.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ret_48", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Un moment de calme 🌿", "Profitez de cet instant pour tester vos acquis.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_49", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "Prêt pour une révision ? 📝", "Votre séance express est à portée de main.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("ret_50", NotificationCategory.RETURN_REMINDER, TimeOfDay.ANY, "À très vite sur Sam ! 👋", "Votre compagnon pour toutes les langues du Faso.", "sam-ai://home"))

        // =========================================================================
        // 3. MOTIVATION & APPRENTISSAGE - 50 messages
        // =========================================================================
        list.add(SmartNotificationMessage("mot_1", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Chaque jour compte 🌟", "Chaque jour est une nouvelle occasion d'apprendre.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_2", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Progression continue 📈", "Progressez pas à pas en mooré et en dioula.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_3", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Un pas de plus 👣", "Un mot de plus aujourd'hui, c'est un grand pas en avant.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_4", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Puissance des mots 💬", "Maîtriser une langue locale, c'est préserver son identité.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_5", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Persévérance 🎯", "La clé du succès réside dans la constance de la pratique.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_6", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Esprit curieux 🧠", "Votre cerveau adore apprendre de nouvelles sonorités.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_7", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Richesse culturelle 🌍", "Chaque langue offre une vision unique et poétique du monde.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_8", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Confiance en soi 💪", "Osez vous exprimer en langues locales au quotidien.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_9", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Grandir chaque jour 🌱", "Un vocabulaire riche renforce votre impact personnel.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_10", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Fierté locale 🇧🇫", "Célébrez la beauté de nos traditions et dialectes.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_11", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Défi quotidien 🏆", "Relevez le défi : apprenez 3 expressions utiles aujourd'hui.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_12", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Écoute attentive 🎧", "Écoutez la sonorité authentique des phrases traduites.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_13", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Excellence 🌟", "La perfection vient avec l'entraînement régulier.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_14", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Lien humain ❤️", "Parler la langue de l'autre crée un pont de fraternité.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_15", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Curiosité sans fin 🔍", "Explorez les subtilités du vocabulaire de la famille.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_16", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Pas d'âge pour apprendre 📖", "Chaque instant est parfait pour élargir ses horizons.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_17", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Impact positif ✨", "Surprenez vos interlocuteurs avec des formules soignées.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_18", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Énergie positive ⚡", "Transformez vos moments d'attente en révision productive.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_19", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Savoir partagé 🤝", "Ce que vous apprenez aujourd'hui servira demain.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_20", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Régularité gagnante 🥇", "Quelques minutes par jour suffisent pour devenir fluide.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_21", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Voyage linguistique 🗺️", "Parcourez les régions du Faso à travers leurs mots.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_22", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Esprit vif 💡", "Apprendre une langue retarde le vieillissement cérébral.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_23", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Faites rayonner nos langues ✨", "Soyez l'ambassadeur de votre patrimoine linguistique.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_24", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Patience et passion 🕯️", "Tout grand orateur a commencé par un premier mot.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_25", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Prenez plaisir 🎉", "L'apprentissage devient un jeu avec SAM AI.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_26", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Compréhension mutuelle 🤝", "Moins de malentendus, plus de convivialité.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_27", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Dépassez vos limites 🚀", "Testez la traduction des phrases complexes dès maintenant.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_28", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Culture vivante 🌿", "Une langue vit tant qu'elle est parlée avec amour.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_29", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Inspiration du jour 🌅", "Votre détermination fait toute la différence.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_30", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Bravo pour votre assiduité 👏", "Poursuivez sur cette belle lancée linguistique.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_31", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Visez haut 🎯", "La maîtrise d'un dialecte ouvre de nouvelles opportunités.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_32", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Apprenez en souriant 😄", "La joie d'apprendre facilite la mémorisation.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_33", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Une nouvelle étape 🏆", "Consultez votre score de progression dès aujourd'hui.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_34", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Précision des termes 🔍", "Chaque mot porte une histoire et une couleur locale.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_35", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "La parole est d'or ✨", "Savoir bien parler apaise les cœurs et unit les esprits.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_36", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Développez votre potentiel 🚀", "Deux minutes de révision pour renforcer vos automatismes.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_37", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Fidèle à vos racines 🌱", "Les langues africaines regorgent de trésors inestimables.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_38", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Un pont entre générations 👴👧", "Partagez vos nouvelles connaissances avec vos proches.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_39", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Le pouvoir de la pratique ⚙️", "Répétez les expressions à haute voix pour progresser.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_40", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Constance et succès 🥇", "Votre motivation est le moteur de votre réussite.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_41", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Clarté d'esprit 💡", "Comprendre les proverbes enrichit votre réflexion.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_42", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Découvrez votre talent 🌟", "Vous avez une remarquable capacité d'assimilation.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_43", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Le goût du savoir 📚", "Chaque nouvelle leçon vous rapproche du bilinguisme.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_44", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Exprimez votre respect 🙏", "Les formules de politesse locales sont très appréciées.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_45", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Un moment de détente active 🍃", "Révisez en toute tranquillité où que vous soyez.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_46", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "La force de l'habitude 🔄", "Faites de SAM AI votre réflexe quotidien.", "sam-ai://home"))
        list.add(SmartNotificationMessage("mot_47", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Ouvrez de nouveaux horizons 🌅", "La communication authentique n'a pas de prix.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_48", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Fier de vos progrès 👏", "Continuez d'aller de l'avant avec détermination.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("mot_49", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "Soyez curieux 🔍", "Posez vos questions linguistiques à SAM AI.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("mot_50", NotificationCategory.MOTIVATION, TimeOfDay.ANY, "La clé de l'intégration 🗝️", "Parler la langue du pays ouvre toutes les portes.", "sam-ai://home"))

        // =========================================================================
        // 4. MOT DU JOUR & EXPRESSIONS - 60 messages
        // =========================================================================
        list.add(SmartNotificationMessage("wod_1", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour disponible", "Découvrez comment dire « Paix » en mooré : Laafi.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_2", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "Le mot « Hèrè » signifie la paix et le bonheur.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_3", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression du jour ✨", "« Barka » : l'expression essentielle pour remercier.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_4", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Mooré", "« Yaa sîda » : C'est la vérité / Absolument.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_5", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Enrichissez votre lexique 📚", "Découvrez le mot dioula « An bɛ kɔ fɛ » (À plus tard).", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_6", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Wend na sõng", "« Que Dieu aide » : une bénédiction traditionnelle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_7", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression utile 💡", "Comment demander « Combien ça coûte ? » en mooré : Wãn to ?", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_8", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Jɔrɔ t'a la » : Pas de souci / Ne t'inquiète pas.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_9", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Vocabulaire du marché 🛍️", "« Raaogo » (marché en mooré) et ses expressions clés.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_10", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Nondgo", "« Nondgo » signifie l'amour et l'affection en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_11", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression de bienvenue 🌿", "« Ne y waogo » : Soyez le très bienvenu !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_12", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« I ni ce » : L'incontournable remerciement dioula.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_13", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Sagesse locale 📜", "« Bĩng n get bĩng » : L'union fait la force.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_14", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Koom", "« Koom » : L'eau, source vitale et symbole d'accueil.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_15", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression courante 🗣️", "« Fo yiki laafi ? » : Vous êtes-vous réveillé en paix ?", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_16", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Hakili » : L'intelligence, l'esprit et la sagesse.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_17", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression de respect 🙏", "« Mam pʋ'ʋsa yãmb » : Je vous salue respectueusement.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_18", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Sagbo", "« Sagbo » : Le tô traditionnel, symbole de partage.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_19", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression du soir 🌙", "« Wend n kõ-d beog laafi » : Que Dieu nous donne la paix.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_20", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Mogoya » : L'humanité et les valeurs de fraternité.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_21", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Terme de famille 👨‍👩‍👧", "« Baaba » (Père) et « Maama » (Mère) en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_22", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Bãngre", "« Bãngre » : La connaissance et le savoir acquis.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_23", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Salutation cordiale 🤝", "« Yaa laafi bala » : Tout va parfaitement bien.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_24", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Terikɛ » : Mon cher ami / mon compagnon fidèle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_25", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Vocabulaire du voyage 🚗", "« Sore » : La route et le chemin en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_26", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Tẽnga", "« Tẽnga » : La terre natale, le village ou le pays.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_27", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Formule d'encouragement 👏", "« Yɩ waoogo » : Prends courage et sois fort.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_28", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Sababu", "« Sababu » : L'opportunité, la cause bénéfique.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_29", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Politesse en dioula ✨", "« Aw dansɛ » : Bienvenue parmi nous !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_30", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Kũuni", "« Kũuni » : Le cadeau, le présent offert avec le cœur.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_31", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression santé 🩺", "« Yãmb roogo yaa laafi ? » : La famille se porte-t-elle bien ?", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_32", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Mooré", "« Biiga » : L'enfant, la joie et l'avenir du foyer.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_33", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression dioula ☀️", "« Kɛnɛya bɛ wa ? » : Avez-vous la pleine santé ?", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_34", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Roogo", "« Roogo » : La maison, le foyer chaleureux.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_35", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Formule de départ 🚗", "« Wend n tall-f » : Que Dieu te protège sur ton chemin.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_36", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Sabali » : La patience et la retenue bienveillante.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_37", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Vocabulaire travail 💼", "« Tʋʋma » : Le travail et l'activité professionnelle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_38", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Salutation de travail 🔨", "« Ne y tʋʋma » : Bon travail et bon courage !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_39", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Gomde", "« Gomde » : La parole, le verbe et le dialogue.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_40", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression dioula 🌾", "« Baraji » : La bénédiction divine et la récompense.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_41", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Terme de nature 🌳", "« Tiiga » : L'arbre, symbole de vie et d'ombrage.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_42", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Sũ-noogo", "« Sũ-noogo » : La joie profonde du cœur.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_43", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression d'accord 👍", "« Yaa vẽeneg » : C'est limpide et très clair.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_44", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour en Dioula", "« Dɔɔnin dɔɔnin » : Petit à petit, avec persévérance.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_45", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Vocabulaire repas 🍲", "« Rɩɩbo » : La nourriture et le repas en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_46", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Formule de table 🍽️", "« Ne y kooma » : Bon appétit à tous !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_47", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Weefo", "« Weefo » : Le cheval, emblème royal et noble.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_48", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression dioula 🤝", "« An bɛ kelen » : Nous sommes unis et solidaires.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_49", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Terme d'honneur 👑", "« Naaba » : Le chef traditionnel respecté.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_50", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Zoodo", "« Zoodo » : L'amitié sincère et durable.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_51", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression dioula 🌟", "« Ala ka tile hɛrɛ caya » : Que Dieu bénisse la journée.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_52", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Vocabulaire fête 🎉", "« Kibare » : Les bonnes nouvelles et festivités.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_53", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Pãnga", "« Pãnga » : La force, la puissance et le courage.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_54", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Expression d'affection ❤️", "« Mam nonga foom » : Je t'aime en mooré.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_55", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Affection en dioula ❤️", "« N b'i fɛ » : Je t'aime tendrement en dioula.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_56", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Yam", "« Yam » : L'intelligence et le discernement.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_57", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Terme de temps ⏳", "« Wakat » : Le moment, l'époque ou l'heure.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_58", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Salutation d'adieu 👋", "« Wend na kõ-d beogo » : À demain si Dieu le veut.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_59", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "📖 Mot du jour : Sũ-sõngo", "« Sũ-sõngo » : La bonté de cœur et la générosité.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("wod_60", NotificationCategory.WORD_OF_DAY, TimeOfDay.ANY, "Sagesse dioula 📜", "« Hakilima bɛ jɔrɔ ban » : La sagesse dissipe les soucis.", "sam-ai://learning"))

        // =========================================================================
        // 5. ASTUCES & FONCTIONNALITÉS - 50 messages
        // =========================================================================
        list.add(SmartNotificationMessage("tip_1", NotificationCategory.TIPS, TimeOfDay.ANY, "Astuce traduction vocale 🎙️", "Appuyez sur le micro pour dicter vos phrases directement.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_2", NotificationCategory.TIPS, TimeOfDay.ANY, "Traduction d'images 📸", "Scannez un panneau ou un texte photo pour le traduire.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_3", NotificationCategory.TIPS, TimeOfDay.ANY, "Gestion des favoris ⭐", "Ajoutez vos traductions importantes en favoris d'un clic.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_4", NotificationCategory.TIPS, TimeOfDay.ANY, "Mode Hors-ligne 📶", "Votre dictionnaire reste fonctionnel sans connexion internet.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_5", NotificationCategory.TIPS, TimeOfDay.ANY, "Synthèse vocale 🔊", "Écoutez la voix de prononciation pour parfaire votre accent.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_6", NotificationCategory.TIPS, TimeOfDay.ANY, "Recherche instantanée ⚡", "Tapez les premières lettres pour trouver un mot instantanément.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_7", NotificationCategory.TIPS, TimeOfDay.ANY, "Copier-Coller rapide 📋", "Copiez le résultat traduit d'une simple pression d'icône.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_8", NotificationCategory.TIPS, TimeOfDay.ANY, "Historique sécurisé 🔒", "Retrouvez vos précédentes consultations à tout moment.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_9", NotificationCategory.TIPS, TimeOfDay.ANY, "Inversion de langue 🔄", "Inversez la langue source et cible en une fraction de seconde.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_10", NotificationCategory.TIPS, TimeOfDay.ANY, "Clavier adapté ⌨️", "Bénéficiez des caractères spéciaux comme ɛ, ɔ, ʋ, ɩ.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_11", NotificationCategory.TIPS, TimeOfDay.ANY, "Partage facile 📤", "Partagez directement vos traductions sur WhatsApp et SMS.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_12", NotificationCategory.TIPS, TimeOfDay.ANY, "Mode Sombre reposant 🌙", "Activez le thème sombre dans les paramètres pour vos yeux.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_13", NotificationCategory.TIPS, TimeOfDay.ANY, "Quiz d'entraînement 🎯", "Consolidez votre mémoire avec les fiches d'apprentissage.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_14", NotificationCategory.TIPS, TimeOfDay.ANY, "Assistance officielle 💬", "Contactez notre équipe d'assistance directement sur WhatsApp.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_15", NotificationCategory.TIPS, TimeOfDay.ANY, "Gain de temps 🚀", "Enregistrez vos phrases types pour les réutiliser sans retaper.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_16", NotificationCategory.TIPS, TimeOfDay.ANY, "Phonétique claire 🗣️", "Lisez la transcription phonétique pour faciliter l'élocution.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_17", NotificationCategory.TIPS, TimeOfDay.ANY, "Personnalisation ⚙️", "Réglez la fréquence de vos rappels dans les paramètres.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_18", NotificationCategory.TIPS, TimeOfDay.ANY, "Zéro publicité intrusive 🛡️", "Profitez d'une expérience fluide, claire et professionnelle.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_19", NotificationCategory.TIPS, TimeOfDay.ANY, "Multi-dialectes 🌍", "Basculez librement entre le Mooré, le Dioula et le Français.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_20", NotificationCategory.TIPS, TimeOfDay.ANY, "Astuce prononciation 🎧", "Répétez à voix haute après la voix de SAM AI.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_21", NotificationCategory.TIPS, TimeOfDay.ANY, "Recherche par catégorie 🗂️", "Filtrez vos mots par thèmes : santé, commerce, voyage.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_22", NotificationCategory.TIPS, TimeOfDay.ANY, "Sauvegarde locale 💾", "Vos préférences sont conservées en toute confidentialité.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_23", NotificationCategory.TIPS, TimeOfDay.ANY, "Fluidité maximale ⚡", "L'application démarre au quart de tour sur tous vos téléphones.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_24", NotificationCategory.TIPS, TimeOfDay.ANY, "Détection automatique 🤖", "Notre moteur reconnaît automatiquement la langue saisie.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_25", NotificationCategory.TIPS, TimeOfDay.ANY, "Guide d'orthographe ✍️", "Apprenez les règles de graphie officielle burkinabè.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_26", NotificationCategory.TIPS, TimeOfDay.ANY, "Recherche contextuelle 🔍", "Découvrez des exemples réels d'utilisation en phrases.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_27", NotificationCategory.TIPS, TimeOfDay.ANY, "Accès rapide widget 📲", "Gardez SAM AI sur votre écran d'accueil.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_28", NotificationCategory.TIPS, TimeOfDay.ANY, "Vérification des tons 🎵", "Les tons en dioula et mooré apportent le sens exact.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_29", NotificationCategory.TIPS, TimeOfDay.ANY, "Exportation PDF / Texte 📄", "Exportez facilement vos listes de vocabulaire.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_30", NotificationCategory.TIPS, TimeOfDay.ANY, "Traduction bidirectionnelle ⇄", "Passez du français au dioula et inversement.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_31", NotificationCategory.TIPS, TimeOfDay.ANY, "Mode Éco-batterie 🔋", "L'application est optimisée pour économiser votre batterie.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_32", NotificationCategory.TIPS, TimeOfDay.ANY, "Index thématique 📑", "Accédez aux fiches : salutations, nombres, marché.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_33", NotificationCategory.TIPS, TimeOfDay.ANY, "Effacer l'historique 🧹", "Gérez votre historique en toute liberté depuis l'écran dédié.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_34", NotificationCategory.TIPS, TimeOfDay.ANY, "Aide au dialogue 💬", "Préparez vos voyages dans les provinces en amont.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_35", NotificationCategory.TIPS, TimeOfDay.ANY, "Feedback direct ✍️", "Suggérez un mot via notre formulaire officiel.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_36", NotificationCategory.TIPS, TimeOfDay.ANY, "Double appui favoris ⭐", "Marquez un mot important pour le retrouver instantanément.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_37", NotificationCategory.TIPS, TimeOfDay.ANY, "Compatibilité tous écrans 📱", "Interface soignée pour petits et grands téléphones.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_38", NotificationCategory.TIPS, TimeOfDay.ANY, "Lecture lente disponible 🐢", "Ralentissez la voix pour bien entendre chaque syllabe.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_39", NotificationCategory.TIPS, TimeOfDay.ANY, "Dictionnaire vérifié ✅", "Toutes les traductions sont certifiées par nos linguistes.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_40", NotificationCategory.TIPS, TimeOfDay.ANY, "Sécurité absolue 🔒", "Zéro partage de vos données personnelles avec des tiers.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_41", NotificationCategory.TIPS, TimeOfDay.ANY, "Recherche par mot-clé 🔎", "Trouvez les synonymes et antonymes en un instant.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_42", NotificationCategory.TIPS, TimeOfDay.ANY, "Prononciation phonétique 📢", "Maîtrisez les voyelles nasales sans hésitation.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_43", NotificationCategory.TIPS, TimeOfDay.ANY, "Historique filtrable 🔍", "Recherchez facilement dans vos traductions passées.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_44", NotificationCategory.TIPS, TimeOfDay.ANY, "Raccourcis gestuels 👆", "Glissez vers la gauche pour supprimer une entrée.", "sam-ai://history"))
        list.add(SmartNotificationMessage("tip_45", NotificationCategory.TIPS, TimeOfDay.ANY, "Assistance multilingue 🌐", "L'interface est disponible en Français et en Anglais.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_46", NotificationCategory.TIPS, TimeOfDay.ANY, "Mises à jour fluides 🔄", "Bénéficiez automatiquement des nouvelles expressions.", "sam-ai://home"))
        list.add(SmartNotificationMessage("tip_47", NotificationCategory.TIPS, TimeOfDay.ANY, "Clarté des définitions 📖", "Consultez le sens littéral et figuré de chaque mot.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_48", NotificationCategory.TIPS, TimeOfDay.ANY, "Apprentissage progressif 📈", "Passez du niveau débutant à avancé sans pression.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("tip_49", NotificationCategory.TIPS, TimeOfDay.ANY, "Vérification en direct ⚡", "Corrigez vos fautes d'orthographe en temps réel.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("tip_50", NotificationCategory.TIPS, TimeOfDay.ANY, "Partage culturel 🤝", "Faites découvrir l'application à vos amis et collègues.", "sam-ai://home"))

        // =========================================================================
        // 6. FIDÉLITÉ & ENCOURAGEMENTS - 40 messages
        // =========================================================================
        list.add(SmartNotificationMessage("loy_1", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Merci de votre confiance ❤️", "Merci d'utiliser SAM AI pour vos échanges.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_2", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Fidélité récompensée 🏅", "Votre régularité est remarquable. Félicitations !", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_3", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Ensemble pour nos langues 🤝", "Grâce à vous, nos langues nationales restent vivantes.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_4", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Communauté active 🌟", "Vous faites partie de milliers d'utilisateurs passionnés.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_5", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Bravo pour votre parcours 👏", "Votre assiduité linguistique porte déjà ses fruits.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_6", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Un grand merci 🌿", "Chaque retour et avis contribue à faire évoluer l'app.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_7", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Fierté partagée 🇧🇫", "Ensemble, valorisons le patrimoine culturel burkinabè.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_8", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Votre engagement compte ✨", "Votre progression quotidienne est une véritable source d'inspiration.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_9", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Excellente collaboration 💎", "Nous nous efforçons de vous offrir la meilleure précision.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_10", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Soutien mutuel 🤝", "SAM AI évolue chaque jour pour vous satisfaire.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_11", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Merci à vous ❤️", "Une application créée avec passion pour notre peuple.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_12", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Prestigieux utilisateur 🌟", "Continuez d'explorer la richesse de nos dialectes.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_13", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Amour des langues 📚", "Votre passion pour le mooré et le dioula est admirable.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_14", NotificationCategory.LOYALTY, TimeOfDay.ANY, "À vos côtés toujours 🛡️", "La fiabilité et la sécurité de vos données restent notre priorité.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_15", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Une belle aventure 🚀", "Merci d'écrire cette belle histoire linguistique avec nous.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_16", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Reconnaissance sincère 🙏", "Merci d'être un pilier de notre communauté d'apprenants.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_17", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Plaisir partagé 🌸", "Votre assiduité donne du sens à notre travail.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_18", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Culture en fête 🎭", "Chaque traduction renforce la vitalité de nos langues.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_19", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Vous êtes formidable 🌟", "Poursuivez cette quête passionnante du savoir.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_20", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Merci de votre fidélité 🤝", "SAM AI continuera de s'enrichir pour vous.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_21", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Un pas vers l'excellence 🏆", "Votre curiosité linguistique est un modèle pour tous.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_22", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Fidélité au sommet 💎", "Nous sommes honorés de compter parmi vos applications favorites.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_23", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Force et solidarité 🇧🇫", "Ensemble, portons haut les couleurs de nos dialectes.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_24", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Une confiance méritée 🛡️", "Nous garantissons la totale confidentialité de vos recherches.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_25", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Partenaire du quotidien 📱", "Votre compagnon fidèle pour toutes les langues du Faso.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_26", NotificationCategory.LOYALTY, TimeOfDay.ANY, "L'amour du patrimoine 🏛️", "Préserver nos langues, c'est préserver notre âme.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_27", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Félicitations pour votre parcours 🥇", "Vous avez déjà parcouru un chemin remarquable.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_28", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Gratitude infinie ❤️", "Merci pour vos encouragements et vos suggestions.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_29", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Votre avis compte ✍️", "Partagez votre expérience pour nous aider à grandir.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_30", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Fidèle parmi les fidèles 🌟", "Merci pour votre attachement indéfectible.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_31", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Passionné de culture 📚", "Votre persévérance est une véritable fierté.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_32", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Toujours connecté 🌿", "SAM AI reste à votre écoute en continu.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_33", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Fierté nationale 🇧🇫", "Valorisons ensemble les dialectes de notre patrie.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_34", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Un honneur de vous servir 🤝", "L'excellence linguistique est notre engagement.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_35", NotificationCategory.LOYALTY, TimeOfDay.ANY, "La régularité paie 📈", "Chaque jour vous rapproche de la maîtrise totale.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("loy_36", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Soutien inconditionnel 🛡️", "Une technologie pensée pour faciliter votre quotidien.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_37", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Votre voix compte 🎙️", "Merci de faire rayonner SAM AI autour de vous.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_38", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Inspiration partagée ✨", "Votre fidélité inspire nos futures innovations.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_39", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Prenez soin de vous 🌿", "Nous vous souhaitons une merveilleuse journée d'apprentissage.", "sam-ai://home"))
        list.add(SmartNotificationMessage("loy_40", NotificationCategory.LOYALTY, TimeOfDay.ANY, "Merci et bravo ! 👏", "À très bientôt pour de nouvelles découvertes enrichissantes.", "sam-ai://home"))

        // =========================================================================
        // 7. NOUVEAUTÉS & CULTURE - 40 messages
        // =========================================================================
        list.add(SmartNotificationMessage("cul_1", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Culture du Faso 🇧🇫", "Découvrez les symboles du Faso Dan Fani et du Sagbo.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_2", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Proverbe traditionnel 📜", "« Le savoir est un baobab que nul bras ne peut entourer seul ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_3", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Dictionnaire enrichi 📚", "De nouvelles expressions authentiques ont été ajoutées.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("cul_4", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Sagesse ancestrale 🌿", "Les contes et légendes s'expriment au travers des mots.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_5", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Histoire vivante 🏛️", "Chaque mot en mooré raconte une part d'histoire du Moogo.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_6", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Traditions & Valeurs 🤝", "L'hospitalité et le respect : piliers de notre culture.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_7", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Expressions régionales 🗺️", "Comparez les nuances entre dialectes régionaux.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_8", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Musique des mots 🎶", "La musicalité du dioula charme toujours par sa fluidité.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("cul_9", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Événements culturels 🎭", "Partagez la joie des fêtes traditionnelles en langues locales.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_10", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Transmission intergénérationnelle 👨‍👧", "Parlez les langues de nos aïeux avec fierté et justesse.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_11", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Sagesse des anciens 👴", "« Si tu fermes les yeux quand on te conseille, tu marcheras dans le noir ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_12", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Art du conte 📖", "Découvrez les métaphores des contes sous l'arbre à palabres.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_13", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Solidarité africaine 🤝", "« Une seule main ne peut ramasser la farine ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_14", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Les masques sacrés 🎭", "Découvrez le vocabulaire rituel et festif des traditions.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_15", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Éloge de la patience ⏳", "« La patience cuit même les pierres » en sagesse locale.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_16", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'art du tissage 🧵", "Le Faso Dan Fani, fleuron de notre identité textile.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_17", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'honneur et la parole 🛡️", "Un proverbe dit : « La parole donnée est une dette sacrée ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_18", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Hymne à la nature 🌿", "Apprenez les noms des plantes médicinales traditionnelles.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_19", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'hospitalité légendaire 🏠", "Offrir de l'eau à l'étranger est le premier devoir d'accueil.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_20", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Gastronomie locale 🍲", "Découvrez les termes des mets traditionnels : Gonré, Babenda.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_21", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Parenté à plaisanterie 😄", "Une tradition séculaire de paix et de fraternité sociale.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_22", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Le rythme du djembé 🥁", "La musique accompagne chaque étape de la vie communautaire.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_23", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'arbre à palabres 🌳", "Le lieu de médiation, de paix et de concorde démocratique.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_24", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Les saisons du Sahel 🌾", "Vocabulaire de l'hivernage et des récoltes abondantes.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_25", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'art de la forge 🔨", "Le travail du fer et des bronzes de Ouagadougou.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_26", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'amitié sans frontière 🤝", "« L'ami véritable est un frère que l'on choisit ».", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_27", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Architecture de terre 🏛️", "Les habitats traditionnels kassena et mossi.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_28", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Poésie des proverbes 📜", "Chaque adage est un joyau de sagesse intemporelle.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_29", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "La valeur du temps ⏳", "« Le temps perdu ne se rattrape jamais à la course ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_30", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Célébration des récoltes 🌽", "Rendez hommage aux paysans qui nourrissent notre terre.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_31", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Richesse linguistique 🗣️", "Plus de 60 langues font la fierté de notre pays.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_32", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'humilité des grands 🌾", "« L'épi mûr est celui qui courbe la tête ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_33", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Le respect des aînés 👵", "Les cheveux blancs sont la mémoire vivante du village.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_34", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Harmonie sociale 🕊️", "Vivre ensemble dans la concorde et la bienveillance.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_35", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "La transmission du savoir 📖", "« Ce que tu donnes t'appartient pour toujours ».", "sam-ai://learning"))
        list.add(SmartNotificationMessage("cul_36", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'éclat des traditions ✨", "Fiers de notre passé, confiants dans notre avenir.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_37", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "La force de l'espoir 🌅", "Après la nuit la plus sombre vient toujours l'aube.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_38", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Unité dans la diversité 🤝", "Chaque culture apporte une note à la grande symphonie.", "sam-ai://home"))
        list.add(SmartNotificationMessage("cul_39", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "L'élégance de la langue 💎", "Parler juste, c'est honorer la dignité de son prochain.", "sam-ai://translation"))
        list.add(SmartNotificationMessage("cul_40", NotificationCategory.NEWS_CULTURE, TimeOfDay.ANY, "Le trésor des mots 🗝️", "SAM AI préserve notre trésor linguistique.", "sam-ai://home"))

        // =========================================================================
        // 8. ACTUALITÉS DU FASO & MONDE (Catégorie Actualités)
        // =========================================================================
        list.add(SmartNotificationMessage("news_1", NotificationCategory.NEWS, TimeOfDay.ANY, "Actualités du Faso 🇧🇫", "Consultez les derniers articles et faits marquants au Burkina Faso.", "sam-ai://news"))
        list.add(SmartNotificationMessage("news_2", NotificationCategory.NEWS, TimeOfDay.ANY, "Flash Info Faso 📰", "Restez informé des actualités socioculturelles et sportives du pays.", "sam-ai://news"))
        list.add(SmartNotificationMessage("news_3", NotificationCategory.NEWS, TimeOfDay.ANY, "Culture & Société 🌟", "Découvrez les initiatives innovantes des jeunes talents burkinabè.", "sam-ai://news"))
        list.add(SmartNotificationMessage("news_4", NotificationCategory.NEWS, TimeOfDay.ANY, "Sport & Événements ⚽", "Suivez les performances des Étalons et les compétitions nationales.", "sam-ai://news"))
        list.add(SmartNotificationMessage("news_5", NotificationCategory.NEWS, TimeOfDay.ANY, "Tech & Innovation 💡", "Les nouvelles technologies au service de la jeunesse africaine.", "sam-ai://news"))

        // =========================================================================
        // 9. QUIZ & PROGRESSION LINGUISTIQUE
        // =========================================================================
        list.add(SmartNotificationMessage("quiz_1", NotificationCategory.QUIZ, TimeOfDay.ANY, "Défi Quiz du jour 🎯", "Testez vos connaissances en mooré, dioula ou haoussa en 2 minutes !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("quiz_2", NotificationCategory.QUIZ, TimeOfDay.ANY, "Nouvelle série de Quiz 🔥", "Votre série d'apprentissage vous attend, relevez le défi aujourd'hui !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("quiz_3", NotificationCategory.QUIZ, TimeOfDay.ANY, "Quiz Vidéo Burkina 🎬", "Reconnaissez-vous les expressions populaires du Faso ? Testez-vous !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("quiz_4", NotificationCategory.QUIZ, TimeOfDay.ANY, "Entraînement rapide ⏱️", "3 questions pour booster votre mémoire des expressions locales.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("quiz_5", NotificationCategory.QUIZ, TimeOfDay.ANY, "Objectif Maîtrise 🏆", "Chaque bonne réponse vous rapproche du titre d'expert linguistique.", "sam-ai://learning"))

        // =========================================================================
        // 10. RÉCOMPENSES & NIVEAUX
        // =========================================================================
        list.add(SmartNotificationMessage("rew_1", NotificationCategory.REWARDS, TimeOfDay.ANY, "Nouveau Badge Débloqué 🏅", "Félicitations pour vos efforts linguistiques constants !", "sam-ai://learning"))
        list.add(SmartNotificationMessage("rew_2", NotificationCategory.REWARDS, TimeOfDay.ANY, "Points XP en hausse ⚡", "Vous gagnez des points à chaque mot appris et quiz réussi.", "sam-ai://learning"))
        list.add(SmartNotificationMessage("rew_3", NotificationCategory.REWARDS, TimeOfDay.ANY, "Cap de progression franchi 🌟", "Continuez ainsi pour atteindre le niveau supérieur dans votre langue favorite.", "sam-ai://learning"))

        // =========================================================================
        // 11. ANNONCES & MISES À JOUR OFFICIELLES
        // =========================================================================
        list.add(SmartNotificationMessage("ann_1", NotificationCategory.ANNOUNCEMENTS, TimeOfDay.ANY, "Nouveautés SAM AI 🚀", "Découvrez les améliorations de rapidité et d'intelligence artificielle.", "sam-ai://home"))
        list.add(SmartNotificationMessage("ann_2", NotificationCategory.ANNOUNCEMENTS, TimeOfDay.ANY, "Note du Créateur 🇧🇫", "Abdoul Mohamine Sawadogo et l'équipe travaillent pour votre confort.", "sam-ai://about"))
        list.add(SmartNotificationMessage("ann_3", NotificationCategory.ANNOUNCEMENTS, TimeOfDay.ANY, "Mode Hors-ligne disponible 💾", "Profitez de la traduction même sans connexion Internet.", "sam-ai://home"))

        list
    }

    /**
     * Sélectionne intelligemment le message suivant en respectant :
     * 1. L'heure de la journée (Matin, Après-midi, Soir)
     * 2. Les catégories activées par l'utilisateur
     * 3. L'historique des notifications récentes (anti-répétition)
     */
    fun selectNextSmartMessage(
        enabledCategories: Set<String>,
        recentlySentIds: Set<String>,
        currentTimeOfDay: TimeOfDay = getCurrentTimeOfDay()
    ): SmartNotificationMessage {
        // Filtrer les messages éligibles
        val eligibleMessages = ALL_MESSAGES.filter { message ->
            val matchesCategory = enabledCategories.isEmpty() || enabledCategories.contains(message.category.key)
            val matchesTime = message.timeOfDay == TimeOfDay.ANY || message.timeOfDay == currentTimeOfDay
            matchesCategory && matchesTime
        }

        // Éliminer ceux déjà envoyés récemment
        val candidateMessages = eligibleMessages.filterNot { recentlySentIds.contains(it.id) }

        return when {
            candidateMessages.isNotEmpty() -> candidateMessages.random()
            eligibleMessages.isNotEmpty() -> eligibleMessages.random()
            else -> ALL_MESSAGES.random()
        }
    }
}
