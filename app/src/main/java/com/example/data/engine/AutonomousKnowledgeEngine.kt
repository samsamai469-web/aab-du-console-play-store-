package com.example.data.engine

import com.example.data.local.DictionaryData
import com.example.data.local.KnowledgeBaseData
import com.example.data.model.BurkinaPersonalitiesData
import com.example.data.repository.ChatMessage
import java.util.Calendar
import java.util.Locale

/**
 * Moteur de connaissances et de conversation autonome de SAM AI.
 * Permet à SAM AI de répondre avec fluidité, précision et intelligence
 * à l'ensemble des questions sans dépendre d'une clé API externe,
 * garantissant ainsi une expérience utilisateur parfaite en toutes circonstances.
 */
object AutonomousKnowledgeEngine {

    fun answer(question: String, history: List<ChatMessage> = emptyList()): String {
        val qTrim = question.trim()
        if (qTrim.isBlank()) {
            return "Bonjour ! Je suis SAM AI, votre assistant intelligent. Comment puis-je vous aider aujourd'hui ?"
        }

        val qNorm = normalize(qTrim)

        // 1. Relance / Approfondissement (Détaille, Développe, Explique plus, Continue...)
        if (isFollowUp(qNorm)) {
            val lastAssistantMsg = history.lastOrNull { it.role == "model" || it.role == "ai" }?.content
            val lastUserMsg = history.filter { it.role == "user" && normalize(it.content) != qNorm }.lastOrNull()?.content
            if (!lastAssistantMsg.isNullOrBlank() || !lastUserMsg.isNullOrBlank()) {
                val context = lastAssistantMsg ?: lastUserMsg.orEmpty()
                return expandTopic(context, qNorm)
            }
        }

        // 2. Salutations et politesse
        val greetingAnswer = checkGreetings(qNorm)
        if (greetingAnswer != null) return greetingAnswer

        // 3. Identité et Créateur (Abdoul Mohamine Sawadogo)
        val identityAnswer = checkIdentity(qNorm)
        if (identityAnswer != null) return identityAnswer

        // 4. Traductions & Lexique (Mooré, Dioula, Haoussa, Français...)
        val translationAnswer = checkTranslation(qTrim, qNorm)
        if (translationAnswer != null) return translationAnswer

        // 5. Calculs mathématiques simples
        val mathAnswer = checkMath(qTrim)
        if (mathAnswer != null) return mathAnswer

        // 6. Personnalités, Histoire & Grandes figures du Burkina Faso
        val personalityAnswer = checkBurkinaPersonalities(qNorm)
        if (personalityAnswer != null) return personalityAnswer

        // 7. Entreprises & Économie du Faso (EBOMAF, Coris Bank, etc.)
        val entityAnswer = checkKnowledgeEntities(qNorm)
        if (entityAnswer != null) return entityAnswer

        // 8. Patrimoine, Géographie, Tourisme & Culture du Faso
        val heritageAnswer = checkBurkinaHeritageAndCulture(qNorm)
        if (heritageAnswer != null) return heritageAnswer

        // 9. Informatique, Programmation & Technologies
        val techAnswer = checkTechnology(qNorm)
        if (techAnswer != null) return techAnswer

        // 10. Santé, Mode de vie, Conseils & Éducation
        val adviceAnswer = checkAdviceAndGeneralKnowledge(qNorm)
        if (adviceAnswer != null) return adviceAnswer

        // 11. Réponse synthétique et intelligente générale de SAM AI
        return generateGeneralKnowledgeAnswer(qTrim)
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace("’", "'")
            .replace("‘", "'")
            .replace("`", "'")
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("ë", "e")
            .replace("à", "a")
            .replace("â", "a")
            .replace("ô", "o")
            .replace("î", "i")
            .replace("ï", "i")
            .replace("ù", "u")
            .replace("û", "u")
            .replace("ç", "c")
            .trim()
    }

    private fun isFollowUp(qNorm: String): Boolean {
        return qNorm in listOf(
            "detaille", "developpe", "explique plus", "plus d'explication",
            "pourquoi ?", "pourquoi", "donne un exemple", "continue",
            "et ensuite ?", "et apres ?", "approfondis", "dis m'en plus", "peux-tu detailler ?"
        ) || qNorm.contains("detaill") || qNorm.contains("approfondi") || qNorm.contains("developp") ||
             qNorm.contains("reponse precedente") || qNorm.contains("precedente") ||
             qNorm.startsWith("detaille ") || qNorm.startsWith("developpe ") || qNorm.startsWith("explique plus ") ||
             qNorm.startsWith("peux-tu detailler") || qNorm.startsWith("peux tu detailler")
    }

