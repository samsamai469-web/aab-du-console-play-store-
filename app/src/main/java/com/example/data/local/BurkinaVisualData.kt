package com.example.data.local

import com.example.R

data class VisualMooreItem(
    val id: Long,
    val word: String, // Mooré word / expression
    val pronunciation: String, // Phonetic guide
    val category: String,
    val imageUrl: String = "",
    val localDrawable: Int? = null,
    val frenchMeaning: String = "",
    val contextInfo: String = "Burkina Faso",
    val status: String = "validated"
)

object BurkinaVisualData {
    val visualCatalog: List<VisualMooreItem> = listOf(
        // --- 1. FAMILLE BURKINABÈ ---
        VisualMooreItem(
            id = 101,
            word = "M'ma",
            pronunciation = "Oum-ma",
            category = "Famille",
            frenchMeaning = "Mère / Maman",
            localDrawable = R.drawable.img_burkinabe_mother_1786879056257,
            contextInfo = "Mère burkinabè et son enfant au foyer familial."
        ),
        VisualMooreItem(
            id = 102,
            word = "M'ba",
            pronunciation = "Em-ba",
            category = "Famille",
            frenchMeaning = "Père / Papa",
            localDrawable = R.drawable.img_burkinabe_father_1786953039606,
            contextInfo = "Père de famille burkinabè bienveillant et respecté."
        ),
        VisualMooreItem(
            id = 103,
            word = "Biiga",
            pronunciation = "Bii-ga",
            category = "Famille",
            frenchMeaning = "Enfant",
            localDrawable = R.drawable.img_burkinabe_child_1786953052232,
            contextInfo = "Enfant burkinabè joyeux et plein de vie."
        ),
        VisualMooreItem(
            id = 104,
            word = "Saamba",
            pronunciation = "Saam-ba",
            category = "Famille",
            frenchMeaning = "Famille / Parents",
            localDrawable = R.drawable.img_burkinabe_family_1786879044801,
            contextInfo = "Famille burkinabè unie et chaleureuse."
        ),

        // --- 2. ALIMENTS & VIE QUOTIDIENNE ---
        VisualMooreItem(
            id = 701,
            word = "Koom",
            pronunciation = "Koum",
            category = "Aliments",
            frenchMeaning = "Eau (Source de vie)",
            localDrawable = R.drawable.img_burkinabe_water_1786881881442,
            contextInfo = "Eau fraîche et pure, première boisson offerte à chaque invité au Faso."
        ),
        VisualMooreItem(
            id = 702,
            word = "Rii",
            pronunciation = "Rii",
            category = "Aliments",
            frenchMeaning = "Manger / Repas",
            localDrawable = R.drawable.img_burkinabe_eating_rice_1786881902148,
            contextInfo = "Personne burkinabè savourant un bon repas chaud."
        ),
        VisualMooreItem(
            id = 703,
            word = "Sagbo",
            pronunciation = "Sag-bo",
            category = "Aliments",
            frenchMeaning = "Tô (Plat traditionnel de mil/maïs)",
            localDrawable = R.drawable.img_burkinabe_sagbo_1786953068024,
            contextInfo = "Plat traditionnel national burkinabè de pâte de mil ou maïs avec sa sauce gombo."
        ),
        VisualMooreItem(
            id = 705,
            word = "Gõse",
            pronunciation = "Gonn-sé",
            category = "Nature",
            frenchMeaning = "Dormir / Se reposer",
            localDrawable = R.drawable.img_burkinabe_sleeping_peace_1786881948233,
            contextInfo = "Moment de repos paisible et réparateur."
        ),

        // --- 3. MAISON, MÉTIERS & CULTURE ---
        VisualMooreItem(
            id = 502,
            word = "Yiri",
            pronunciation = "Yi-ri",
            category = "Maison",
            frenchMeaning = "Maison / Concession",
            localDrawable = R.drawable.img_burkinabe_house_1786881867414,
            contextInfo = "Maison traditionnelle en terre et concession familiale au Burkina Faso."
        ),
        VisualMooreItem(
            id = 504,
            word = "Kooda",
            pronunciation = "Kou-da",
            category = "Métiers",
            frenchMeaning = "Agriculteur / Cultivateur",
            localDrawable = R.drawable.img_burkinabe_farmer_1786879067110,
            contextInfo = "Cultivateur burkinabè travaillant la terre avec courage et savoir-faire."
        ),
        VisualMooreItem(
            id = 505,
            word = "Saba",
            pronunciation = "Sa-ba",
            category = "Métiers",
            frenchMeaning = "Forgeron artisan",
            localDrawable = R.drawable.img_burkinabe_blacksmith_1786881777979,
            contextInfo = "Artisan forgeron façonnant le métal avec passion dans son atelier."
        ),
        VisualMooreItem(
            id = 401,
            word = "Faso Dan Fani",
            pronunciation = "Fa-so Dan Fa-ni",
            category = "Vêtements",
            frenchMeaning = "Pagne tissé traditionnel burkinabè",
            localDrawable = R.drawable.img_faso_dan_fani_1786953081141,
            contextInfo = "Pagne en coton tissé à la main, symbole national de l'identité burkinabè."
        ),

        // --- 4. ANIMAUX DU FASO ---
        VisualMooreItem(
            id = 301,
            word = "Wéefo",
            pronunciation = "Wé-fo",
            category = "Animaux",
            frenchMeaning = "Cheval (Étalon du Faso)",
            localDrawable = R.drawable.img_burkinabe_horse_weefo_1786881763857,
            contextInfo = "L'Étalon noble, symbole de courage et de fierté nationale du Burkina Faso."
        ),
        VisualMooreItem(
            id = 302,
            word = "Bua",
            pronunciation = "Bou-a",
            category = "Animaux",
            frenchMeaning = "Chèvre",
            localDrawable = R.drawable.img_burkinabe_goat_1786881820618,
            contextInfo = "Chèvre paisible dans la cour d'un village burkinabè."
        ),

        // --- 5. NATURE & CHAMPS ---
        VisualMooreItem(
            id = 901,
            word = "Koodo",
            pronunciation = "Kou-do",
            category = "Nature",
            frenchMeaning = "Champ de sorgho / Mil",
            localDrawable = R.drawable.img_burkinabe_sorghum_field_1786881963412,
            contextInfo = "Champ de sorgho et mil doré au cœur des terroirs du Burkina Faso."
        )
    )

    val categories: List<String> = listOf(
        "Toutes",
        "Famille",
        "Aliments",
        "Animaux",
        "Vêtements",
        "Métiers",
        "Maison",
        "Nature"
    )
}
