package com.example.data.model

data class BurkinaPersonality(
    val id: Int,
    val name: String,
    val dateOrPeriod: String,
    val titleOrRole: String,
    val impactSummary: String,
    val detailedBiography: String,
    val historicalMilestone: String? = null,
    val category: String, // "Fondateurs & Rois", "Révolution & République", "Histoire & Liberté", "Arts & Cinéma", "Champions & Sports"
    val avatarEmoji: String,
    val imageUrl: String,
    val localDrawableResId: Int? = null,
    val imageSourceNote: String,
    val themeColorHex: Long,
    val keyQuoteOrMotto: String,
    val legacyPoints: List<String>
)

object BurkinaPersonalitiesData {

    val HISTORICAL_MILESTONES = listOf(
        MilestoneItem(1, "01 mars 1919", "Création de la colonie de Haute-Volta.", "Haute-Volta coloniale", "🏛️"),
        MilestoneItem(2, "05 septembre 1932", "Suppression de la Haute-Volta et partage de son territoire entre les colonies voisines.", "Démembrement territorial", "✂️"),
        MilestoneItem(3, "04 septembre 1947", "Rétablissement de la Haute-Volta.", "Reconstitution territoriale", "🔄"),
        MilestoneItem(4, "11 décembre 1958", "La Haute-Volta devient une République autonome au sein de la Communauté française.", "République autonome", "📜"),
        MilestoneItem(5, "05 août 1960", "Indépendance de la Haute-Volta.", "Maurice Yaméogo", "🇧🇫"),
        MilestoneItem(6, "11 décembre 1960", "Maurice Yaméogo devient officiellement le premier président de la République de Haute-Volta.", "Maurice Yaméogo", "👔"),
        MilestoneItem(7, "03 janvier 1966", "Soulèvement populaire et chute de Maurice Yaméogo.", "Sangoulé Lamizana", "✊🏾"),
        MilestoneItem(8, "14 juin 1970", "Adoption de la Constitution de la IIᵉ République.", "IIᵉ République", "📖"),
        MilestoneItem(9, "25 novembre 1980", "Coup d’État de Saye Zerbo contre Sangoulé Lamizana.", "Saye Zerbo (CMRPN)", "⭐"),
        MilestoneItem(10, "07 novembre 1982", "Coup d’État contre Saye Zerbo et arrivée de Jean-Baptiste Ouédraogo.", "Jean-Baptiste Ouédraogo (CSP)", "🩺"),
        MilestoneItem(11, "17 mai 1983", "Arrestation de Thomas Sankara, alors Premier ministre.", "Thomas Sankara", "🔒"),
        MilestoneItem(12, "04 août 1983", "Arrivée de Thomas Sankara au pouvoir et début de la Révolution burkinabè.", "Thomas Sankara (CNR)", "✊🏾"),
        MilestoneItem(13, "04 août 1984", "La Haute-Volta devient officiellement le Burkina Faso.", "Thomas Sankara", "⭐"),
        MilestoneItem(14, "25 décembre 1985", "Début de la « guerre de Noël » entre le Burkina Faso et le Mali.", "Conflit de l'Agacher", "⚔️"),
        MilestoneItem(15, "15 octobre 1987", "Assassinat de Thomas Sankara et arrivée de Blaise Compaoré au pouvoir.", "Blaise Compaoré", "🕊️"),
        MilestoneItem(16, "02 juin 1991", "Adoption de la Constitution de la IVᵉ République.", "IVᵉ République", "📜"),
        MilestoneItem(17, "01 décembre 1991", "Élection de Blaise Compaoré à la présidence.", "Blaise Compaoré", "🗳️"),
        MilestoneItem(18, "13 décembre 1998", "Assassinat du journaliste Norbert Zongo.", "Norbert Zongo", "📰"),
        MilestoneItem(19, "11 juin 2000", "Révision importante de la Constitution.", "Réforme Constitutionnelle", "⚖️"),
        MilestoneItem(20, "27 novembre 2005", "Réélection de Blaise Compaoré.", "Blaise Compaoré", "🗳️"),
        MilestoneItem(21, "21 novembre 2010", "Réélection de Blaise Compaoré.", "Blaise Compaoré", "🗳️"),
        MilestoneItem(22, "30 octobre 2014", "Insurrection populaire contre la modification de la Constitution.", "Insurrection populaire", "🔥"),
        MilestoneItem(23, "31 octobre 2014", "Démission de Blaise Compaoré.", "Chute du régime", "🚪"),
        MilestoneItem(24, "17 novembre 2014", "Michel Kafando devient président de la transition.", "Michel Kafando", "🏛️"),
        MilestoneItem(25, "17 septembre 2015", "Tentative de coup d’État du Régiment de sécurité présidentielle.", "RSP / Diendéré", "🛑"),
        MilestoneItem(26, "29 novembre 2015", "Élection de Roch Marc Christian Kaboré.", "Roch Marc Christian Kaboré", "🗳️"),
        MilestoneItem(27, "29 décembre 2015", "Investiture de Roch Marc Christian Kaboré.", "Roch Marc Christian Kaboré", "👔"),
        MilestoneItem(28, "29 décembre 2016", "Attaque contre une position militaire à Nassoumbou, marquant une nouvelle étape de la menace terroriste.", "Nassoumbou", "🛡️"),
        MilestoneItem(29, "23 mars 2019", "Attaque de Yirgou et importantes violences intercommunautaires.", "Yirgou", "⚠️"),
        MilestoneItem(30, "22 novembre 2020", "Réélection de Roch Marc Christian Kaboré.", "Roch Marc Christian Kaboré", "🗳️"),
        MilestoneItem(31, "24 janvier 2022", "Coup d’État contre Roch Marc Christian Kaboré.", "MPSR I", "🎖️"),
        MilestoneItem(32, "24 janvier 2022", "Paul-Henri Sandaogo Damiba devient président de la transition.", "Paul-Henri S. Damiba", "🎖️"),
        MilestoneItem(33, "30 septembre 2022", "Coup d’État d’Ibrahim Traoré contre Damiba.", "MPSR II", "🛡️"),
        MilestoneItem(34, "02 octobre 2022", "Ibrahim Traoré devient président de la transition.", "Capitaine Ibrahim Traoré", "⭐"),
        MilestoneItem(35, "28 janvier 2024", "Annonce du retrait du Burkina Faso, du Mali et du Niger de la CEDEAO.", "Alliance des États du Sahel (AES)", "🌍"),
        MilestoneItem(36, "25 mai 2024", "Adoption d’une nouvelle Charte de la Transition prolongeant la transition.", "Assises Nationales / Charte", "📜"),
        MilestoneItem(37, "29 janvier 2025", "Retrait officiel du Burkina Faso de la CEDEAO.", "Souveraineté nationale", "🇧🇫"),
        MilestoneItem(38, "29 janvier 2025", "Le Burkina Faso, le Mali et le Niger cessent officiellement d’être membres de la CEDEAO.", "Confédération AES", "🤝"),
        MilestoneItem(39, "11 décembre 2025", "Célébration de la fête nationale du Burkina Faso dans le contexte de la transition.", "Fête Nationale", "🎉"),
        MilestoneItem(40, "2026", "Poursuite de la transition politique sous la direction d’Ibrahim Traoré.", "Capitaine Ibrahim Traoré", "🚀")
    )

