package com.example.util

object AppStrings {
    fun appSubtitle(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Bãng-m-meng la gom-biisi"
        "dioula" -> "Kalan ni kuman fanga"
        "haoussa", "hausa" -> "Koyan harshe da fassara"
        "en" -> "Intelligent Assistant & Multimodal Translator"
        else -> "Assistant intelligent & Traducteur Multimodal"
    }

    fun categoryName(category: String, lang: String): String = when (category.lowercase()) {
        "salutations" -> when (lang.lowercase()) {
            "moore", "mooré" -> "Pusgu"
            "dioula" -> "Fôli"
            "haoussa", "hausa" -> "Gaisuwa"
            "en" -> "Greetings"
            else -> "Salutations"
        }
        "famille" -> when (lang.lowercase()) {
            "moore", "mooré" -> "Zak-rãmba"
            "dioula" -> "Somogow"
            "haoussa", "hausa" -> "Iyali"
            "en" -> "Family"
            else -> "Famille"
        }
        "commerce", "marché" -> when (lang.lowercase()) {
            "moore", "mooré" -> "Raaga"
            "dioula" -> "Sugu"
            "haoussa", "hausa" -> "Kasuwa"
            "en" -> "Market"
            else -> "Commerce"
        }
        "nourriture", "repas" -> when (lang.lowercase()) {
            "moore", "mooré" -> "Rɩɩb"
            "dioula" -> "Dumuni"
            "haoussa", "hausa" -> "Abinci"
            "en" -> "Food"
            else -> "Nourriture"
        }
        "nombres" -> when (lang.lowercase()) {
            "moore", "mooré" -> "Sõdse"
            "dioula" -> "Jateminɛ"
            "haoussa", "hausa" -> "Lambobi"
            "en" -> "Numbers"
            else -> "Nombres"
        }
        else -> category
    }

    fun navHome(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Yiri"
        "dioula" -> "So"
        "haoussa", "hausa" -> "Gida"
        "en" -> "Home"
        else -> "Accueil"
    }

    fun navTranslation(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Gom-lebgre"
        "dioula" -> "Kuma bayɛlɛman"
        "haoussa", "hausa" -> "Fassara"
        "en" -> "Translate"
        else -> "Traduire"
    }

    fun navLearning(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Zãmsg"
        "dioula" -> "Kalan"
        "haoussa", "hausa" -> "Koyo"
        "en" -> "Learn"
        else -> "Apprendre"
    }

    fun navNews(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Kibay"
        "dioula" -> "Kibarow"
        "haoussa", "hausa" -> "Labarai"
        "en" -> "News"
        else -> "Actualités"
    }

    fun navProfile(lang: String): String = when (lang.lowercase()) {
        "moore", "mooré" -> "Ned tɩɩr"
        "dioula" -> "Môgô"
        "haoussa", "hausa" -> "Bayanai"
        "en" -> "Profile"
        else -> "Profil"
    }

    fun profileTitle(lang: String): String = when (lang.lowercase()) {
        "en" -> "My Profile & Settings"
        "moore", "mooré" -> "Mam tɩɩr la yɛla"
        "dioula" -> "Ne ka kow ni ladilikan"
        else -> "Mon Profil & Paramètres"
    }

    fun preferences(lang: String): String = when (lang.lowercase()) {
        "en" -> "Preferences"
        "moore", "mooré" -> "Nonglem yɛla"
        "dioula" -> "Fɛn fɔlɔw"
        else -> "Préférences"
    }

    fun appearance(lang: String): String = when (lang.lowercase()) {
        "en" -> "Appearance & Theme"
        "moore", "mooré" -> "Gesgo la yɛla"
        "dioula" -> "Cogo ni kulu"
        else -> "Apparence & Thème"
    }

    fun privacy(lang: String): String = when (lang.lowercase()) {
        "en" -> "Privacy & Security"
        "moore", "mooré" -> "Gũub la laafɩ"
        "dioula" -> "Lakana ni gundolilaw"
        else -> "Confidentialité & Sécurité"
    }

    fun about(lang: String): String = when (lang.lowercase()) {
        "en" -> "About SAM AI"
        "moore", "mooré" -> "SAM AI yɛla"
        "dioula" -> "SAM AI kow"
        else -> "À propos de SAM AI"
    }

    fun addedToFavorites(lang: String): String = when (lang.lowercase()) {
        "en" -> "Added to favorites ⭐"
        "moore", "mooré" -> "Paasa nonglem pʋgẽ ⭐"
        "dioula" -> "Fara ne fɛn fɛlɛkan kan ⭐"
        else -> "Ajouté aux favoris ⭐"
    }

    fun removedFromFavorites(lang: String): String = when (lang.lowercase()) {
        "en" -> "Removed from favorites"
        "moore", "mooré" -> "Yiis nonglem pʋgẽ"
        "dioula" -> "Bɔ ne fɛn fɛlɛkan kan"
        else -> "Retiré des favoris"
    }

    fun filterAll(count: Int, lang: String): String = when (lang.lowercase()) {
        "en" -> "All ($count)"
        "moore", "mooré" -> "Fãa ($count)"
        "dioula" -> "Bɛɛ ($count)"
        else -> "Tout ($count)"
    }

    fun filterConversations(count: Int, lang: String): String = when (lang.lowercase()) {
        "en" -> "Conversations ($count)"
        "moore", "mooré" -> "Gom-biis ($count)"
        "dioula" -> "Kumanw ($count)"
        else -> "Conversations ($count)"
    }

    fun filterTranslations(count: Int, lang: String): String = when (lang.lowercase()) {
        "en" -> "Translations ($count)"
        "moore", "mooré" -> "Gom-lebgre ($count)"
        "dioula" -> "Kuma bayɛlɛmanw ($count)"
        else -> "Traductions ($count)"
    }

    fun filterFavorites(count: Int, lang: String): String = when (lang.lowercase()) {
        "en" -> "Favorites ($count) ⭐"
        "moore", "mooré" -> "Nonglem ($count) ⭐"
        "dioula" -> "Fɛn fɔlɔw ($count) ⭐"
        else -> "Favoris ($count) ⭐"
    }
}
