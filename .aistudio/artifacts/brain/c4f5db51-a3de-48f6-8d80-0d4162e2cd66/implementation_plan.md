# Résolution de l'Assistant SAM AI et Suppression des Références aux Clés API

Ce plan détaille la correction complète de l'assistant SAM AI pour répondre de manière fluide et intelligente à toutes les questions posées par les utilisateurs, sans jamais afficher de message d'erreur technique ni inviter à configurer une clé API, tout en supprimant les options de clés d'API de l'écran Profil.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Les directives strictes suivantes fournies par l'utilisateur sont au cœur de cette mise à jour :
> - **Zéro message sur la clé API** : Aucun utilisateur ne doit jamais voir de message lui demandant de configurer ou vérifier une clé API Gemini dans les paramètres.
> - **Suppression du Profil** : Retrait définitif du menu et de la boîte de dialogue de saisie manuelle de clés API dans l'écran Profil.
> - **Assistant toujours fonctionnel** : SAM AI doit répondre avec précision grâce à son moteur hybride (Gemini en ligne lorsque la clé d'environnement est active, et bascule transparente immédiate sur sa riche base de connaissances burkinabè et conversationnelle en local en cas d'absence de clé ou de panne réseau).

---

### 1. Overview & Core Concept

- **Problème résolu** :
  - Lorsqu'un utilisateur pose une question dans le chat SAM AI sans que la clé API Gemini soit initialisée côté serveur, l'application renvoyait une erreur textuelle désagréable : *« Clé API Gemini absente ou non configurée »* ou *« Veuillez vérifier votre clé API dans les Paramètres de l'application »*.
  - De plus, l'écran Profil proposait un bouton technique invitant les utilisateurs finaux à coller des clés API complexes (Gemini et Tavily).
- **Solution apportée** :
  - **Masquage et neutralisation technique totale** : Retrait de toute consigne relative aux clés API destinées au grand public.
  - **Nettoyage de l'écran Profil** : Retrait du bloc « Configuration IA & Recherche Web » dans les préférences de `ProfileScreen.kt`.
  - **Moteur de réponse résilient** : Si Gemini n'est pas configuré ou rencontre une erreur (quota, réseau, absence de clé), SAM AI active instantanément un générateur de réponses conversationnelles riche basé sur la mémoire locale du Faso (histoire, langues Mooré et Dioula, culture, créateur Abdoul Mohamine Sawadogo, savoirs généraux), garantissant une expérience fluide sans le moindre message d'erreur.

---

### 2. User Experience & Visual Design

- **Dans le chat SAM AI (`SamAiGeminiAssistantModal`)** :
  - L'utilisateur saisit sa question ou choisit une suggestion.
  - SAM AI réfléchit et affiche directement une réponse complète, structurée et chaleureuse.
  - Finie toute bulle d'erreur rouge ou mention de paramètres d'API. L'assistant paraît toujours opérationnel et intelligent.
- **Dans l'écran Profil (`ProfileScreen`)** :
  - L'interface des préférences est harmonieuse et accessible : choix du Thème (Sombre / Clair), Style de palette (Moderne / Classique), Langue de l'app (Français / Anglais), Rappels et notifications, À propos et Support.
  - Zéro formulaire technique de clés d'API pour les utilisateurs normaux.

---

### 3. Key Product Decisions & Trade-Offs

- **Décision 1 : Fallback conversationnel natif au lieu d'une erreur bloquante**
  - *Choix* : Si l'API cloud n'est pas joignable ou sans clé valide, intercepter l'erreur au niveau de `SamAiViewModel` et `AiRepository` pour générer une réponse contextuelle immédiate à partir du lexique, des dictionnaires (Mooré, Dioula, Haoussa), de la base de données historique et du moteur de dialogue burkinabè.
  - *Pourquoi* : L'utilisateur bénéficie d'une réponse utile et immédiate sans frustration.
- **Décision 2 : Suppression du dialogue de clés API dans Profil**
  - *Choix* : Supprimer `item_profile_ai_config` et `showAiConfigDialog` de la vue utilisateur standard.
  - *Pourquoi* : Conforme à la demande explicite de l'utilisateur (« aucun ne doit jamais voir ça, faut l'enlever dans le profil »).
- **Décision 3 : Réécriture des messages d'erreur de `GeminiErrorHandler`**
  - *Choix* : Remplacer toute mention de clé d'API par une formulation d'assistance naturelle et bienveillante ou déclencher la réponse autonome.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                      Utilisateur (Chat SAM AI)                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            SamAiViewModel                              │
│                   askSamAiGemini(userQuestion)                         │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
    (Clé active présente et valide)   (Absence de clé ou Erreur réseau/API)
                    ▼                                ▼
┌───────────────────────────────────────┐ ┌──────────────────────────────┐
│        Google Gemini API Cloud        │ │  Moteur Local Intelligent    │
│    (gemini-3.5-flash via Retrofit)    │ │   - Dictionnaires & Langues  │
│                                       │ │   - Patrimoine & Histoire    │
│                                       │ │   - Connaissances du Faso    │
└───────────────────┬───────────────────┘ └──────────────┬───────────────┘
                    │                                    │
                    └─────────────────┬──────────────────┘
                                      │
                                      ▼
             ┌─────────────────────────────────────────────────┐
             │       Réponse fluide, riche et sans erreur      │
             │       affichée directement dans la bulle        │
             └─────────────────────────────────────────────────┘
```

- **Fichiers ciblés pour l'exécution** :
  1. `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` :
     - Retirer l'élément de liste de configuration IA (`item_profile_ai_config`) et le dialogue `showAiConfigDialog`.
  2. `app/src/main/java/com/example/util/GeminiErrorHandler.kt` :
     - Éliminer toute mention invitant à vérifier la clé API dans les paramètres.
  3. `app/src/main/java/com/example/data/repository/AiRepository.kt` :
     - Ne plus bloquer ni retourner d'erreur brute « Clé API absente ».
  4. `app/src/main/java/com/example/ui/SamAiViewModel.kt` :
     - En cas d'erreur de communication ou d'absence de clé, déclencher automatiquement le moteur de réponse intelligent local pour fournir une réponse de haute qualité sans afficher de message d'erreur.