    val PERSONALITIES_LIST = listOf(
        // 1. Yennenga
        BurkinaPersonality(
            id = 1,
            name = "Yennenga",
            dateOrPeriod = "Vers le XIIᵉ siècle",
            titleOrRole = "Princesse légendaire & Fondatrice symbolique",
            impactSummary = "Princesse légendaire à l’origine du peuple mossi et symbole fondateur de la nation.",
            detailedBiography = "Dans la tradition orale mossi, Yennenga est une princesse guerrière, fille du roi Naba Neega de Gambaga. Son récit est lié à sa rencontre avec le chasseur Rialé et à la naissance d'Ouédraogo ('l'étalon'), ancêtre fondateur de la tradition royale mossi. Les dates exactes sont légendaires et constituent le mythe fondateur de la nation. L'Étalon de Yennenga est aujourd'hui le symbole suprême du FESPACO et des équipes nationales.",
            historicalMilestone = "XIIᵉ siècle : Naissance de la légende mossi",
            category = "Fondateurs & Rois",
            avatarEmoji = "👸🏾",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Yennenga_statue_Ouagadougou.jpg/640px-Yennenga_statue_Ouagadougou.jpg",
            localDrawableResId = null,
            imageSourceNote = "Statue & Portrait de la Princesse Yennenga",
            themeColorHex = 0xFFD81B60,
            keyQuoteOrMotto = "« Symbole du courage, de l'indépendance, du leadership féminin et de l'origine mythique de la nation. »",
            legacyPoints = listOf(
                "Mère mythique de la dynastie royale mossi",
                "Symbole suprême du FESPACO (Étalon d'Or)",
                "Figure universelle de l'émancipation féminine et du courage africain"
            )
        ),

        // 2. Naaba Oubri
        BurkinaPersonality(
            id = 2,
            name = "Naaba Oubri",
            dateOrPeriod = "Vers 1333–1358",
            titleOrRole = "Fondateur traditionnel de Ouagadougou",
            impactSummary = "Fondateur du royaume de Ouagadougou et unificateur des royaumes mossis.",
            detailedBiography = "Selon la tradition historique mossi, Oubri est le petit-fils d'Ouédraogo et fils de Zoungrana. Il est présenté comme le premier Mogho Naaba et comme le fondateur de Wogdogo (Ouagadougou). Les récits anciens reposent largement sur la tradition orale. Il fixa la capitale et bâtit les fondations politiques et coutumières durables du royaume.",
            historicalMilestone = "1333–1358 : Fondation de Ouagadougou",
            category = "Fondateurs & Rois",
            avatarEmoji = "👑",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Statue_de_Naaba_Koom_ou_Naaba_Oubri.jpg/640px-Statue_de_Naaba_Koom_ou_Naaba_Oubri.jpg",
            localDrawableResId = null,
            imageSourceNote = "Mausolée & Buste de Naaba Oubri",
            themeColorHex = 0xFF8E24AA,
            keyQuoteOrMotto = "« Fondation de Ouagadougou et construction de la légitimité politique de la dynastie mossi. »",
            legacyPoints = listOf(
                "Fondateur de la capitale historique Ouagadougou",
                "Premier souverain à porter le titre sacré de Mogho Naaba",
                "Bâtisseur des institutions coutumières et de l'unité territoriale"
            )
        ),

        // 3. Mogho Naaba Baongo
        BurkinaPersonality(
            id = 3,
            name = "Mogho Naaba Baongo",
            dateOrPeriod = "Depuis 1982",
            titleOrRole = "37ᵉ Empereur des Mossis & Gardien des Traditions",
            impactSummary = "Empereur des Mossis, médiateur dans les crises nationales et gardien des traditions.",
            detailedBiography = "Baongo II a été intronisé le 21 décembre 1982 et est le 37e Mogho Naaba. Figure majeure et autorité morale incontestée de la chefferie traditionnelle, il intervient activement dans la médiation sociale, la cohésion nationale et le règlement pacifique des crises majeures du pays, tout en perpétuant le cérémonial royal.",
            historicalMilestone = "Intronisé en 1982 : Rôle de médiation et paix sociale",
            category = "Fondateurs & Rois",
            avatarEmoji = "🏛️",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/Mogho_Naaba_Baongo_II.jpg/640px-Mogho_Naaba_Baongo_II.jpg",
            localDrawableResId = com.example.R.drawable.img_moogho_naaba_baongo,
            imageSourceNote = "Sa Majesté le Moogho Naaba Baongo — Palais Royal des Mossis",
            themeColorHex = 0xFF5E35B1,
            keyQuoteOrMotto = "« Préservation des traditions, rôle de médiation et contribution essentielle au dialogue social. »",
            legacyPoints = listOf(
                "Médiateur clé lors des transitions et crises sociopolitiques",
                "Préservation du patrimoine culturel et rituel du Mogho Naaba",
                "Promotion continue du dialogue inter-religieux et de la concorde"
            )
        ),

        // 4. Thomas Sankara
        BurkinaPersonality(
            id = 4,
            name = "Thomas Sankara",
            dateOrPeriod = "1983–1987",
            titleOrRole = "Père de la Révolution & Président du Faso",
            impactSummary = "Renomme la Haute-Volta en Burkina Faso (4 août 1984), lance la Révolution et lutte contre la corruption.",
            detailedBiography = "Le capitaine Thomas Sankara dirige le Burkina Faso de 1983 à 1987. Il renomme la Haute-Volta « Burkina Faso » (Pays des Hommes Intègres) le 4 août 1984. Il porte un programme révolutionnaire axé sur l’autosuffisance alimentaire ('Consommons ce que nous produisons'), la vaccination massive, l'émancipation des femmes, le Faso Dan Fani et l'intégrité publique.",
            historicalMilestone = "4 août 1984 : Naissance du nom Burkina Faso",
            category = "Révolution & République",
            avatarEmoji = "✊🏾",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a4/Thomas_Sankara_photo.png",
            localDrawableResId = com.example.R.drawable.img_thomas_sankara,
            imageSourceNote = "Capitaine Thomas Sankara — Père de la Révolution Burkinabè",
            themeColorHex = 0xFFC62828,
            keyQuoteOrMotto = "« Oser inventer l'avenir ! Transformation politique, souveraineté nationale et héritage panafricain. »",
            legacyPoints = listOf(
                "Création du nom Burkina Faso, de l'hymne Ditanyè et du drapeau",
                "Campagnes historiques de santé publique, d'écologie et d'alphabétisation",
                "Icône mondiale de l'intégrité, du féminisme et de l'indépendance africaine"
            )
        ),

        // 5. Maurice Yaméogo
        BurkinaPersonality(
            id = 5,
            name = "Maurice Yaméogo",
            dateOrPeriod = "1959–1966",
            titleOrRole = "Premier Président de la Haute-Volta indépendante",
            impactSummary = "Premier président après l’indépendance proclamée le 5 août 1960.",
            detailedBiography = "Maurice Yaméogo est le premier président de la République de Haute-Volta de 1959 à 1966. L’indépendance nationale est proclamée solennellement le 5 août 1960. Il a édifié les premières institutions républicaines et l'administration moderne de l'État voltaïque indépendant avant de quitter le pouvoir le 3 janvier 1966.",
            historicalMilestone = "5 août 1960 : Proclamation de l'Indépendance",
            category = "Révolution & République",
            avatarEmoji = "📜",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Maurice_Yam%C3%A9ogo_1961.jpg/640px-Maurice_Yam%C3%A9ogo_1961.jpg",
            localDrawableResId = com.example.R.drawable.img_maurice_yameogo,
            imageSourceNote = "Portrait officiel de Maurice Yaméogo",
            themeColorHex = 0xFF1565C0,
            keyQuoteOrMotto = "« Construction de l’État voltaïque indépendant et premières orientations diplomatiques. »",
            legacyPoints = listOf(
                "Signature et proclamation des actes d'indépendance de 1960",
                "Fondation de la diplomatie et des premiers ministères républicains",
                "Premier chef d'État de l'histoire moderne du pays"
            )
        ),

        // 6. Sangoulé Lamizana
        BurkinaPersonality(
            id = 6,
            name = "Sangoulé Lamizana",
            dateOrPeriod = "1966–1980",
            titleOrRole = "Général & Chef de l’État (14 ans de mandat)",
            impactSummary = "Président ayant dirigé le pays pendant 14 ans et modernisé l’administration.",
            detailedBiography = "Le général Aboubacar Sangoulé Lamizana devient chef de l’État le 3 janvier 1966 et dirige la Haute-Volta jusqu’au 25 novembre 1980. Il accompagne le passage à la IIe République, maintient la rigueur budgétaire et revient à la présidence après l’élection démocratique de 1978.",
            historicalMilestone = "1966–1980 : Longue stabilité et modernisation",
            category = "Révolution & République",
            avatarEmoji = "🎖️",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Aboubacar_Sangoul%C3%A9_Lamizana.jpg/640px-Aboubacar_Sangoul%C3%A9_Lamizana.jpg",
            localDrawableResId = com.example.R.drawable.img_sangoule_lamizana,
            imageSourceNote = "Général Aboubacar Sangoulé Lamizana",
            themeColorHex = 0xFF2E7D32,
            keyQuoteOrMotto = "« Longue période de stabilité institutionnelle, construction de l’armée et transitions démocratiques. »",
            legacyPoints = listOf(
                "Stabilité financière et gestion rigoureuse des finances de l'État",
                "Organisation des premières élections pluralistes présidentielles en 1978",
                "Grandes infrastructures de désenclavement et d'urbanisme"
            )
        ),

        // 7. Blaise Compaoré
        BurkinaPersonality(
            id = 7,
            name = "Blaise Compaoré",
            dateOrPeriod = "1987–2014",
            titleOrRole = "Chef de l’État (27 ans de présidence)",
            impactSummary = "Dirige le Burkina durant 27 ans jusqu’à l’insurrection populaire des 30–31 octobre 2014.",
            detailedBiography = "Blaise Compaoré prend le pouvoir le 15 octobre 1987 et reste président jusqu’au 31 octobre 2014, date de sa démission après le soulèvement populaire massif. Il instaure la IVe République en 1991 et positionne le Burkina Faso comme médiateur diplomatique central en Afrique de l'Ouest.",
            historicalMilestone = "30–31 oct. 2014 : Fin de règne & Insurrection populaire",
            category = "Révolution & République",
            avatarEmoji = "🏛️",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Blaise_Compaor%C3%A9_2008.jpg/640px-Blaise_Compaor%C3%A9_2008.jpg",
            localDrawableResId = null,
            imageSourceNote = "Portrait officiel de Blaise Compaoré",
            themeColorHex = 0xFF455A64,
            keyQuoteOrMotto = "« 27 années de pouvoir, médiations diplomatiques et transformation de la vie politique burkinabè. »",
            legacyPoints = listOf(
                "Adoption de la Constitution de la IVᵉ République de 1991",
                "Médiateur dans plusieurs crises politiques de la sous-région ouest-africaine",
                "Développement des grandes infrastructures de la capitale (Ouaga 2000)"
            )
        ),

        // 8. Norbert Zongo
        BurkinaPersonality(
            id = 8,
            name = "Norbert Zongo",
            dateOrPeriod = "1949–1998",
            titleOrRole = "Journaliste d’investigation & Martyr de la Presse",
            impactSummary = "Journaliste assassiné le 13 décembre 1998, devenu symbole de la justice et de la liberté de la presse.",
            detailedBiography = "Fondateur et directeur de l'hebdomadaire L’Indépendant et écrivain courageux, Norbert Zongo dénonçait les dérives autoritaires, l'injustice et la corruption. Son assassinat le 13 décembre 1998 avec trois compagnons à Sapouy est devenu un tournant majeur pour les droits humains et la liberté de la presse en Afrique.",
            historicalMilestone = "13 déc. 1998 : Symbole de la liberté de la presse",
            category = "Histoire & Liberté",
            avatarEmoji = "📰",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Norbert_Zongo_portrait.jpg/640px-Norbert_Zongo_portrait.jpg",
            localDrawableResId = com.example.R.drawable.img_norbert_zongo,
            imageSourceNote = "Portrait de Norbert Zongo — Martyr de la presse libre",
            themeColorHex = 0xFFE65100,
            keyQuoteOrMotto = "« Journalisme d’investigation, défense intransigeante de la liberté et mobilisation citoyenne. »",
            legacyPoints = listOf(
                "L'Université de Koudougou baptisée Université Norbert Zongo",
                "Création du Centre National de Presse Norbert Zongo (CNP-NZ)",
                "Modèle absolu du journalisme libre et engagé pour la vérité"
            )
        ),

        // 9. Joseph Ki-Zerbo
        BurkinaPersonality(
            id = 9,
            name = "Joseph Ki-Zerbo",
            dateOrPeriod = "1922–2006",
            titleOrRole = "Éminent Historien, Penseur & Panafricaniste",
            impactSummary = "Historien, auteur et défenseur de l’éducation africaine durant plus de 50 ans.",
            detailedBiography = "Né à Toma en 1922, le Professeur Joseph Ki-Zerbo est le premier Africain agrégé d’histoire en 1956. Directeur du volume I de l'Histoire générale de l'Afrique de l'UNESCO, il a forgé la pensée du développement endogène ('On ne développe pas, on se développe') et consacré sa vie à l'éducation africaine enracinée.",
            historicalMilestone = "1950–2006 : Direction de l'Histoire générale de l'Afrique",
            category = "Histoire & Liberté",
            avatarEmoji = "📚",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Joseph_Ki-Zerbo_1997.jpg/640px-Joseph_Ki-Zerbo_1997.jpg",
            localDrawableResId = com.example.R.drawable.img_joseph_ki_zerbo,
            imageSourceNote = "Professeur Joseph Ki-Zerbo — Éminent historien africain",
            themeColorHex = 0xFF00796B,
            keyQuoteOrMotto = "« Nan lara, an sara ! (Si nous nous couchons, nous sommes morts !) »",
            legacyPoints = listOf(
                "Coordination de l'Histoire Générale de l'Afrique (UNESCO)",
                "L'Université Joseph Ki-Zerbo de Ouagadougou porte son nom",
                "Penseur de renommée mondiale de l'éducation et de l'auto-développement"
            )
        ),

        // 10. Nazi Boni
        BurkinaPersonality(
            id = 10,
            name = "Nazi Boni",
            dateOrPeriod = "1909–1969 (1962)",
            titleOrRole = "Écrivain pionnier & Homme d'État",
            impactSummary = "Publie Crépuscule des temps anciens (1962), une œuvre majeure de la littérature africaine.",
            detailedBiography = "Député et président de l'Assemblée de Haute-Volta, Nazi Boni a lutté pour la reconstitution du territoire voltaïque. En 1962, il publie son chef-d'œuvre littéraire 'Crépuscule des temps anciens', immortalisant les traditions, valeurs guerrières et la culture du peuple Bwaba. L'Université de Bobo-Dioulasso porte son nom.",
            historicalMilestone = "1962 : Publication de Crépuscule des temps anciens",
            category = "Histoire & Liberté",
            avatarEmoji = "🖋️",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Nazi_Boni.jpg/640px-Nazi_Boni.jpg",
            localDrawableResId = null,
            imageSourceNote = "Portrait historique de Nazi Boni",
            themeColorHex = 0xFF6D4C41,
            keyQuoteOrMotto = "« Littérature fondatrice, nationalisme, dignité culturelle et unité africaine. »",
            legacyPoints = listOf(
                "Auteur du roman classique 'Crépuscule des temps anciens'",
                "L'Université Nazi Boni de Bobo-Dioulasso lui rend hommage",
                "Défenseur historique de la réunification du territoire de la Haute-Volta"
            )
        ),

        // 11. Jean-Baptiste Ouédraogo
        BurkinaPersonality(
            id = 11,
            name = "Jean-Baptiste Ouédraogo",
            dateOrPeriod = "1982–1983",
            titleOrRole = "Médecin-commandant & Chef de l’État (CSP)",
            impactSummary = "Président de transition avant l’arrivée de Thomas Sankara.",
            detailedBiography = "Médecin-commandant spécialiste en pédiatrie, Jean-Baptiste Ouédraogo devient chef de l’État le 7 novembre 1982. Il nomme Thomas Sankara Premier ministre en janvier 1983. Après sa présidence, il fonde la clinique Notre Dame de la Paix et s'illustre comme sage et médiateur respecté des crises politiques nationales.",
            historicalMilestone = "1982–1983 : Transition du Conseil de Salut du Peuple",
            category = "Révolution & République",
            avatarEmoji = "🩺",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Jean-Baptiste_Ou%C3%A9draogo.jpg/640px-Jean-Baptiste_Ou%C3%A9draogo.jpg",
            localDrawableResId = null,
            imageSourceNote = "Portrait de Jean-Baptiste Ouédraogo",
            themeColorHex = 0xFF0288D1,
            keyQuoteOrMotto = "« Rôle de transition crucial dans l'histoire politique et engagement médical au sommet. »",
            legacyPoints = listOf(
                "Direction de la transition politique majeure de 1982-1983",
                "Pédiatre pionnier et fondateur d'infrastructures de santé",
                "Médiateur d'honneur lors des concertations nationales"
            )
        ),

        // 12. Saye Zerbo
        BurkinaPersonality(
            id = 12,
            name = "Saye Zerbo",
            dateOrPeriod = "1980–1982",
            titleOrRole = "Colonel & Chef de l’État (CMRPN)",
            impactSummary = "Chef de l’État après le coup d’État militaire de novembre 1980.",
            detailedBiography = "Le colonel Saye Zerbo prend la tête du Comité Militaire de Redressement pour le Progrès National (CMRPN) le 25 novembre 1980 après avoir renversé Sangoulé Lamizana. Il instaure une gestion axée sur la discipline et le travail agricole avant la prise de pouvoir par le CSP en novembre 1982.",
            historicalMilestone = "1980–1982 : Gouvernement du CMRPN",
            category = "Révolution & République",
            avatarEmoji = "⭐",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Saye_Zerbo.jpg/640px-Saye_Zerbo.jpg",
            localDrawableResId = com.example.R.drawable.img_saye_zerbo,
            imageSourceNote = "Colonel Saye Zerbo",
            themeColorHex = 0xFF5D4037,
            keyQuoteOrMotto = "« Étape importante dans la succession des gouvernements précédant la Révolution de 1983. »",
            legacyPoints = listOf(
                "Campagnes de rigueur morale et de restructuration de l'administration",
                "Priorité accordée au monde rural et aux filières vivrières",
                "Ancien ministre des Affaires étrangères et haut gradé militaire"
            )
        ),

        // 13. Roch Marc Christian Kaboré
        BurkinaPersonality(
            id = 13,
            name = "Roch Marc Christian Kaboré",
            dateOrPeriod = "2015–2022",
            titleOrRole = "Président élu de la République",
            impactSummary = "Premier président élu démocratiquement après l’insurrection populaire.",
            detailedBiography = "Roch Marc Christian Kaboré est élu président de la République en novembre 2015 à la suite de la transition post-insurrectionnelle, puis réélu en 2020. Son mandat est marqué par le Plan National de Développement Économique et Social (PNDES), la gratuité des soins de santé maternelle et infantile et les défis sécuritaires.",
            historicalMilestone = "2015 : Première présidence post-insurrectionnelle",
            category = "Révolution & République",
            avatarEmoji = "👔",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Roch_Marc_Christian_Kabor%C3%A9_2016.jpg/640px-Roch_Marc_Christian_Kabor%C3%A9_2016.jpg",
            localDrawableResId = null,
            imageSourceNote = "Portrait officiel de Roch Marc Christian Kaboré",
            themeColorHex = 0xFF1976D2,
            keyQuoteOrMotto = "« Retour à une présidence civile élue démocratiquement après l'insurrection populaire. »",
            legacyPoints = listOf(
                "Gratuité des soins pour les femmes enceintes et les enfants de moins de 5 ans",
                "Construction de grands échangeurs routiers et de la centrale solaire de Zagtouli",
                "Consolidation des institutions démocratiques républicaines"
            )
        ),

        // 14. Ibrahim Traoré
        BurkinaPersonality(
            id = 14,
            name = "Ibrahim Traoré",
            dateOrPeriod = "Depuis 2022",
            titleOrRole = "Capitaine & Président du Faso (Chef de l'État)",
            impactSummary = "Président de la Transition et acteur majeur de la reconquête sécuritaire et de la souveraineté.",
            detailedBiography = "Le capitaine Ibrahim Traoré prend la tête du pays le 30 septembre 2022 et prête serment comme Président du Faso. Figure centrale de la transition actuelle, il impulse une politique de souveraineté nationale, de mobilisation patriotique (VDP), la création de la Confédération de l'AES (Burkina, Mali, Niger), l'Offensive Agro-Pastorale et la première raffinerie d'or nationale.",
            historicalMilestone = "Depuis 2022 : Reconquête territoriale, AES & Souveraineté",
            category = "Révolution & République",
            avatarEmoji = "🛡️",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/8/88/Ibrahim_Traor%C3%A9_portrait.jpg",
            localDrawableResId = null,
            imageSourceNote = "Portrait officiel du Capitaine Ibrahim Traoré — Source : Présidence du Faso",
            themeColorHex = 0xFFB71C1C,
            keyQuoteOrMotto = "« La patrie ou la mort, nous vaincrons ! Souveraineté, sécurité et transformation de l’État. »",
            legacyPoints = listOf(
                "Fondation et présidence de la Confédération des États du Sahel (AES)",
                "Mobilisation générale citoyenne et reconquête sécuritaire du territoire",
                "Inauguration de la première Raffinerie Nationale d'Or et des usines textiles"
            )
        ),

        // 15. Philippe Zinda Kaboré
        BurkinaPersonality(
            id = 15,
            name = "Philippe Zinda Kaboré",
            dateOrPeriod = "1920–1947 (Mémoire 1961)",
            titleOrRole = "Pionnier de la lutte anticoloniale & Député historique",
            impactSummary = "Député pionnier dont la mémoire a inspiré la révolte scolaire historique du 20 mai 1961.",
            detailedBiography = "Premier ingénieur voltaïque et député à l'Assemblée constituante française en 1946, Philippe Zinda Kaboré a mené avec bravoure le combat pour la reconstitution de la Haute-Volta dissoute. Sa disparition précoce en 1947 a marqué les esprits. Le 20 mai 1961, une révolte scolaire historique éclate en son nom. Le Lycée Philippe Zinda Kaboré perpétue sa mémoire.",
            historicalMilestone = "1947 / 20 mai 1961 : Combat territorial & Révolte scolaire",
            category = "Histoire & Liberté",
            avatarEmoji = "🎓",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Philippe_Zinda_Kabor%C3%A9.jpg/640px-Philippe_Zinda_Kabor%C3%A9.jpg",
            localDrawableResId = com.example.R.drawable.img_philippe_zinda_kabore,
            imageSourceNote = "Portrait historique de Philippe Zinda Kaboré",
            themeColorHex = 0xFF7B1FA2,
            keyQuoteOrMotto = "« Engagement politique précoce, lutte anticoloniale et dignité démocratique. »",
            legacyPoints = listOf(
                "Action parlementaire décisive pour la renaissance territoriale du pays en 1947",
                "Le Lycée Philippe Zinda Kaboré, plus grand établissement d'enseignement du pays",
                "Symbole d'excellence intellectuelle et de patriotisme juvénile"
            )
        ),

        // 16. Idrissa Ouédraogo
        BurkinaPersonality(
            id = 16,
            name = "Idrissa Ouédraogo",
            dateOrPeriod = "1954–2018 (1990)",
            titleOrRole = "Cinéaste de légende & Lauréat à Cannes",
            impactSummary = "Son film Tilaï remporte le Grand Prix du Jury au Festival de Cannes (1990).",
            detailedBiography = "Né à Banfora, Idrissa Ouédraogo est l’un des réalisateurs africains les plus reconnus au monde. Après le triomphe de 'Yaaba' en 1989, son chef-d'œuvre 'Tilaï' remporte le Grand Prix du Jury à Cannes en 1990 et l’Étalon de Yennenga au FESPACO 1991. Il a fait rayonner le 7ᵉ art burkinabè sur la scène mondiale.",
            historicalMilestone = "1990 : Grand Prix du Jury à Cannes (Tilaï)",
            category = "Arts & Cinéma",
            avatarEmoji = "🎬",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Idrissa_Ouedraogo_Cannes.jpg/640px-Idrissa_Ouedraogo_Cannes.jpg",
            localDrawableResId = com.example.R.drawable.img_idrissa_ouedraogo,
            imageSourceNote = "Idrissa Ouédraogo — Grand Prix du Jury à Cannes",
            themeColorHex = 0xFFC2185B,
            keyQuoteOrMotto = "« Rayonnement international du cinéma burkinabè et contribution majeure au FESPACO. »",
            legacyPoints = listOf(
                "Grand Prix du Jury au Festival de Cannes (Tilaï, 1990)",
                "Étalon d'Or de Yennenga au FESPACO 1991",
                "Inspiration majeure pour toutes les générations du cinéma africain"
            )
        ),

        // 17. Gaston Kaboré
        BurkinaPersonality(
            id = 17,
            name = "Gaston Kaboré",
            dateOrPeriod = "Né en 1951 (1989)",
            titleOrRole = "Maître du Cinéma Africain & Fondateur d'IMAGINE",
            impactSummary = "Réalisateur de Wend Kuuni, œuvre emblématique du cinéma africain.",
            detailedBiography = "Né à Bobo-Dioulasso, Gaston Kaboré réalise 'Wend Kuuni' (1982), qui reçoit le César du meilleur film francophone en 1985, puis 'Buud Yam' (1997), couronné par l’Étalon de Yennenga au FESPACO. Fondateur de l'institut supérieur de formation cinématographique IMAGINE à Ouagadougou, il est un éducateur et passeur d'images hors pair.",
            historicalMilestone = "1989 / 1997 : Wend Kuuni & Étalon d'Or Buud Yam",
            category = "Arts & Cinéma",
            avatarEmoji = "🎥",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/Gaston_Kabor%C3%A9_2015.jpg/640px-Gaston_Kabor%C3%A9_2015.jpg",
            localDrawableResId = com.example.R.drawable.img_gaston_kabore,
            imageSourceNote = "Gaston Kaboré — Réalisateur & Maître du 7ᵉ art",
            themeColorHex = 0xFF303F9F,
            keyQuoteOrMotto = "« Développement du cinéma, formation des talents et transmission de la mémoire africaine. »",
            legacyPoints = listOf(
                "César du meilleur film francophone (Wend Kuuni)",
                "Étalon de Yennenga au FESPACO (Buud Yam)",
                "Fondation de l'Institut Supérieur de Cinéma IMAGINE à Ouagadougou"
            )
        ),

        // 18. Hugues Fabrice Zango
        BurkinaPersonality(
            id = 18,
            name = "Hugues Fabrice Zango",
            dateOrPeriod = "2021–2024 (2023)",
            titleOrRole = "Champion du Monde & Recordman du Triple Saut",
            impactSummary = "Premier Burkinabè champion du monde d’athlétisme (triple saut).",
            detailedBiography = "Né en 1993 à Ouagadougou et docteur en génie électrique, Hugues Fabrice Zango est sacré Champion du Monde du triple saut à Budapest en 2023 et Champion du Monde en salle en 2024. Il a également offert au Burkina Faso la toute première médaille olympique de son histoire (bronze à Tokyo 2021) et détient le record du monde en salle (18,07 m).",
            historicalMilestone = "2023 : Premier titre de Champion du Monde d'Athlétisme",
            category = "Champions & Sports",
            avatarEmoji = "🥇",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Hugues_Fabrice_Zango_Tokyo_2020.jpg/640px-Hugues_Fabrice_Zango_Tokyo_2020.jpg",
            localDrawableResId = com.example.R.drawable.img_hugues_fabrice_zongo,
            imageSourceNote = "Hugues Fabrice Zango — Champion du Monde d'Athlétisme",
            themeColorHex = 0xFFF57F17,
            keyQuoteOrMotto = "« Première médaille olympique du Burkina Faso et premier titre mondial d’athlétisme. »",
            legacyPoints = listOf(
                "Champion du Monde d'Athlétisme Budapest 2023",
                "Première médaille olympique de l'histoire du Faso (Tokyo 2020/2021)",
                "Recordman du Monde en salle (18,07 m)"
            )
        ),

        // 19. Jonathan Pitroipa
        BurkinaPersonality(
            id = 19,
            name = "Jonathan Pitroipa",
            dateOrPeriod = "2013",
            titleOrRole = "Meilleur Joueur de la CAN 2013 & Légende des Étalons",
            impactSummary = "Conduit les Étalons à leur première finale de Coupe d’Afrique des Nations.",
            detailedBiography = "Jonathan Pitroipa est l’artisan majeur de l'épopée des Étalons qui atteignent la grande finale de la CAN 2013 en Afrique du Sud. Élu Meilleur Joueur de la compétition (MVP), il a fait vibrer tout le peuple burkinabè et incarné le talent et la bravoure des footballeurs nationaux sur les pelouses internationales.",
            historicalMilestone = "2013 : Finale de la CAN & Meilleur Joueur d'Afrique",
            category = "Champions & Sports",
            avatarEmoji = "⚽",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/Jonathan_Pitroipa_2012.jpg/640px-Jonathan_Pitroipa_2012.jpg",
            localDrawableResId = com.example.R.drawable.img_jonathan_pitroipa,
            imageSourceNote = "Jonathan Pitroipa — Meilleur Joueur de la CAN 2013",
            themeColorHex = 0xFF2E7D32,
            keyQuoteOrMotto = "« Performance historique des Étalons et rayonnement mondial du football burkinabè. »",
            legacyPoints = listOf(
                "Meilleur Joueur (MVP) de la Coupe d'Afrique des Nations 2013",
                "Vice-champion d'Afrique des Nations avec la sélection nationale",
                "Créateur de structures et académies de formation pour jeunes talents"
            )
        ),

        // 20. Iron Biby (Cheick Ahmed Al-Hassan Sanou)
        BurkinaPersonality(
            id = 20,
            name = "Iron Biby (Cheick Sanou)",
            dateOrPeriod = "2018–2024+",
            titleOrRole = "Champion du Monde de Log Lift & Recordman Absolu (231 kg)",
            impactSummary = "Multiple champion et recordman du monde du Log Lift (231 kg, 7 sept. 2024), ambassadeur sportif du Faso.",
            detailedBiography = "Cheick Ahmed Al-Hassan Sanou, dit Iron Biby, est un strongman burkinabè originaire de Bobo-Dioulasso. Le 7 septembre 2024 à Birmingham, il soulève 231 kg au log lift et bat son propre record du monde, décrochant son 6e titre mondial consécutif. Fierté nationale, il porte le drapeau burkinabè au sommet de la force mondiale.",
            historicalMilestone = "7 sept. 2024 : Record du Monde mondial de Log Lift (231 kg)",
            category = "Champions & Sports",
            avatarEmoji = "🏋🏾",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Iron_Biby_Cheick_Sanou.jpg/640px-Iron_Biby_Cheick_Sanou.jpg",
            localDrawableResId = com.example.R.drawable.img_iron_biby,
            imageSourceNote = "Iron Biby — Sextuple Champion du Monde & Recordman de Log Lift (231 kg)",
            themeColorHex = 0xFFD84315,
            keyQuoteOrMotto = "« Visibilité internationale exceptionnelle et record du monde mondial de force pure (231 kg). »",
            legacyPoints = listOf(
                "Recordman du Monde absolu de Log Lift (231 kg à Birmingham)",
                "Sextuple Champion du Monde des Strongmen",
                "Modèle de travail, de discipline et de générosité patriotique"
            )
        )
    )
}

data class MilestoneItem(
    val index: Int = 0,
    val date: String,
    val event: String,
    val personality: String,
    val icon: String
)