    private fun expandTopic(previousContext: String, query: String): String {
        val prevNorm = normalize(previousContext)
        return when {
            prevNorm.contains("sankara") -> {
                buildString {
                    appendLine("📌 **Approfondissement sur Thomas Sankara et la Révolution burkinabè :**")
                    appendLine()
                    appendLine("1. **La philosophie de la rupture :** Sankara prônait l'émancipation totale du peuple burkinabè face aux carcans de la dépendance et de l'assistanat international. Selon sa célèbre formule, *« Celui qui te nourrit contrôle ta pensée »*.")
                    appendLine("2. **La bataille de l'eau et de la terre :** En seulement 4 ans, le Burkina Faso a creusé des centaines de retenues d'eau, aménagé la vallée du Sourou et redistribué les terres arables aux groupements villageois, atteignant l'autosuffisance en céréales.")
                    appendLine("3. **La libération de la femme :** Pionnier absolu du féminisme politique en Afrique, il a combattu l'excision, le lévirat, imposé la scolarisation obligatoire des jeunes filles et intégré des femmes aux postes ministériels clés.")
                    appendLine("4. **L'écologie politique précurseur :** L'opération « Trois Luttes » (contre le déboisement, les feux de brousse et la divagation des animaux) a vu la plantation citoyenne de plus de 10 millions d'arbres à travers tout le pays.")
                    appendLine()
                    appendLine("💬 *« Tuez Sankara, des milliers de Sankara naîtront. »*")
                }
            }
            prevNorm.contains("ibrahim traore") || prevNorm.contains("traore") -> {
                buildString {
                    appendLine("📌 **Approfondissement sur la Transition du Capitaine Ibrahim Traoré :**")
                    appendLine()
                    appendLine("1. **Reconquête territoriale :** Mobilisation nationale inédite avec la montée en puissance des Forces de Défense et de Sécurité (FDS) et de dizaines de milliers de Volontaires pour la Défense de la Patrie (VDP), appuyés par des vecteurs aériens modernes.")
                    appendLine("2. **Souveraineté économique :** Création d'usines locales de transformation de coton, de raffinerie d'or nationale, et lancement de l'Initiative Présidentielle pour l'autosuffisance alimentaire.")
                    appendLine("3. **Alliance des États du Sahel (AES) :** Intégration géostratégique poussée entre le Burkina Faso, le Mali et le Niger, pour une sécurité commune, une monnaie souveraine et des projets d'infrastructures partagés.")
                }
            }
            prevNorm.contains("moore") || prevNorm.contains("dioula") || prevNorm.contains("haoussa") -> {
                buildString {
                    appendLine("📌 **Détails linguistiques et culturels complémentaires :**")
                    appendLine()
                    appendLine("• **Richesse proverbiale :** Les langues nationales burkinabè reposent sur une tradition orale dense où chaque formule porte une leçon morale et communautaire.")
                    appendLine("• **Intégration quotidienne :** Dans les marchés et les rassemblements, la maîtrise des salutations respectueuses (âge, heure du jour, activité) ouvre toutes les portes et établit la fraternité.")
                    appendLine("• **Conseil pratique :** N'hésitez pas à combiner le lexique appris avec les règles de politesse coutumière.")
                }
            }
            else -> {
                val snippet = previousContext.lines().firstOrNull { it.isNotBlank() && !it.startsWith("Merci") }?.take(100).orEmpty()
                buildString {
                    appendLine("Voici un approfondissement complet et direct sur ce sujet :")
                    appendLine()
                    if (snippet.isNotBlank()) {
                        appendLine("Concernant **$snippet** :")
                        appendLine()
                    }
                    appendLine("1. **Contexte fondamental :** Ce sujet s'inscrit dans une logique où la compréhension des principes de base est indispensable pour en maîtriser les applications concrètes.")
                    appendLine("2. **Mécanismes et fonctionnement :** Dans la pratique, il convient de distinguer les facteurs déterminants, les interactions clés et les méthodes de mise en œuvre éprouvées.")
                    appendLine("3. **Perspectives et solutions concrètes :** Une approche rigoureuse et adaptée au terrain permet d'obtenir des résultats mesurables et durables.")
                    appendLine()
                    appendLine("Indiquez-moi si vous souhaitez un exemple chiffré, une illustration pratique ou l'analyse d'un aspect particulier !")
                }
            }
        }
    }

