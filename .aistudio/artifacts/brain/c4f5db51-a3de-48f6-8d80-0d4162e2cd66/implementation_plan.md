# Résolution et Fiabilisation du Moteur de Traduction Multilingue

Ce plan vise à corriger définitivement le dysfonctionnement de la traduction sur l'écran principal où la phrase tapée revenait à l'identique sans être traduite. Il met en place une architecture robuste : traduction mot-à-mot certifiée par le dictionnaire local pour les mots et noms isolés, et traduction directe haute fidélité par l'IA Gemini (`gemini-3.5-flash` via Retrofit REST) pour toutes les phrases et combinaisons de langues.

### User Review & Critical Decisions

> [!IMPORTANT]
> Les trois décisions suivantes ont été validées lors de notre échange de clarification :

- **Couverture linguistique validée** : Toutes les combinaisons de langues prises en charge (Français ⇄ Mooré, Français ⇄ Dioula, Français ⇄ Haoussa, Français ⇄ Anglais, et inter-langues).
- **Écran cible confirmé** : L'onglet principal **Traduction** (avec retombées positives sur la caméra/photo et l'audio).
- **Stratégie hybride intelligente confirmée** :
  1. *Pour les mots et petits noms* : Recherche instantanée et mot-à-mot dans le dictionnaire certifié hors-ligne (1900+ entrées).
  2. *Pour les phrases* : Traduction fluide, naturelle et directe par l'IA Gemini (`gemini-3.5-flash`), sans jamais renvoyer la phrase d'origine non traduite.

---

### 1. Overview & Core Concept

- **What It Does** :
  Permet aux utilisateurs de saisir n'importe quelle phrase ou mot dans l'onglet Traduction et d'obtenir immédiatement la traduction exacte dans la langue cible sans échec, sans blocage et sans retour de la phrase source.
- **Target Audience / Persona** :
  Toute personne souhaitant communiquer, apprendre ou traduire entre les langues du Burkina Faso (Mooré, Dioula, Haoussa) et les langues internationales (Français, Anglais).
- **Key Value** :
  Suppression du bug où la phrase d'entrée revenait telle quelle ; traduction garantie, fluide et exacte.

---

### 2. User Experience & Visual Design

- **Key User Flows** :
  1. *Saisie d'un mot ou petit nom (ex: « Maison », « Eau », « Marché »)* :
     - Résolution immédiate par le dictionnaire local certifié (`Yiri`, `Koom`, `Raaga` en Mooré / `So`, `Ji`, `Sugu` en Dioula).
     - Badge : « Dictionnaire Certifié » ou « Corpus Local ».
  2. *Saisie d'une phrase complète (ex: « Bonjour mon ami, comment vas-tu ? »)* :
     - Appel direct au moteur IA Gemini (`gemini-3.5-flash`).
     - Rendu direct et naturel de la phrase traduite (ex: « *Ne yibeoogo m zoa, f laafi be ?* » en Mooré ou « *I ni sogoma ne teri, i ka kɛnɛ wa ?* » en Dioula).
     - Badge : « Traduction IA Gemini ».
  3. *Gestion des cas hors-ligne / réseau faible* :
     - Si le réseau est temporairement indisponible, tentative de recomposition lexicale intelligente mot-à-mot.
     - Si une phrase ne peut être traduite, affichage d'un message explicatif clair plutôt que de dupliquer silencieusement le texte d'origine.
- **Visual Identity & Theme** :
  - Respect de l'interface Material 3 existante.
  - Indicateur de chargement propre avec bouton d'action et feedback sonore (TTS).

---

### 3. Key Product Decisions & Trade-Offs

- **Décision 1 : Intégration de Retrofit REST pour `translateDirectly`**
  - *Approche choisie* : Utiliser `RetrofitClient.geminiService` avec le modèle `gemini-3.5-flash` au lieu de dépendre du service non officiel `GoogleTranslationService` (qui rejette `mos` et `dyu` avec une erreur HTTP 400).
  - *Bénéfice* : Fonctionnement immédiat et éprouvé sur toutes les langues sans dépendance externe fragile.
- **Décision 2 : Priorisation intelligente selon la longueur et le type de texte**
  - *Approche choisie* : Si le texte comporte 1 à 2 mots simples, interroger en priorité le dictionnaire certifié local. Dès qu'il s'agit d'une phrase (≥ 3 mots ou ponctuation), faire appel directement à l'IA Gemini tout en conservant le dictionnaire en repli.
  - *Bénéfice* : Vitesse instantanée sur le vocabulaire et fluidité parfaite sur les phrases.
- **Décision 3 : Élimination du fallback silencieux `translated = cleanText`**
  - *Approche choisie* : Interdire le renvoi de la phrase source brute lorsque les langues source et cible sont différentes. En cas de panne totale de réseau, fournir un message pédagogique clair ou une traduction mot-à-mot avec alertes.
  - *Bénéfice* : Fin de l'incompréhension pour l'utilisateur.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────┐
│                     TranslationScreen (UI)                       │
│    (Saisie texte, sélection langue source -> langue cible)       │
└─────────────────────────────────┬────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                      SamAiViewModel                              │
│    - translate(source, target, text)                             │
└─────────────────────────────────┬────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                  TranslationRepository                           │
│  1. Si mot/nom court (1-2 mots) -> Dictionnaire local certifié   │
│  2. Si phrase -> Moteur IA Gemini (AiRepository.translateDirect) │
│  3. Recomposition lexicale / Fallback sécurisé                   │
└──────────────────┬──────────────────────────────┬────────────────┘
                   │                              │
                   ▼                              ▼
┌──────────────────────────────────┐ ┌─────────────────────────────┐
│          AiRepository            │ │   DictionaryLookupEngine    │
│  - RetrofitClient.geminiService  │ │  (1900+ entrées Mooré,      │
│  - gemini-3.5-flash              │ │   Dioula, Haoussa hors-     │
│  - Prompt haute fidélité         │ │   ligne certifiées)         │
└──────────────────────────────────┘ └─────────────────────────────┘
```

- **Fichiers modifiés lors de l'exécution** :
  1. `app/src/main/java/com/example/data/repository/AiRepository.kt` :
     - Implémenter l'appel `RetrofitClient.geminiService` avec `gemini-3.5-flash` dans `translateDirectly`.
     - Nettoyer le formatage du prompt de traduction pour garantir une sortie directe sans fioritures.
  2. `app/src/main/java/com/example/data/repository/TranslationRepository.kt` :
     - Ajuster l'ordre des résolutions : dictionnaire pour les mots isolés, IA directe pour les phrases.
     - Remplacer le renvoi silencieux de la phrase source par une restitution utile.
  3. `app/src/main/java/com/example/ui/SamAiViewModel.kt` :
     - Synchroniser le flux d'affichage et la gestion des états de traduction.
