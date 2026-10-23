# SAM AI : Moteur d'Actualités et de Recherche en Direct pour OpenAI

Ce plan d'implémentation détaille l'intégration d'un pipeline d'alimentation en temps réel pour l'assistant SAM AI, permettant d'interroger la presse burkinabè (AIB, RTB, LeFaso.net, Sidwaya) et les dépêches mondiales (RFI, France 24, BBC, Reuters) dès qu'une question d'actualité est posée, afin d'injecter ces faits récents dans l'API officielle OpenAI.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> - **Maintien strict de l'API OpenAI** : L'assistant continue de s'appuyer sur l'API officielle OpenAI (via votre clé `OPENAI_API_KEY`), garantissant la qualité rédactionnelle et l'intelligence de GPT-4o.
> - **Alimentation dynamique du contexte** : Fin de la réponse « *Je n'ai pas accès aux informations en direct* ». Dès qu'une question d'actualité ou temporelle est posée, l'application récupère en direct les dernières dépêches et les fournit immédiatement au prompt d'OpenAI.
> - **Couverture double (Faso + Monde)** : Le moteur prend en compte les actualités nationales burkinabè et internationales généralistes.
> - **Zéro dépendance complexe payante** : Utilisation d'un agrégateur de flux RSS / d'actualités directes et d'un moteur de recherche d'actualités public sans abonnement tiers bloquant.

- **Décision confirmée** : Solution 1 choisie (OpenAI + actualités en temps réel Burkina Faso et Monde).
- **Format des réponses** : Synthèse soignée par OpenAI avec mention claire des sources d'actualités consultées en bas de bulle.

---

### 1. Overview & Core Concept

- **Problème résolu** : 
  Actuellement, lorsqu'un utilisateur pose une question sur les événements récents (ex: *« Quelles sont les actualités d'aujourd'hui au Burkina Faso ? »*), le modèle OpenAI n'ayant pas de navigateur web par défaut répond : *« Je n'ai pas accès aux informations en direct : je ne peux donc pas confirmer les nouvelles de ce vendredi 23 octobre 2026 »*.
- **Expérience cible** :
  L'application détecte en une fraction de seconde le besoin d'actualités, interroge les flux de presse en direct, résume les faits saillants récents et les transmet à OpenAI. L'utilisateur reçoit une réponse riche, documentée et à jour.
- **Bénéficiaires** :
  Tous les utilisateurs de SAM AI souhaitant suivre les actualités du Faso, de la sous-région et du monde, la politique, le sport, l'économie et la culture sans quitter leur application mobile.

---

### 2. User Experience & Visual Design

- **Dans le chat SAM AI (`SamAiGeminiAssistantModal`)** :
  1. L'utilisateur saisit sa question : *« Quelles sont les dernières actualités du Burkina Faso et de la sous-région ? »*.
  2. Un indicateur discret s'affiche brièvement : *« Consultation des dernières dépêches en direct... ⚡ »*.
  3. SAM AI (OpenAI) formule une réponse claire et structurée en sections thématiques (Politique, Sécurité/Développement, Société/Sport).
  4. En bas de la bulle de message, un bloc épuré présente les sources d'information consultées (ex: `AIB • RTB • LeFaso.net • RFI`).
- **Composants d'interface (Material 3)** :
  - Badges de fraîcheur temporelle avec icône d'horodatage.
  - Boutons d'action rapides (Écouter en synthèse vocale, Détailler, Copier).
  - Aucune mention d'erreur technique ni de message bloquant sur la connectivité internet.

---

### 3. Key Product Decisions & Trade-Offs

- **Décision 1 : Détection intelligente de l'intention d'actualité via `WebSearchClassifier`**
  - *Choix* : Réutiliser et optimiser le classifieur existant pour différencier les questions générales (traduction, définitions, code) qui vont directement à OpenAI, des questions d'actualité/sport/politique qui déclenchent la récupération de dépêches.
  - *Pourquoi* : Préserve la réactivité et la vitesse pour les questions intemporelles, tout en activant la recherche uniquement quand elle apporte une réelle valeur.
- **Décision 2 : Pipeline direct via un service de dépêches d'actualités (`LiveNewsFeedService`)**
  - *Choix* : Créer un service dédié interrogeant directement les flux d'actualités structurés (AIB, RTB, LeFaso, RFI, France24, BBC Afrique) et les actualités web ouvertes en JSON/XML sans nécessiter de clé API tierce.
  - *Pourquoi* : Fiabilité maximale, aucun quota tiers bloquant (contrairement aux limites de Tavily ou de clés tierces payantes), rapidité d'exécution.
- **Décision 3 : Injection dans le prompt OpenAI sans altérer le modèle**
  - *Choix* : Insérer les titres et résumés dans le message `developer`/`system` sous forme de contexte factuel certifié.
  - *Pourquoi* : Exploite la pleine puissance de raisonnement de `gpt-4o` tout en évitant toute hallucination sur les dates récentes.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Utilisateur (Chat SAM AI)                       │
│             « Dernières actualités du Burkina Faso et du monde »       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          SamAiViewModel                                │
│                   sendSamAiUniversalMessage(...)                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
         (Question générale)                 (Besoin d'actualité)
                  │                                   │
                  │                                   ▼
                  │                    ┌──────────────────────────────┐
                  │                    │     LiveNewsFeedService      │
                  │                    │ - Presse Faso (AIB, RTB,     │
                  │                    │   Lefaso, Sidwaya)           │
                  │                    │ - Presse Monde (RFI, BBC,    │
                  │                    │   France 24)                 │
                  │                    └──────────────┬───────────────┘
                  │                                   │ (Dépêches du jour)
                  ▼                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         OpenAiBackendService                           │
│        Appel https://api.openai.com/v1/chat/completions                │
│    - Rôle système : SAM AI + Contexte d'actualité temps réel          │
│    - Rôle utilisateur : Question d'origine                            │
│    - max_completion_tokens : 3000                                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│            Réponse complète avec faits réels et sources claires        │
└────────────────────────────────────────────────────────────────────────┘
```

- **Composants clés à créer / enrichir** :
  1. `LiveNewsFeedService.kt` : Moteur de récupération en temps réel des dépêches et articles du Faso et du monde avec cache de courte durée (5 minutes) pour la rapidité.
  2. `OpenAiBackendService.kt` : Injection dynamique des articles dans le prompt contextuel envoyé à l'API OpenAI.
  3. `SamAiViewModel.kt` : Orchestration fluide du flux sans temps mort ni message d'erreur.