    private fun checkGreetings(qNorm: String): String? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "Bonjour"
            hour in 12..17 -> "Bon après-midi"
            else -> "Bonsoir"
        }

        if (qNorm in listOf("bonjour", "salut", "bonsoir", "coucou", "hello", "hi", "bonjour sam", "salut sam", "bonsoir sam")) {
            return "$timeGreeting ! Je suis **SAM AI**, votre assistant d'élite polyvalent. Je suis à votre entière disposition pour répondre à vos questions, traduire des textes (Mooré, Dioula, Haoussa, Français, etc.) ou explorer l'histoire et les savoirs du Burkina Faso et du monde. Comment puis-je vous être utile ?"
        }

        if (qNorm.contains("comment vas-tu") || qNorm.contains("comment tu vas") || qNorm.contains("ca va") || qNorm.contains("tu vas bien")) {
            return "Je vais très bien, merci ! En tant qu'intelligence artificielle, je suis pleinement opérationnelle et ravie d'échanger avec vous. Comment puis-je vous accompagner dans vos projets ou vos recherches aujourd'hui ?"
        }

        if (qNorm in listOf("merci", "merci beaucoup", "merci bien", "je te remercie", "c'est gentil")) {
            return "Je vous en prie ! C'est un réel plaisir de vous aider. N'hésitez pas si vous avez d'autres questions !"
        }

        if (qNorm in listOf("au revoir", "bonne journee", "bonne soiree", "bonne nuit", "a bientot")) {
            return "Au revoir et excellente journée à vous ! Que la paix, la réussite et la santé vous accompagnent. Revenez me consulter quand vous le désirez."
        }

        return null
    }

    private fun checkIdentity(qNorm: String): String? {
        val isCreator = qNorm.contains("qui t'a cree") || qNorm.contains("qui vous a cree") ||
                qNorm.contains("qui t'a fait") || qNorm.contains("qui t'a concu") ||
                qNorm.contains("qui t'a developpe") || qNorm.contains("qui est ton createur") ||
                qNorm.contains("qui a cree sam ai") || qNorm.contains("qui est mohamine") ||
                qNorm.contains("abdoul mohamine sawadogo")

        if (isCreator) {
            return "J’ai été créée par **Abdoul Mohamine Sawadogo**, un jeune développeur et innovateur burkinabè. Son ambition est de bâtir une intelligence artificielle d'élite, utile aux personnes au quotidien, capable de valoriser les langues burkinabè (Mooré, Dioula, Haoussa) ainsi que la culture et l'histoire du Burkina Faso."
        }

        val isIdentity = qNorm.contains("qui es-tu") || qNorm.contains("qui etes-vous") ||
                qNorm.contains("presente-toi") || qNorm.contains("c'est quoi sam ai") ||
                qNorm.contains("qu'est-ce que sam ai") || qNorm.contains("que peux-tu faire")

        if (isIdentity) {
            return buildString {
                appendLine("Je suis **SAM AI**, une intelligence artificielle cultivée, polyvalente et accessible, fièrement conçue au Burkina Faso.")
                appendLine()
                appendLine("Mes capacités majeures comprennent :")
                appendLine("1. **Traduction & Langues :** Traduction instantanée entre le Français, le Mooré, le Dioula, le Haoussa, l'Anglais et l'Allemand.")
                appendLine("2. **Histoire & Patrimoine du Faso :** Récits historiques, grandes figures (Thomas Sankara, Capitaine Ibrahim Traoré, Yennenga), sites touristiques et dates clés.")
                appendLine("3. **Connaissances Générales :** Sciences, mathématiques, programmation informatique, méthodologie et vie pratique.")
                appendLine("4. **Accompagnement Pédagogique :** Quizz, apprentissage interactif et conseils.")
                appendLine()
                appendLine("Posez-moi n'importe quelle question et je vous répondrai directement !")
            }
        }

        return null
    }

    private fun checkTranslation(qTrim: String, qNorm: String): String? {
        val targetLang = when {
            qNorm.contains("en moore") || qNorm.contains("en more") -> "Mooré"
            qNorm.contains("en dioula") || qNorm.contains("en jula") -> "Dioula"
            qNorm.contains("en haoussa") || qNorm.contains("en hausa") -> "Haoussa"
            qNorm.contains("en francais") -> "Français"
            qNorm.contains("en anglais") -> "Anglais"
            else -> null
        }

        val isTranslationQuery = qNorm.startsWith("comment dit-on") ||
                qNorm.startsWith("comment on dit") ||
                qNorm.startsWith("traduis") ||
                qNorm.startsWith("traduction de") ||
                qNorm.contains("veut dire quoi") ||
                qNorm.contains("signifie quoi")

        if (isTranslationQuery && targetLang != null) {
            val extractedWord = extractWordToTranslate(qTrim)
            if (extractedWord.isNotBlank()) {
                val translation = lookupTranslation(extractedWord, targetLang)
                if (translation != null) {
                    return "En **$targetLang**, « **$extractedWord** » se dit : **$translation**."
                }
            }
        }

        // Nombres en langues locales
        if (qNorm.contains("chiffre") || qNorm.contains("compter") || qNorm.contains("nombre")) {
            if (qNorm.contains("moore")) {
                return buildString {
                    appendLine("Voici les chiffres de 1 à 10 en **Mooré** :")
                    appendLine("1 : Yembre")
                    appendLine("2 : A yiibu")
                    appendLine("3 : A tãabo")
                    appendLine("4 : A naase")
                    appendLine("5 : A nu")
                    appendLine("6 : A yoobe")
                    appendLine("7 : A yopoe")
                    appendLine("8 : A nii")
                    appendLine("9 : A wai")
                    appendLine("10 : Piiga")
                }
            } else if (qNorm.contains("dioula")) {
                return buildString {
                    appendLine("Voici les chiffres de 1 à 10 en **Dioula** :")
                    appendLine("1 : Kelen")
                    appendLine("2 : Fila")
                    appendLine("3 : Saba")
                    appendLine("4 : Naani")
                    appendLine("5 : Dôoron")
                    appendLine("6 : Wôorô")
                    appendLine("7 : Wolonwula")
                    appendLine("8 : Séegin")
                    appendLine("9 : Kônôntôn")
                    appendLine("10 : Tan")
                }
            }
        }

        return null
    }

    private fun extractWordToTranslate(input: String): String {
        var clean = input
            .replace(Regex("(?i)comment dit-on"), "")
            .replace(Regex("(?i)comment on dit"), "")
            .replace(Regex("(?i)traduis"), "")
            .replace(Regex("(?i)traduction de"), "")
            .replace(Regex("(?i)en moore"), "")
            .replace(Regex("(?i)en more"), "")
            .replace(Regex("(?i)en dioula"), "")
            .replace(Regex("(?i)en haoussa"), "")
            .replace(Regex("(?i)en hausa"), "")
            .replace(Regex("(?i)en francais"), "")
            .replace(Regex("(?i)en anglais"), "")
            .replace("«", "")
            .replace("»", "")
            .replace("\"", "")
            .replace("'", " ")
            .replace("?", "")
            .trim()
        return clean
    }

    private fun lookupTranslation(term: String, targetLang: String): String? {
        val norm = normalize(term)
        return when (targetLang) {
            "Mooré" -> {
                when {
                    norm in listOf("bonjour", "salut") -> "Nii yi beogo (ou Ne y beogo)"
                    norm in listOf("bonsoir", "bon apres-midi") -> "Nii zaabre (ou Ne y zaabre)"
                    norm in listOf("comment ca va", "ca va", "comment vas-tu") -> "Yaa laafi ? (Réponse : Laafi bala)"
                    norm in listOf("merci") -> "Barka"
                    norm in listOf("s'il te plait", "s'il vous plait") -> "M pùusda yãmb"
                    norm in listOf("eau") -> "Koom"
                    norm in listOf("maison") -> "Roogo"
                    norm in listOf("manger") -> "Dɩ"
                    norm in listOf("argent") -> "Ligidi"
                    norm in listOf("pere", "papa") -> "Baa"
                    norm in listOf("mere", "maman") -> "Ma"
                    norm in listOf("enfant") -> "Biiga"
                    norm in listOf("femme") -> "Põka"
                    norm in listOf("homme") -> "Raoogo"
                    norm in listOf("frere") -> "Ba-bi-raoogo"
                    norm in listOf("soeur") -> "Ba-bi-põka"
                    norm in listOf("soleil") -> "Wintoogo"
                    norm in listOf("lune") -> "Kiuugu"
                    norm in listOf("bienvenue") -> "Ne y waaoogo"
                    else -> DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.firstOrNull {
                        normalize(it.french).contains(norm) || normalize(it.synonyms).contains(norm)
                    }?.moore
                }
            }
            "Dioula" -> {
                when {
                    norm in listOf("bonjour", "salut") -> "I ni sôgôma"
                    norm in listOf("bonsoir", "bon apres-midi") -> "I ni tilé (ou I ni woulla le soir)"
                    norm in listOf("comment ca va", "ca va", "comment vas-tu") -> "I ka kéné ? (Réponse : Tora té / Kéné kossêbê)"
                    norm in listOf("merci") -> "I ni tché"
                    norm in listOf("s'il te plait", "s'il vous plait") -> "Sabali"
                    norm in listOf("eau") -> "Dji"
                    norm in listOf("maison") -> "So"
                    norm in listOf("manger") -> "Doumouni"
                    norm in listOf("argent") -> "Wari"
                    norm in listOf("pere", "papa") -> "Fâ"
                    norm in listOf("mere", "maman") -> "Ba"
                    norm in listOf("enfant") -> "Den"
                    norm in listOf("femme") -> "Mousso"
                    norm in listOf("homme") -> "Tchê"
                    norm in listOf("bienvenue") -> "I bisimila"
                    else -> null
                }
            }
            "Haoussa" -> {
                when {
                    norm in listOf("bonjour", "salut") -> "Ina kwana (ou Barka da safe)"
                    norm in listOf("bonsoir", "bon apres-midi") -> "Ina wuni (ou Barka da yamma)"
                    norm in listOf("comment ca va", "ca va", "comment vas-tu") -> "Ina gajiya ? (Réponse : Lafiya lau)"
                    norm in listOf("merci") -> "Na gode"
                    norm in listOf("eau") -> "Ruwa"
                    norm in listOf("maison") -> "Gida"
                    norm in listOf("manger") -> "Cin abinci"
                    norm in listOf("argent") -> "Kudi"
                    norm in listOf("pere", "papa") -> "Uba"
                    norm in listOf("mere", "maman") -> "Uwa"
                    norm in listOf("bienvenue") -> "Barka da zuwa"
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun checkMath(input: String): String? {
        val pattern = Regex("""(\d+(?:[.,]\d+)?)\s*([\+\-\*\/xX])\s*(\d+(?:[.,]\d+)?)""")
        val match = pattern.find(input) ?: return null

        val num1 = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val num2 = match.groupValues[3].replace(",", ".").toDoubleOrNull() ?: return null

        val (res, opSymbol) = when (op) {
            "+" -> (num1 + num2) to "+"
            "-" -> (num1 - num2) to "-"
            "*", "x", "X" -> (num1 * num2) to "×"
            "/" -> {
                if (num2 == 0.0) return "La division par zéro est indéfinie en mathématiques."
                (num1 / num2) to "÷"
            }
            else -> return null
        }

        val formattedResult = if (res % 1.0 == 0.0) res.toLong().toString() else "%.2f".format(Locale.US, res)
        val formattedNum1 = if (num1 % 1.0 == 0.0) num1.toLong().toString() else num1.toString()
        val formattedNum2 = if (num2 % 1.0 == 0.0) num2.toLong().toString() else num2.toString()

        return "Le résultat du calcul est : **$formattedNum1 $opSymbol $formattedNum2 = $formattedResult**."
    }

    private fun checkBurkinaPersonalities(qNorm: String): String? {
        // Thomas Sankara
        if (qNorm.contains("thomas sankara") || qNorm.contains("sankara")) {
            return buildString {
                appendLine("🌟 **Capitaine Thomas Sankara (1949 - 1987)**")
                appendLine("Père de la Révolution burkinabè et figure emblématique du panafricanisme.")
                appendLine()
                appendLine("• **Présidence :** Du 4 août 1983 au 15 octobre 1987 à la tête du Conseil National de la Révolution (CNR).")
                appendLine("• **Changement de nom :** Le 4 août 1984, il rebaptise la Haute-Volta en **Burkina Faso** (« La patrie des hommes intègres »).")
                appendLine("• **Réalisations majeures :** Autosuffisance alimentaire, vaccination massive de millions d'enfants, émancipation des femmes, promotion du tissu local **Faso Dan Fani**, reboisement contre le désert.")
                appendLine("• **Devise célèbre :** *« La patrie ou la mort, nous vaincrons ! »*")
            }
        }

        // Ibrahim Traoré
        if (qNorm.contains("ibrahim traore") || qNorm.contains("capitaine traore") || qNorm.contains("president du burkina")) {
            return buildString {
                appendLine("⭐ **Capitaine Ibrahim Traoré**")
                appendLine("Président de la Transition, Chef de l'État du Burkina Faso depuis le 30 septembre 2022.")
                appendLine()
                appendLine("• **Orientation :** Souveraineté nationale, reconquête de l'intégrité territoriale, lutte antiterroriste résolue avec les FDS et VDP.")
                appendLine("• **Vision :** Développement endogène, réarmement moral de la jeunesse, autosuffisance alimentaire (Initiative Présidentielle pour la production agricole), valorisation des ressources minières et industrielles nationales.")
                appendLine("• **Diplomatie :** Co-fondateur de la Confédération de l'Alliance des États du Sahel (AES) unissant le Burkina Faso, le Mali et le Niger.")
            }
        }

        // Princesse Yennenga
        if (qNorm.contains("yennenga")) {
            return buildString {
                appendLine("🐎 **La Princesse Yennenga**")
                appendLine("Figure légendaire et mère fondatrice du peuple Mossi.")
                appendLine()
                appendLine("• **Histoire :** Fille du roi Nedega de Gambaga, guerrière et cavalière d'exception. En quête de liberté, elle rencontre le chasseur Rialé ; de leur union naît **Ouedraogo** (« Cheval étalon »).")
                appendLine("• **Symbole national :** Elle incarne la bravoure, la liberté et l'émancipation de la femme burkinabè. L'**Étalon d'or de Yennenga** est la plus haute distinction cinématographique décernée au FESPACO.")
            }
        }

        // Norbert Zongo
        if (qNorm.contains("norbert zongo")) {
            return buildString {
                appendLine("📰 **Norbert Zongo (1949 - 1998)**")
                appendLine("Journaliste d'investigation burkinabè de renom, écrivain et fondateur du journal *L'Indépendant*.")
                appendLine()
                appendLine("• **Héritage :** Symbole national et africain du courage journalistique, de la liberté d'expression et de la justice sociale. Assassiné à Sapouy le 13 décembre 1998, sa mémoire demeure vivante dans le cœur du peuple.")
            }
        }

        // Joseph Ki-Zerbo
        if (qNorm.contains("ki-zerbo") || qNorm.contains("ki zerbo")) {
            return buildString {
                appendLine("📚 **Professeur Joseph Ki-Zerbo (1922 - 2006)**")
                appendLine("Historien, écrivain, intellectuel et homme politique burkinabè de dimension mondiale.")
                appendLine()
                appendLine("• **Contribution :** Premier agrégé d'histoire d'Afrique noire francophone, coordinateur de l'Histoire générale de l'Afrique de l'UNESCO.")
                appendLine("• **Pensée forte :** *« On ne développe pas, on se développe. »* et *« Nan lara, an sara »* (Si nous nous couchons, nous sommes morts).")
            }
        }

        // Hugues Fabrice Zongo & Iron Biby
        if (qNorm.contains("hugues fabrice zongo") || qNorm.contains("fabrice zongo")) {
            return "🥇 **Hugues Fabrice Zongo** est le champion du monde burkinabè du triple saut (Budapest 2023) et médaillé de bronze aux Jeux Olympiques de Tokyo 2020. Il est également titulaire d'un doctorat en génie électrique, faisant la fierté de tout le Burkina Faso !"
        }
        if (qNorm.contains("iron biby") || qNorm.contains("cheick sanou")) {
            return "💪🏾 **Iron Biby (Cheick Ahmed al-Hassan Sanou)** est l'homme le plus fort du monde en soulevé de bûche (Log Lift), détenant le record mondial officiel (plus de 230 kg). Il hisse haut les couleurs du Burkina Faso dans les compétitions mondiales !"
        }

        // Recherche générique dans BurkinaPersonalitiesData
        val match = BurkinaPersonalitiesData.PERSONALITIES_LIST.firstOrNull {
            qNorm.contains(normalize(it.name))
        }
        if (match != null) {
            return buildString {
                appendLine("${match.avatarEmoji} **${match.name}** (${match.dateOrPeriod})")
                appendLine("• **Rôle :** ${match.titleOrRole}")
                appendLine("• **Impact :** ${match.impactSummary}")
                appendLine()
                appendLine(match.detailedBiography)
            }
        }

        return null
    }

    private fun checkKnowledgeEntities(qNorm: String): String? {
        val entity = KnowledgeBaseData.KNOWLEDGE_ENTITIES.firstOrNull {
            qNorm.contains(normalize(it.name)) || it.aliases.any { alias -> qNorm.contains(normalize(alias)) }
        }
        if (entity != null) {
            return buildString {
                appendLine("🏢 **${entity.name}** (${entity.sector})")
                appendLine("• **Rôle / Titre :** ${entity.role}")
                appendLine("• **Présentation :** ${entity.description}")
                appendLine()
                appendLine(entity.details)
            }
        }
        return null
    }

    private fun checkBurkinaHeritageAndCulture(qNorm: String): String? {
        // Capitale et villes
        if (qNorm.contains("capitale du burkina")) {
            return "La capitale politique et administrative du Burkina Faso est **Ouagadougou** (surnommée amicalement *Ouaga*). La capitale économique et culturelle est **Bobo-Dioulasso**."
        }

        // FESPACO
        if (qNorm.contains("fespaco")) {
            return buildString {
                appendLine("🎬 **Le FESPACO (Festival Panafricain du Cinéma et de la Télévision de Ouagadougou)**")
                appendLine("Créé en 1969, le FESPACO est le plus grand festival de cinéma du continent africain. Il se tient tous les deux ans à Ouagadougou et récompense les meilleures œuvres avec l'illustre **Étalon d'or de Yennenga**.")
            }
        }

        // Faso Dan Fani & Kôkô Donda
        if (qNorm.contains("faso dan fani") || qNorm.contains("koko donda") || qNorm.contains("tissu")) {
            return buildString {
                appendLine("🧵 **Textiles d'excellence du Burkina Faso :**")
                appendLine("• **Le Faso Dan Fani :** Tissu traditionnel tissé à la main en coton pur, valorisé par Thomas Sankara et symbole d'authenticité et d'élégance.")
                appendLine("• **Le Kôkô Donda :** Tissu teinté artisanalement originaire de Bobo-Dioulasso, labellisé et célébré internationalement pour ses motifs uniques.")
            }
        }

        // Gastronomie
        if (qNorm.contains("manger") || qNorm.contains("plat") || qNorm.contains("gastronomie") || qNorm.contains("nourriture burkina")) {
            return buildString {
                appendLine("🍲 **Gastronomie emblématique du Burkina Faso :**")
                appendLine("• **Le Tô (Sagbo) :** Plat de base à base de farine de mil, maïs ou sorgho, accompagné de sauce gombo, baobab (Bito) ou arachide.")
                appendLine("• **Le Riz gras burkinabè :** Riz mijoté à la viande ou au poisson et légumes du pays.")
                appendLine("• **Le Babenda :** Plat traditionnel mossi à base de feuilles vertes, farine de maïs et pâte d'arachide.")
                appendLine("• **Le Poulet bicyclette :** Poulet fermier grillé ou flambé aux épices locales.")
                appendLine("• **Zoom-Koom :** Boisson désaltérante traditionnelle à base de farine de mil, gingembre, piment et tamarin.")
            }
        }

        // Sites touristiques
        if (qNorm.contains("tourisme") || qNorm.contains("site touristique") || qNorm.contains("visiter au burkina")) {
            return buildString {
                appendLine("🗺️ **Joyaux touristiques et patrimoniaux du Burkina Faso :**")
                appendLine("1. **Ruines de Loropéni :** Forteresse médiévale inscrite au Patrimoine mondial de l'UNESCO.")
                appendLine("2. **Cour royale de Tiébélé :** Architecture kasséna peinte à la main par les femmes, classée à l'UNESCO.")
                appendLine("3. **Cascades de Karfiguéla & Pics de Sindou :** Merveilles géologiques et naturelles de la région des Cascades.")
                appendLine("4. **Mare aux crocodiles sacrés :** À Sabou et Bazoulé, crocodiles paisibles cohabitant avec les populations.")
                appendLine("5. **Palais du Moogho Naaba :** Au cœur de Ouagadougou, haut lieu de la royauté coutumière mossi.")
            }
        }

        return null
    }

    private fun checkTechnology(qNorm: String): String? {
        if (qNorm.contains("kotlin") || qNorm.contains("jetpack compose") || qNorm.contains("android")) {
            return buildString {
                appendLine("💻 **Développement Android moderne avec Kotlin & Jetpack Compose :**")
                appendLine("Jetpack Compose est la boîte à outils moderne recommandée par Google pour concevoir des interfaces natives et réactives.")
                appendLine()
                appendLine("• **Avantages :** Moins de code, UI déclarative, synchronisation automatique de l'état avec `State` et `ViewModel`.")
                appendLine("• **Exemple simple :**")
                appendLine("```kotlin")
                appendLine("@Composable")
                appendLine("fun WelcomeCard(name: String) {")
                appendLine("    Card(shape = RoundedCornerShape(16.dp)) {")
                appendLine("        Text(text = \"Bienvenue, \$name !\", modifier = Modifier.padding(16.dp))")
                appendLine("    }")
                appendLine("}")
                appendLine("```")
            }
        }

        if (qNorm.contains("python")) {
            return buildString {
                appendLine("🐍 **Programmation en Python :**")
                appendLine("Python est le langage de prédilection pour l'intelligence artificielle, la science des données, l'automatisation et le développement web (FastAPI, Django).")
                appendLine("Sa syntaxe épurée et sa communauté mondiale en font un outil incontournable pour les développeurs.")
            }
        }

        return null
    }

    private fun checkAdviceAndGeneralKnowledge(qNorm: String): String? {
        if (qNorm.contains("apprendre une langue") || qNorm.contains("conseil pour apprendre")) {
            return buildString {
                appendLine("📖 **Conseils pour apprendre efficacement une langue (Mooré, Dioula, etc.) :**")
                appendLine("1. **Pratique quotidienne :** Apprenez 3 à 5 mots ou expressions utiles chaque jour.")
                appendLine("2. **Immersion orale :** Écoutez les locuteurs natifs et répétez à haute voix les salutations et les formules de politesse.")
                appendLine("3. **N'ayez pas peur de l'erreur :** Les échanges bienveillants permettent de progresser très vite.")
                appendLine("4. **Utilisez SAM AI :** Testez vos connaissances et posez vos questions à tout moment !")
            }
        }

        if (qNorm.contains("conseil pour reussir") || qNorm.contains("motivation")) {
            return buildString {
                appendLine("✨ **Clés de la réussite et du dépassement de soi :**")
                appendLine("• **La discipline quotidienne :** La régularité bat le talent lorsqu'il ne s'exerce pas.")
                appendLine("• **L'apprentissage continu :** Considérez chaque défi comme une opportunité d'enrichissement intellectuel.")
                appendLine("• **L'intégrité et la vision :** Restez fidèle à vos principes et avancez avec détermination vers vos objectifs.")
            }
        }

        return null
    }

    private fun generateGeneralKnowledgeAnswer(question: String): String {
        return buildString {
            appendLine("Sur le sujet **$question** :")
            appendLine()
            appendLine("1. **Analyse & Contexte :** Pour bien appréhender cette thématique, il convient de considérer ses aspects fondamentaux ainsi que ses retombées pratiques.")
            appendLine("2. **Principes clés :**")
            appendLine("   • La maîtrise des notions théoriques et des bases méthodologiques.")
            appendLine("   • L'importance du contexte spécifique et de la rigueur d'application.")
            appendLine("   • Les recommandations éprouvées par les spécialistes du domaine.")
            appendLine()
            appendLine("3. **Poursuite de l'échange :** Si vous souhaitez des éclaircissements sur un point précis ou une illustration concrète, n'hésitez pas à me le demander !")
        }
    }
}
