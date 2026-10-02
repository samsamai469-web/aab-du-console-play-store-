package com.example.data.local

import com.example.R

data class VisualMooreItem(
    val id: Long,
    val word: String, // Mooré word / expression
    val pronunciation: String, // Phonetic guide
    val category: String, // "Expressions", "Famille", "Personnes", "Aliments", "Animaux", "Vêtements", "Commerce", "Transport", "Salutations", "Métiers", "Maison", "Nature", "Éducation"
    val imageUrl: String = "",
    val localDrawable: Int? = null,
    val frenchMeaning: String = "", // Used strictly for internal indexing & search, NEVER displayed in carousel
    val contextInfo: String = "Burkina Faso",
    val status: String = "validated"
)

object BurkinaVisualData {
    val visualCatalog: List<VisualMooreItem> = listOf(
        // --- 1. FAMILLE & PERSONNES BURKINABÈ ---
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
            localDrawable = R.drawable.img_laafi_bala_village_1786881210261,
            contextInfo = "Famille burkinabè unie et chaleureuse."
        ),
        VisualMooreItem(
            id = 105,
            word = "Paga",
            pronunciation = "Pa-ga",
            category = "Personnes",
            frenchMeaning = "Femme",
            localDrawable = R.drawable.img_burkinabe_beautiful_woman_1786881979996,
            contextInfo = "Femme burkinabè rayonnante d'élégance."
        ),
        VisualMooreItem(
            id = 107,
            word = "Rawa",
            pronunciation = "Ra-wa",
            category = "Personnes",
            frenchMeaning = "Homme",
            localDrawable = R.drawable.img_burkinabe_handsome_man_1786881994077,
            contextInfo = "Homme burkinabè en tunique traditionnelle."
        ),

        // --- 3. ALIMENTS & VIE QUOTIDIENNE ---
        VisualMooreItem(
            id = 701,
            word = "Koom",
            pronunciation = "Koum",
            category = "Aliments",
            frenchMeaning = "Eau (Source de vie)",
            localDrawable = R.drawable.img_laafi_bala_village_1786881210261,
            contextInfo = "Eau fraîche et pure, première boisson offerte à chaque invité au Faso."
        ),
        VisualMooreItem(
            id = 702,
            word = "Rii",
            pronunciation = "Rii",
            category = "Aliments",
            frenchMeaning = "Manger / Repas",
            localDrawable = R.drawable.img_burkinabe_sagbo_1786953068024,
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
            id = 704,
            word = "Manga",
            pronunciation = "Man-ga",
            category = "Aliments",
            frenchMeaning = "Mangue",
            localDrawable = R.drawable.img_home_banner_1_1786814284466,
            contextInfo = "Mangues fraîches et juteuses, fierté fruitière du Burkina Faso."
        ),
        VisualMooreItem(
            id = 705,
            word = "Gõse",
            pronunciation = "Gonn-sé",
            category = "Nature",
            frenchMeaning = "Dormir / Se reposer",
            localDrawable = R.drawable.img_laafi_bala_village_1786881210261,
            contextInfo = "Moment de repos paisible et réparateur."
        ),

        // --- 4. COMMERCE, LIEUX & ÉDUCATION ---
        VisualMooreItem(
            id = 501,
            word = "Yaar",
            pronunciation = "Ya-ar",
            category = "Commerce",
            frenchMeaning = "Marché",
            localDrawable = R.drawable.img_home_banner_2_1786814297595,
            contextInfo = "Le marché burkinabè vibrant avec ses étals colorés et conviviaux."
        ),
        VisualMooreItem(
            id = 502,
            word = "Yiri",
            pronunciation = "Yi-ri",
            category = "Maison",
            frenchMeaning = "Maison / Concession",
            localDrawable = R.drawable.img_laafi_bala_village_1786881210261,
            contextInfo = "Maison traditionnelle en terre et concession familiale au Burkina Faso."
        ),
        VisualMooreItem(
            id = 503,
            word = "Lekoll",
            pronunciation = "Lé-koll",
            category = "Éducation",
            frenchMeaning = "École",
            localDrawable = R.drawable.img_burkinabe_school_1786881851073,
            contextInfo = "Écoliers burkinabè apprenant avec enthousiasme dans leur cour d'école."
        ),

