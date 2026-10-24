package com.example.data.local

import com.example.R
import kotlinx.serialization.Serializable

@Serializable
data class VisualDioulaItem(
    val id: String,
    val dioulaWord: String,
    val frenchTranslation: String,
    val category: String,
    val pronunciation: String = "",
    val imageUrl: String = "",
    val localDrawable: Int? = null,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val word: String get() = dioulaWord
    val frenchMeaning: String get() = frenchTranslation
    val dioulaTranslation: String get() = dioulaWord
    val frenchWord: String get() = frenchTranslation
    val dioulaPronunciation: String get() = pronunciation
}

object BurkinaDioulaVisualData {
    val categories = listOf(
        "Tous",
        "Salutations",
        "Famille",
        "Nourriture",
        "Objets & Métiers"
    )

    val visualCatalog = listOf(
        VisualDioulaItem(
            id = "dioula_1",
            dioulaWord = "I ni sogoma",
            frenchTranslation = "Bonjour (le matin)",
            category = "Salutations",
            pronunciation = "I ni so-go-ma",
            localDrawable = R.drawable.img_laafi_bala_friends_1786881102699
        ),
        VisualDioulaItem(
            id = "dioula_2",
            dioulaWord = "I ni su",
            frenchTranslation = "Bonsoir / Bonne nuit",
            category = "Salutations",
            pronunciation = "I ni su",
            localDrawable = R.drawable.img_laafi_bala_village_1786881210261
        ),
        VisualDioulaItem(
            id = "dioula_4",
            dioulaWord = "Dumuni",
            frenchTranslation = "Nourriture / Repas",
            category = "Nourriture",
            pronunciation = "Dou-mou-ni",
            localDrawable = R.drawable.img_burkinabe_eating_rice_1786881902148
        ),
        VisualDioulaItem(
            id = "dioula_5",
            dioulaWord = "Ji",
            frenchTranslation = "Eau",
            category = "Nourriture",
            pronunciation = "Dji",
            localDrawable = R.drawable.img_burkinabe_water_1786881881442
        ),
        VisualDioulaItem(
            id = "dioula_6",
            dioulaWord = "So",
            frenchTranslation = "Maison / Concession",
            category = "Famille",
            pronunciation = "So",
            localDrawable = R.drawable.img_burkinabe_house_1786881867414
        ),
        VisualDioulaItem(
            id = "dioula_8",
            dioulaWord = "Sènɛkɛla",
            frenchTranslation = "Cultivateur / Agriculteur",
            category = "Objets & Métiers",
            pronunciation = "Sè-nè-kè-la",
            localDrawable = R.drawable.img_burkinabe_farmer_1786879067110
        ),
        VisualDioulaItem(
            id = "dioula_9",
            dioulaWord = "Numun",
            frenchTranslation = "Forgeron",
            category = "Objets & Métiers",
            pronunciation = "Nou-moun",
            localDrawable = R.drawable.img_burkinabe_blacksmith_1786881777979
        )
    )
}
