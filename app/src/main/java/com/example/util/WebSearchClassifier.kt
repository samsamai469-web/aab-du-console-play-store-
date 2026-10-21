package com.example.util

import com.example.data.repository.ChatMessage
import java.util.Locale

enum class SearchNeedLevel {
    NONE,           // Simple query, translation, code, math, evergreen definition, creative writing
    FACT_CHECK,     // Specific entities, creation/origin of companies/products, verified facts (e.g. "Qui a créé Orange Money ?")
    TEMPORAL_LIVE   // Live news, recent sports matches, live weather, fluctuating prices, current events
}

data class SearchDecision(
    val level: SearchNeedLevel,
    val isSearchNeeded: Boolean,
    val isStrictTemporal: Boolean, // If true, live verification is strictly required
    val isNewsTopic: Boolean,      // Topic "news" for Tavily
    val cleanSearchQuery: String,  // Query cleaned of conversational prefixes
    val reason: String
)

object WebSearchClassifier {

    fun classify(
        userQuestion: String,
        recentHistory: List<ChatMessage> = emptyList()
    ): SearchDecision {
        val raw = userQuestion.trim()
        if (raw.isBlank()) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = "",
                reason = "Question vide"
            )
        }

        val norm = normalize(raw)

        // 1. Détection des demandes explicites de recherche sur le Web
        val explicitSearchPhrases = listOf(
            "cherche sur le web", "cherche sur internet", "recherche sur internet",
            "recherche sur le web", "fais une recherche", "trouve des sources",
            "donne-moi des sources", "donne moi des sources", "trouve des liens",
            "cherche des infos sur", "verifie sur internet", "verifie sur le web",
            "regarde sur internet", "cherche en ligne", "recherche en direct"
        )
        if (explicitSearchPhrases.any { norm.contains(it) }) {
            val cleanQuery = extractCleanSearchQuery(raw, explicitSearchPhrases)
            val isNews = norm.contains("actu") || norm.contains("nouvelle") || norm.contains("recent") || norm.contains("aujourd'hui")
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = false,
                isNewsTopic = isNews,
                cleanSearchQuery = cleanQuery,
                reason = "Demande explicite de recherche web par l'utilisateur"
            )
        }

        // 2. Détection PRIORITAIRE des cas NE NÉCESSITANT PAS Internet (NONE)
        // A. Salutations et civilités
        if (isGreetingOrSmallTalk(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Salutation ou civilité conversationnelle"
            )
        }

        // B. Demandes de traduction explicites (ex: « Traduis bonjour en mooré. »)
        if (isTranslationRequest(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Demande de traduction linguistique directe"
            )
        }

        // C. Programmation, algorithmes et code
        if (isCodeOrProgramming(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Programmation informatique / mathématiques / code"
            )
        }

        // D. Mathématiques et calculs
        if (isMathOrCalculation(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Calcul mathématique ou logique pure"
            )
        }

        // E. Définition intemporelle / concepts fondamentaux (ex: « Qu'est-ce qu'un ordinateur ? »)
        if (isEvergreenConceptOrDefinition(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Définition intemporelle ou concept encyclopédique établi"
            )
        }

        // F. Rédaction créative / poésie / exercices
        if (isCreativeWriting(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.NONE,
                isSearchNeeded = false,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = raw,
                reason = "Tâche de rédaction créative ou littéraire"
            )
        }

        // 3. Détection des cas TEMPORELS EN DIRECT (TEMPORAL_LIVE, isStrictTemporal = true)
        // A. Sports, résultats de matchs, scores récents (ex: « Qui a remporté le match d'hier ? »)
        if (isSportsOrMatch(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = true,
                isNewsTopic = true,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Résultat sportif, score de match ou classement d'actualité"
            )
        }

        // B. Actualités récentes, dernières nouvelles (ex: « Quelles sont les dernières nouvelles du Burkina Faso ? »)
        if (isRecentNewsOrCurrentEvents(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = true,
                isNewsTopic = true,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Actualité récente ou événement en cours"
            )
        }

        // C. Météo en direct
        if (isWeather(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = true,
                isNewsTopic = false,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Météo et prévisions météorologiques en direct"
            )
        }

        // D. Cours, taux de change, prix fluctuants (ex: « Quel est le prix actuel de cet appareil ? »)
        if (isLiveFinancialOrPrice(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = true,
                isNewsTopic = false,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Prix du marché, cours financier ou information fluctuante"
            )
        }

        // 4. Détection des VÉRIFICATIONS FACTUELLES PRÉCISES (FACT_CHECK)
        // Ex: « Qui a créé Orange Money ? », « Qui a fondé... », « Date de création de... »
        if (isFactCheckOrEntityCreation(norm)) {
            return SearchDecision(
                level = SearchNeedLevel.FACT_CHECK,
                isSearchNeeded = true,
                isStrictTemporal = false,
                isNewsTopic = false,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Vérification factuelle précise (créateur, origine, entité spécifique)"
            )
        }

        // 5. Analyse contextuelle des questions de suivi (Follow-ups)
        if (isFollowUpToSearch(norm, raw, recentHistory)) {
            return SearchDecision(
                level = SearchNeedLevel.TEMPORAL_LIVE,
                isSearchNeeded = true,
                isStrictTemporal = false,
                isNewsTopic = true,
                cleanSearchQuery = cleanSearchQueryForEngine(raw),
                reason = "Question de suivi conversationnel liée à une recherche précédente"
            )
        }

        // 6. Si aucune règle temporelle ou factuelle stricte n'a été déclenchée :
        // Par défaut, SAM AI répond directement sans forcer la recherche web
        return SearchDecision(
            level = SearchNeedLevel.NONE,
            isSearchNeeded = false,
            isStrictTemporal = false,
            isNewsTopic = false,
            cleanSearchQuery = raw,
            reason = "Question de connaissance générale traitable directement par le modèle"
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
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
    }

    private fun isGreetingOrSmallTalk(norm: String): Boolean {
        val trimmed = norm.trim().trimEnd('?', '!', '.', ',')
        val greetings = setOf(
            "bonjour", "salut", "bonsoir", "coucou", "hello", "hi", "hey",
            "merci", "merci beaucoup", "de rien", "ca va", "comment ca va",
            "comment vas tu", "comment tu vas", "qui es tu", "qui es-tu",
            "tu es qui", "bonne journee", "bonne soiree", "bonne nuit",
            "a bientot", "au revoir", "bye", "a plus"
        )
        if (trimmed in greetings) return true
        if (trimmed.startsWith("bonjour ") && trimmed.length < 25) return true
        if (trimmed.startsWith("salut ") && trimmed.length < 25) return true
        return false
    }

    private fun isTranslationRequest(norm: String): Boolean {
        val markers = listOf(
            "traduis", "traduire", "traduction", "comment dit-on", "comment on dit",
            "comment traduire", "que veut dire", "que signifie", "en moore", "en dioula",
            "en fulfulde", "en gulmancema", "en bissa", "en francais", "en anglais",
            "en espagnol", "en arabe", "en allemand"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isCodeOrProgramming(norm: String): Boolean {
        val markers = listOf(
            "ecris une fonction", "ecris du code", "programme en", "script python",
            "fonction kotlin", "comment coder", "code source", "algorithme",
            "en jetpack compose", "en android", "en java", "en c++", "en typescript",
            "en javascript", "en sql", "boucle for", "boucle while", "coroutine",
            "corrige ce code", "explique ce code", "regex pour", "api rest en"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isMathOrCalculation(norm: String): Boolean {
        if (norm.contains("calcule") || norm.contains("combien font") || norm.contains("combien fait") ||
            norm.contains("resous") || norm.contains("racine carree") || norm.contains("theoreme de pythagore") ||
            norm.contains("theoreme de thales") || norm.contains("equation")
        ) return true
        val mathPattern = Regex("""\d+\s*[\+\-\*\/xX÷]\s*\d+""")
        return mathPattern.containsMatchIn(norm)
    }

    private fun isEvergreenConceptOrDefinition(norm: String): Boolean {
        // Ex: « Qu'est-ce qu'un ordinateur ? », « C'est quoi la photosynthèse ? »
        val definitionStarts = listOf(
            "qu'est-ce qu'un", "qu'est-ce qu'une", "qu'est-ce que", "qu'est-ce qui",
            "qu'est ce qu'un", "qu'est ce qu'une", "qu'est ce que",
            "c'est quoi un", "c'est quoi une", "c'est quoi le", "c'est quoi la", "c'est quoi l'",
            "definition de", "definition d'", "explique le principe de", "comment fonctionne",
            "pourquoi le ciel", "pourquoi la mer", "qu'entend-on par"
        )
        val hasDefStart = definitionStarts.any { norm.startsWith(it) || norm.contains(" $it") }
        if (!hasDefStart) return false

        // Si la question contient des marqueurs temporels stricts, ce n'est PAS intemporel
        val temporalClues = listOf(
            "actuel", "actuelle", "aujourd'hui", "hier", "ce matin", "ce soir",
            "cette annee", "recemment", "dernier", "derniere", "prix", "score",
            "match", "president", "gouvernement", "ministre"
        )
        return !temporalClues.any { norm.contains(it) }
    }

    private fun isCreativeWriting(norm: String): Boolean {
        val markers = listOf(
            "ecris un poeme", "redige une lettre", "invente une histoire",
            "ecris un dialogue", "redige un email", "lettre de motivation",
            "donne-moi des idees de", "redige un discours", "raconte une histoire"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isSportsOrMatch(norm: String): Boolean {
        val markers = listOf(
            "match", "score", "qui a gagne", "qui a remporte", "qui a battu",
            "classement de", "ligue des champions", "champions league", "premier league",
            "la liga", "ligue 1", "serie a", "coupe du monde", "can 20", "etalons du burkina",
            "real madrid", "barca", "barcelone", "psg", "chelsea", "manchester",
            "liverpool", "bayern", "athletisme", "ballon d'or", "buteur", "mi-temps",
            "resultat du match"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isRecentNewsOrCurrentEvents(norm: String): Boolean {
        val markers = listOf(
            "derniere nouvelle", "dernieres nouvelles", "actualite", "actualites",
            "ce matin", "hier soir", "aujourd'hui", "hier", "cette semaine",
            "en ce moment", "derniere minute", "que se passe-t-il", "sommet de",
            "inauguration", "communique de", "ministre actuel", "president actuel",
            "gouvernement actuel", "au burkina faso aujourd'hui", "nouvelles du burkina",
            "evenement recent", "qui dirige actuellement", "situation actuelle"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isWeather(norm: String): Boolean {
        val markers = listOf(
            "meteo", "quel temps fait-il", "previsions meteo", "pleuvra-t-il",
            "temperature a", "temperature a ouagadougou", "chaleur a"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isLiveFinancialOrPrice(norm: String): Boolean {
        val markers = listOf(
            "prix actuel", "cours de", "taux de change", "valeur du dollar",
            "valeur de l'euro", "cours de l'or", "cours du baril", "cours du bitcoin",
            "combien coute aujourd'hui", "combien vaut actuellement", "prix du cfa",
            "prix du litre", "tarif actuel"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isFactCheckOrEntityCreation(norm: String): Boolean {
        // Ex: « Qui a créé Orange Money ? », « Qui a fondé Moov ? », etc.
        val markers = listOf(
            "qui a cree", "qui a fonde", "qui a invente", "qui est le createur de",
            "qui est le fondateur de", "qui a lance", "date de creation de",
            "date de fondation de", "qui possede", "a qui appartient", "pdg actuel de",
            "directeur general de", "quelle entreprise a cree", "quelle societe a cree"
        )
        return markers.any { norm.contains(it) }
    }

    private fun isFollowUpToSearch(norm: String, raw: String, history: List<ChatMessage>): Boolean {
        if (history.isEmpty()) return false
        if (raw.length > 50) return false // Les questions de suivi sont généralement courtes

        val lastAssistantMsg = history.lastOrNull { it.role == "model" }?.content.orEmpty()
        val lastUserMsg = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        val prevNorm = normalize(lastUserMsg + " " + lastAssistantMsg)

        val followUpTriggers = listOf(
            "et hier", "et aujourd'hui", "et pour", "et le deuxieme", "et le score",
            "et qui", "donne plus de details", "developpe", "et au burkina", "et en france",
            "et apres", "quelle source", "lien", "qu'en est-il"
        )
        val hasTrigger = followUpTriggers.any { norm.startsWith(it) || norm.contains(it) }
        val prevHadSearchContext = prevNorm.contains("match") || prevNorm.contains("score") ||
            prevNorm.contains("actualite") || prevNorm.contains("nouvelle") ||
            prevNorm.contains("http") || prevNorm.contains("source")

        return hasTrigger && prevHadSearchContext
    }

    private fun extractCleanSearchQuery(raw: String, triggerPhrases: List<String>): String {
        var clean = raw
        for (phrase in triggerPhrases) {
            clean = clean.replace(Regex("(?i)\\b$phrase\\b"), "")
        }
        clean = clean.trim().removePrefix(":").removePrefix(",").trim()
        return cleanSearchQueryForEngine(clean.ifBlank { raw })
    }

    private fun cleanSearchQueryForEngine(raw: String): String {
        return raw.trim()
            .replace(Regex("(?i)^(peux-tu|pourrais-tu|stp|s'il te plait|dis-moi|cherche|trouve|donne-moi)\\s+"), "")
            .trimEnd('?', '!', '.', ',')
            .trim()
    }
}
