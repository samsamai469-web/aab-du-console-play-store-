# Intégration de la Recherche Web Tavily avec Google Gemini pour SAM AI

Ce plan d'architecture détaille l'intégration native du moteur de recherche en direct **Tavily Search API** couplé à **Google Gemini** (`gemini-3.6-flash` / `gemini-3.5-flash`), conformément au script Python fourni par l'utilisateur et adapté à l'environnement Android.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Les trois décisions suivantes ont été validées lors de notre échange de clarification :

- **Configuration de la clé API Tavily** : Prise en charge via les variables d'environnement et `BuildConfig.TAVILY_API_KEY` (avec injection automatique via `.env.example` et le panneau Secrets d'AI Studio, et possibilité de personnalisation).
- **Déclenchement de la recherche** : Exécution systématique de la recherche web Tavily sur toutes les questions posées à SAM AI pour garantir une pertinence maximale et des données en temps réel.
- **Affichage des sources web** : Présentation sous forme de badges de sources cliquables sous la réponse, affichant le titre ou domaine avec ouverture du lien web au clic.

---

### 1. Overview & Core Concept

- **What It Does** :
  - Lorsqu'un utilisateur pose une question à SAM AI, l'application interroge d'abord l'API Tavily (`POST https://api.tavily.com/search`) pour récupérer les 5 résultats web les plus pertinents et récents (titres, URLs, extraits de contenu et synthèse).
  - Ces résultats sont injectés en contexte enrichi dans la requête Gemini (`gemini-3.6-flash` / `gemini-3.5-flash`).
  - Gemini rédige une réponse fluide, structurée et précise en citant les faits récents.
  - Les sources web réelles sont attachées au message et présentées sous forme de badges cliquables interactifs.
- **Target Audience / Persona** :
  - Tout utilisateur souhaitant obtenir des réponses d'actualité, des données en direct (Burkina Faso, Afrique, monde, météo, économie, résultats sportifs) et vérifier les sources d'information.
- **Key Value** :
  - SAM AI ne souffre plus d'aucune coupure de connaissances temporelle ; il combine la puissance de raisonnement de Gemini avec la fraîcheur du web en direct via Tavily.

---

### 2. User Experience & Visual Design

- **Key User Flows** :
  1. *Saisie d'une question dans le chat SAM AI* :
     - L'utilisateur tape par exemple : *« Quelle est la dernière actualité au Burkina Faso ? »* ou *« Qui a gagné le dernier match ? »*.
     - Indicateur de progression : *« Recherche web en cours avec Tavily... »*.
  2. *Affichage de la réponse et des sources* :
     - La réponse de SAM AI s'affiche avec clarté.
     - Juste en dessous de la bulle de message, une section *« Sources Web en temps réel : »* présente les badges interactifs :
       - Icône web 🌐
       - Titre de l'article ou domaine (ex: *lefaso.net*, *sidwaya.info*, *rfi.fr*)
       - Au clic sur le badge, ouverture instantanée de l'article dans le navigateur.
  3. *Gestion de la clé ou de l'absence de réseau* :
     - Si la clé Tavily n'est pas configurée ou indisponible, repli transparent sur la recherche web native ou les connaissances Gemini sans faire échouer la discussion.
     - Si aucune connexion Internet n'est détectée, notification claire : *« Connexion Internet requise pour la recherche en direct »*.
- **Visual Identity & Theme** :
  - Respect strict des directives Material 3.
  - Badges de sources avec coins arrondis, bordure discrète et retour haptique/ripple au toucher.

---

### 3. Key Product Decisions & Trade-Offs

- **Décision 1 : Client Retrofit / OkHttp dédié pour Tavily Search**
  - *Approche choisie* : Création d'un service léger `TavilySearchService` utilisant OkHttp et `kotlinx.serialization` pour appeler `https://api.tavily.com/search`.
  - *Bénéfice* : Aucun SDK lourd Python nécessaire ; intégration 100% native Kotlin asynchrone avec coroutines sans surcoût d'APK.
- **Décision 2 : Injection de contexte en deux temps (Recherche -> Synthèse Gemini)**
  - *Approche choisie* : Exécuter la recherche Tavily en amont de l'appel Gemini, puis injecter les extraits dans le prompt système et associer les `WebSource` au message final.
  - *Bénéfice* : Réponse déterministe, zéro hallucination sur les URLs, et contrôle parfait des badges de sources dans l'interface Compose.
- **Décision 3 : Secret Management sécurisé (`TAVILY_API_KEY`)**
  - *Approche choisie* : Déclaration de `TAVILY_API_KEY` dans `.env.example`, accès via `BuildConfig.TAVILY_API_KEY` et intégration dans `ApiKeyManager`.
  - *Bénéfice* : Sécurité garantie, respect strict de la politique de secrets d'AI Studio, et flexibilité pour l'utilisateur.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────┐
│                    SamAiGeminiAssistantModal (UI)                    │
│     - Champ de saisie utilisateur                                    │
│     - Bulles de messages avec Badges de Sources Web cliquables       │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                            SamAiViewModel                            │
│     - askSamAiGemini(userText)                                       │
│     - Orchestre la recherche Tavily et l'appel Gemini                │
└──────────────────┬───────────────────────────────┬───────────────────┘
                   │                               │
                   ▼                               ▼
┌──────────────────────────────────┐ ┌─────────────────────────────────┐
│       TavilySearchService        │ │          AiRepository           │
│  - POST https://api.tavily.com   │ │  - RetrofitClient Gemini REST   │
│  - Extraction titres, URLs,      │ │  - gemini-3.6-flash / 3.5-flash │
│    extraits de contenu récents   │ │  - Injection contexte web       │
└──────────────────────────────────┘ └─────────────────────────────────┘
```

- **Composants et Fichiers à modifier / créer** :
  1. `.env.example` :
     - Ajouter `TAVILY_API_KEY=tvly-your-tavily-api-key-here`.
  2. `app/src/main/java/com/example/data/remote/TavilySearchService.kt` :
     - Définir les modèles de données sérialisables (`TavilySearchRequest`, `TavilySearchResponse`, `TavilySearchResultItem`).
     - Créer le client HTTP appelant `https://api.tavily.com/search`.
  3. `app/src/main/java/com/example/data/local/ApiKeyManager.kt` :
     - Gérer la récupération de `TAVILY_API_KEY` via `BuildConfig.TAVILY_API_KEY` et SharedPreferences.
  4. `app/src/main/java/com/example/ui/SamAiViewModel.kt` :
     - Déclencher la recherche Tavily avant la génération Gemini, mapper les résultats vers `List<WebSource>`, et injecter le contexte d'actualité.
