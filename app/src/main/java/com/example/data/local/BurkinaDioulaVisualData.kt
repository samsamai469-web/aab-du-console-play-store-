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
        "Famille",
        "Nourriture",
        "Objets & Métiers",
        "Animaux",
        "Vêtements"
    )

    val visualCatalog = listOf(
        VisualDioulaItem(
            id = "dioula_3",
            dioulaWord = "Den",
            frenchTranslation = "Enfant",
            category = "Famille",
            pronunciation = "Dèn",
            localDrawable = R.drawable.img_burkinabe_child_1786953052232
        ),
        VisualDioulaItem(
            id = "dioula_7",
            dioulaWord = "Ba",
            frenchTranslation = "Mère / Maman",
            category = "Famille",
            pronunciation = "Ba",
            localDrawable = R.drawable.img_burkinabe_mother_1786879056257
        ),
        VisualDioulaItem(
            id = "dioula_10",
            dioulaWord = "Fa",
            frenchTranslation = "Père / Papa",
            category = "Famille",
            pronunciation = "Fa",
            localDrawable = R.drawable.img_burkinabe_father_1786953039606
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
            id = "dioula_14",
            dioulaWord = "Tô",
            frenchTranslation = "Tô (Plat traditionnel)",
            category = "Nourriture",
            pronunciation = "Tô",
            localDrawable = R.drawable.img_burkinabe_sagbo_1786953068024
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
        ),
        VisualDioulaItem(
            id = "dioula_11",
            dioulaWord = "Sô",
            frenchTranslation = "Cheval (Étalon)",
            category = "Animaux",
            pronunciation = "Sô",
            localDrawable = R.drawable.img_burkinabe_horse_weefo_1786881763857
        ),
        VisualDioulaItem(
            id = "dioula_12",
            dioulaWord = "Ba (Sagaba)",
            frenchTranslation = "Chèvre",
            category = "Animaux",
            pronunciation = "Ba",
            localDrawable = R.drawable.img_burkinabe_goat_1786881820618
        ),
        VisualDioulaItem(
            id = "dioula_13",
            dioulaWord = "Faso Dan Fani",
            frenchTranslation = "Pagne tissé burkinabè",
            category = "Vêtements",
            pronunciation = "Fa-so Dan Fa-ni",
            localDrawable = R.drawable.img_faso_dan_fani_1786953081141
        )
    )
}