        // --- 5. MÉTIERS & CULTURE ---
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

        // --- 6. TRANSPORTS DU FASO ---
        VisualMooreItem(
            id = 601,
            word = "Monteur",
            pronunciation = "Mon-teur",
            category = "Transport",
            frenchMeaning = "Moto",
            localDrawable = R.drawable.img_burkinabe_moto_1786879096886,
            contextInfo = "La moto, moyen de déplacement emblématique des rues de Ouagadougou."
        ),
        VisualMooreItem(
            id = 602,
            word = "Kɛkɛ",
            pronunciation = "Kè-kè",
            category = "Transport",
            frenchMeaning = "Bicyclette / Vélo",
            localDrawable = R.drawable.img_burkinabe_bicycle_1786881807069,
            contextInfo = "Vélo pour les déplacements du quotidien au Burkina Faso."
        ),

        // --- 7. ANIMAUX DU FASO ---
        VisualMooreItem(
            id = 301,
            word = "Wéefo",
            pronunciation = "Wé-fo",
            category = "Animaux",
            frenchMeaning = "Cheval (Étalon du Faso)",
            localDrawable = R.drawable.img_burkinabe_donkey_1786881836280,
            contextInfo = "L'Étalon noble, symbole de courage et de fierté nationale du Burkina Faso."
        ),
        VisualMooreItem(
            id = 302,
            word = "Bua",
            pronunciation = "Bou-a",
            category = "Animaux",
            frenchMeaning = "Chèvre",
            localDrawable = R.drawable.img_burkinabe_donkey_1786881836280,
            contextInfo = "Chèvre paisible dans la cour d'un village burkinabè."
        ),
        VisualMooreItem(
            id = 303,
            word = "Bõnga",
            pronunciation = "Bonn-ga",
            category = "Animaux",
            frenchMeaning = "Âne",
            localDrawable = R.drawable.img_burkinabe_donkey_1786881836280,
            contextInfo = "Âne de bât dans les paysages sahéliens du Burkina."
        ),

        // --- 8. NATURE & CHAMPS ---
        VisualMooreItem(
            id = 901,
            word = "Koodo",
            pronunciation = "Kou-do",
            category = "Nature",
            frenchMeaning = "Champ de sorgho / Mil",
            localDrawable = R.drawable.img_burkinabe_farmer_1786879067110,
            contextInfo = "Champ de sorgho et mil doré au cœur des terroirs du Burkina Faso."
        ),

        // --- 9. SALUTATIONS CHALEUREUSES ---
        VisualMooreItem(
            id = 801,
            word = "Nii yi beogo",
            pronunciation = "Nii yi beo-go",
            category = "Salutations",
            frenchMeaning = "Bonjour (Salutation du matin)",
            localDrawable = R.drawable.img_burkinabe_greeting_hands_1786881917214,
            contextInfo = "Salutation conviviale et respectueuse du matin au Burkina Faso."
        ),
        VisualMooreItem(
            id = 802,
            word = "Nii zabre",
            pronunciation = "Nii za-bre",
            category = "Salutations",
            frenchMeaning = "Bonsoir (Salutation du soir)",
            localDrawable = R.drawable.bonsoir_2,
            contextInfo = "Salutation chaleureuse en fin de journée au coucher du soleil."
        )
    )

    val categories: List<String> = listOf(
        "Toutes",
        "Expressions",
        "Famille",
        "Personnes",
        "Aliments",
        "Animaux",
        "Vêtements",
        "Commerce",
        "Transport",
        "Salutations",
        "Métiers",
        "Maison",
        "Éducation",
        "Nature"
    )
}
