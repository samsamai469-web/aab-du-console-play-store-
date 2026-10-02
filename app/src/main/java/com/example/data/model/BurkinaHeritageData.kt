package com.example.data.model

data class HeritageItem(
    val id: Int,
    val emoji: String,
    val name: String,
    val category: String,
    val locationOrDetail: String = "",
    val description: String = ""
)

data class HeritageCategory(
    val title: String,
    val emoji: String,
    val colorHex: Long,
    val items: List<HeritageItem>
)

object BurkinaHeritageData {

    val categories: List<HeritageCategory> = listOf(
        HeritageCategory(
            title = "Patrimoines historiques et culturels",
            emoji = "🏛️",
            colorHex = 0xFFD97706, // Ambre / Ocre
            items = listOf(
                HeritageItem(1, "🏰", "Cour royale de Tiébélé", "Patrimoines historiques et culturels", "Province du Nahouri (Pô)", "Chef-d'œuvre de l'architecture kasséna en terre cuite peinte par les femmes, inscrit au patrimoine mondial de l'UNESCO."),
                HeritageItem(2, "🏺", "Ruines de Loropéni", "Patrimoines historiques et culturels", "Région du Sud-Ouest (Poni)", "Fortification millénaire en pierre témoin du commerce de l'or transsaharien, premier site burkinabè classé UNESCO."),
                HeritageItem(3, "🔥", "Sites de métallurgie ancienne du fer du Burkina Faso", "Patrimoines historiques et culturels", "Tiwêga, Yamané, Békuy, Douroula, Kindibo", "Hauts fourneaux séculaires témoignant de la maîtrise de la réduction du fer depuis plus de 2800 ans, inscrits à l'UNESCO."),
                HeritageItem(4, "🪨", "Art rupestre au Burkina Faso", "Patrimoines historiques et culturels", "Pobé-Mengao, Markoye, Kawara", "Gravures et peintures préhistoriques sur roche illustrant la faune, les cavaliers et symboles ancestraux."),
                HeritageItem(5, "⚰️", "Nécropoles de Bourzanga", "Patrimoines historiques et culturels", "Province du Bam (Centre-Nord)", "Stèles funéraires anthropomorphes et poteries funéraires médiévales de la civilisation Kurumba."),
                HeritageItem(6, "🏘️", "Sya, centre historique de Bobo-Dioulasso", "Patrimoines historiques et culturels", "Bobo-Dioulasso (Hauts-Bassins)", "Cœur historique fondé au XVe siècle, avec ses ruelles sinueuses, maisons en banco et la rivière sacrée du marigot Houet."),
                HeritageItem(7, "🏘️", "Village architectural de Koumi", "Patrimoines historiques et culturels", "À 15 km de Bobo-Dioulasso", "Village troglodytique et traditionnel Bwaba aux maisons étagées en terrasse et structures fortifiées."),
                HeritageItem(8, "🕌", "Mosquées du style soudanais au Burkina Faso", "Patrimoines historiques et culturels", "Bani, Bobo-Dioulasso, Doulgou, Kongoussi", "Édifices religieux en terre crue, contreforts à piliers et minarets caractéristiques de l'architecture sahélienne."),
                HeritageItem(9, "🏛️", "Maison du Peuple", "Patrimoines historiques et culturels", "Centre-ville de Ouagadougou", "Joyau architectural monumental orné de sculptures de masques traditionnels géants, haut-lieu de spectacles."),
                HeritageItem(10, "🏛️", "Musée national du Burkina Faso", "Patrimoines historiques et culturels", "Ouagadougou (Dassasgho)", "Conservatoire national préservant les masques rituels, costumes d'apparat, statuaires et traditions de plus de 60 ethnies."),
                HeritageItem(11, "⛪", "Cathédrale de l’Immaculée-Conception de Ouagadougou", "Patrimoines historiques et culturels", "Centre de Ouagadougou", "Édifiée en briques de terre rouge dans les années 1930, l'une des plus grandes basiliques en terre d'Afrique de l'Ouest."),
                HeritageItem(12, "⛪", "Sanctuaire Notre-Dame de Yagma", "Patrimoines historiques et culturels", "Yagma (Sortie nord de Ouagadougou)", "Grand lieu de pèlerinage national et sous-régional, basilique moderne et collines de recueillement.")
            )
        ),
        HeritageCategory(
            title = "Forêts classées et aires protégées",
            emoji = "🌳",
            colorHex = 0xFF15803D, // Vert forêt
            items = listOf(
                HeritageItem(13, "🌳", "Forêt classée de la Mare aux Hippopotames", "Forêts classées et aires protégées", "Bala (Hauts-Bassins)", "Réserve de biosphère UNESCO abritant une colonie importante d'hippopotames sacrés et une riche avifaune."),
                HeritageItem(14, "🐘", "Parc national d’Arly", "Forêts classées et aires protégées", "Province de la Tapoa (Est)", "Sanctuaire de grands mammifères (éléphants, lions, buffles, léopards) inscrit au patrimoine mondial W-Arly-Pendjari."),
                HeritageItem(15, "🐘", "Parc national du W", "Forêts classées et aires protégées", "Frontière Bénin-Niger-Burkina Faso", "Vaste parc transfrontalier le long des méandres du fleuve Niger en forme de 'W', classé UNESCO."),
                HeritageItem(16, "🌳", "Forêt classée de Kou", "Forêts classées et aires protégées", "Nasso (Proche de Bobo-Dioulasso)", "Forêt-galerie humide célèbre pour la source naturelle de Guinguette et ses arbres géants centenaires."),
                HeritageItem(17, "🌳", "Forêt classée de Dindéresso", "Forêts classées et aires protégées", "Hauts-Bassins (Bobo-Dioulasso)", "Poumon vert et station forestière de recherche, sentiers écologiques et riche biodiversité."),
                HeritageItem(18, "🌳", "Forêt classée de Kua", "Forêts classées et aires protégées", "Bobo-Dioulasso", "Réserve forestière historique protégeant le microclimat et la nappe phréatique de la région."),
                HeritageItem(19, "🌳", "Forêt classée de Niangoloko", "Forêts classées et aires protégées", "Cascades (Frontière Sud)", "Forêt tropicale de transition aux essences végétales denses bordant la région des Cascades."),
                HeritageItem(20, "🌳", "Forêt classée de Nazinga", "Forêts classées et aires protégées", "Centre-Sud (Pô)", "Complexe écologique modèle pour la conservation durable de la faune et la sylviculture sahélienne."),
                HeritageItem(21, "🦌", "Ranch de gibier de Nazinga", "Forêts classées et aires protégées", "Province du Nahouri", "Site pionnier de safari écotouristique où les troupeaux d'éléphants s'abreuvent aux étangs proches du campement."),
                HeritageItem(22, "🐘", "Réserve totale de faune d’Arly", "Forêts classées et aires protégées", "Région de l'Est", "Zone intégrale de protection stricte servant de refuge vital aux espèces menacées de la savane ouest-africaine."),
                HeritageItem(23, "🦛", "Mare aux Hippopotames", "Forêts classées et aires protégées", "Satiri (Région des Hauts-Bassins)", "Étang naturel d'eau douce vénéré par les populations locales pour ses vertus spirituelles et écologiques."),
                HeritageItem(24, "🌿", "Réserve partielle de faune du Sahel", "Forêts classées et aires protégées", "Oudalan, Soum, Séno", "La plus vaste réserve faunique du pays, refuge de la faune saharienne : gazelles dorcas, autruches et outardes."),
                HeritageItem(25, "🐘", "Réserve de faune de Singou", "Forêts classées et aires protégées", "Province de la Tapoa / Gourma", "Corridor biologique reliant les parcs d'Arly et du W pour la migration des grands troupeaux."),
                HeritageItem(26, "🌳", "Réserve de faune de Bontioli", "Forêts classées et aires protégées", "Province de la Bougouriba (Sud-Ouest)", "Réserve de savane arborée protégeant les antilopes, phacochères et primates du Sud-Ouest.")
            )
        ),
        HeritageCategory(
            title = "Monuments et mémoriaux",
            emoji = "🗿",
            colorHex = 0xFFB91C1C, // Rouge brique / Faso
            items = listOf(
                HeritageItem(27, "🏛️", "Monument des Héros Nationaux", "Monuments et mémoriaux", "Ouaga 2000 (Ouagadougou)", "Monumental édifice de 55 mètres de haut en forme de calebasse inversée et de pyramide honorant les martyrs du pays."),
                HeritageItem(28, "🕊️", "Mémorial Thomas Sankara", "Monuments et mémoriaux", "Conseil de l'Entente (Ouagadougou)", "Statue géante de 6 mètres en bronze et musée du souvenir érigés sur le lieu historique du 15 octobre 1987."),
                HeritageItem(29, "⚰️", "Mausolée de Thomas Sankara et de ses douze compagnons", "Monuments et mémoriaux", "Site du Mémorial (Ouagadougou)", "Lieu solennel de repos final des treize martyrs de la Révolution démocratique et populaire d'août 1983."),
                HeritageItem(30, "🐎", "Monument de la Princesse Yennenga", "Monuments et mémoriaux", "Place des Cinéastes (Ouagadougou)", "Statue équestre de la guerrière fondatrice de la dynastie Mossi, brandissant son javelot sur son étalon."),
                HeritageItem(31, "🔥", "Flambeau de la Révolution", "Monuments et mémoriaux", "Rond-point de la Révolution (Ouaga)", "Symbole érigé durant la révolution sankariste représentant la flamme de la souveraineté et de l'intégrité."),
                HeritageItem(32, "🎬", "Monument des Cinéastes Africains", "Monuments et mémoriaux", "Place des Cinéastes (Ouagadougou)", "Édifice en forme d'objectifs de caméra et de bobines célébrant Ouagadougou, capitale du cinéma africain (FESPACO)."),
                HeritageItem(33, "📜", "Monument du 2-Octobre", "Monuments et mémoriaux", "Rond-point 2-Octobre (Ouagadougou)", "Commémoration du Discours d'Orientation Politique (DOP) prononcé le 2 octobre 1983 par Thomas Sankara."),
                HeritageItem(34, "🚂", "Monument de la Bataille du Rail", "Monuments et mémoriaux", "Ouagadougou", "Mémorial dédié à la mobilisation populaire pour la construction de la voie ferrée Ouaga-Tambao."),
                HeritageItem(35, "👩", "Monument de la Place de la Femme", "Monuments et mémoriaux", "Gounghin (Ouagadougou)", "Sculpture monumentale glorifiant le rôle central et l'émancipation de la femme burkinabè dans la nation."),
                HeritageItem(36, "⚰️", "Mausolée de Guimbi Ouattara", "Monuments et mémoriaux", "Bobo-Dioulasso", "Tombeau et monument d'hommage à la grande diplomate et cheffe militaire de l'ancien royaume du Guiriko."),
                HeritageItem(37, "⚰️", "Mausolée de Tiéfo Amoro", "Monuments et mémoriaux", "Noumoûkêdougou (Hauts-Bassins)", "Haut-lieu de mémoire honorant le roi Tiéfo Amoro, héros de la résistance contre les troupes de Samory Touré."),
                HeritageItem(38, "🌍", "Monument de la Paix", "Monuments et mémoriaux", "Boulevard Circulaire (Ouagadougou)", "Structure moderne incarnant la réconciliation nationale, la concorde et l'hospitalité légendaire du Faso."),
                HeritageItem(39, "🇧🇫", "Monument du Cinquantenaire", "Monuments et mémoriaux", "Bobo-Dioulasso", "Érigé en 2010 pour célébrer les 50 ans de l'indépendance nationale du Burkina Faso (1960-2010)."),
                HeritageItem(40, "🇺🇳", "Rond-point des Nations Unies", "Monuments et mémoriaux", "Avenue Kwame Nkrumah / Boulevard de la Révolution", "Carrefour central emblématique de la capitale avec ses fontaines d'eau et drapeaux de l'amitié entre les peuples."),
                HeritageItem(41, "👑", "Place Naaba Koom", "Monuments et mémoriaux", "Face à la Gare ferroviaire de Ouagadougou", "Statue monumentale de femme en bronze représentant l'accueil chaleureux traditionnel du Faso."),
                HeritageItem(42, "⭐", "Rue des Étoiles", "Monuments et mémoriaux", "Avenue de l'Hôtel de Ville (FESPACO)", "Pavage orné d'étoiles de bronze immortalisant les réalisateurs lauréats de l'Étalon d'Or de Yennenga.")
            )
        ),
        HeritageCategory(
            title = "Royaumes, palais et chefferies",
            emoji = "👑",
            colorHex = 0xFF6D28D9, // Pourpre royal
            items = listOf(
                HeritageItem(43, "👑", "Palais du Mogho Naaba — Ouagadougou", "Royaumes, palais et chefferies", "Quartier Paspanga (Ouagadougou)", "Siège séculaire de l'Empereur des Mossis, où se déroule chaque vendredi matin la cérémonie rituelle du Faux Départ."),
                HeritageItem(44, "👑", "Royaume de Ouagadougou", "Royaumes, palais et chefferies", "Région du Centre & Plateau-Central", "Le plus puissant des royaumes mossis traditionnels, fondé par Naaba Oubri, garant de la cohésion ancestrale."),
                HeritageItem(45, "👑", "Royaume du Yatenga", "Royaumes, palais et chefferies", "Ouahigouya (Nord)", "Royaume fondé par Naaba Yadéga au XIIe siècle, illustre pour ses guerriers et la chefferie du Naaba Kango."),
                HeritageItem(46, "👑", "Royaume de Tenkodogo", "Royaumes, palais et chefferies", "Tenkodogo (Centre-Est)", "Berceau originel de l'Empire mossi où régnèrent Naaba Gbewele et Naaba Zoungrana, fils de la Princesse Yennenga."),
                HeritageItem(47, "👑", "Royaume de Boussouma", "Royaumes, palais et chefferies", "Province du Sanmatenga (Centre-Nord)", "Principauté mossi historique fondée par Naaba Nabigswendé, reconnue pour ses rituels sacrés et son indépendance."),
                HeritageItem(48, "👑", "Royaume de Fada N’Gourma", "Royaumes, palais et chefferies", "Gulmu (Région de l'Est)", "Royaume des Gourmantché fondé par Yendabri, centré sur le pouvoir spirituel et coutumier du Nunbado."),
                HeritageItem(49, "👑", "Cour royale de Tiébélé", "Royaumes, palais et chefferies", "Tiébélé (Nahouri)", "Ensemble palatial du chef suprême des Kasséna (Pè), joyau de fresques murales géométriques traditionnelles."),
                HeritageItem(50, "👑", "Cour royale de Tenkodogo", "Royaumes, palais et chefferies", "Centre de Tenkodogo", "Palais du Naaba Guiguempolé, sanctuaire vivant des premières coutumes et généalogies de la dynastie des Mossis."),
                HeritageItem(51, "👑", "Cour royale de Boussouma", "Royaumes, palais et chefferies", "Boussouma", "Enceinte sacrée du Dima de Boussouma, préservant les instruments royaux, tambours sacrés et insignes ancestraux."),
                HeritageItem(52, "👑", "Chefferies traditionnelles du Burkina Faso", "Royaumes, palais et chefferies", "Sur l'ensemble du territoire national", "Réseau coutumier (Mossi, Peul, Bobo, Gourounsi, Lobi, Sénoufo, Bwaba) incarnant la médiation et la paix sociale.")
            )
        ),
        HeritageCategory(
            title = "Architecture et édifices historiques",
            emoji = "🕌",
            colorHex = 0xFF0284C7, // Bleu azur / Ciel
            items = listOf(
                HeritageItem(53, "🕌", "Grande Mosquée de Dioulassoba", "Architecture et édifices historiques", "Bobo-Dioulasso", "Édifiée en 1880 par l'Almamy Sidiki Sanou, chef-d'œuvre soudano-sahélien en banco avec ses tours en ogive et torons de bois."),
                HeritageItem(54, "🕌", "Mosquée ancienne de Bobo-Dioulasso", "Architecture et édifices historiques", "Quartier historique de Dioulassoba", "Lieu de prière historique bâti en argile et bois de rônier selon les techniques ancestrales de construction."),
                HeritageItem(55, "⛪", "Cathédrale de l’Immaculée-Conception de Ouagadougou", "Architecture et édifices historiques", "Avenue Mgr Thevenoud (Ouagadougou)", "Chef-d'œuvre néo-roman en briques de latérite taillées, construite entre 1934 et 1936 par les bâtisseurs locaux."),
                HeritageItem(56, "⛪", "Sanctuaire Notre-Dame de Yagma", "Architecture et édifices historiques", "Yagma (Ouagadougou)", "Grand dôme moderne et esplanade de dévotion recevant chaque année des centaines de milliers de fidèles."),
                HeritageItem(57, "🏘️", "Habitats traditionnels de Tiébélé", "Architecture et édifices historiques", "Province du Nahouri", "Complexes familiaux circulaires (Dinian) et en forme de huit peints avec de la chaux blanche, du graphite et de la latérite."),
                HeritageItem(58, "🏘️", "Habitats traditionnels de Sya", "Architecture et édifices historiques", "Bobo-Dioulasso", "Maisons séculaires en banco à toits en terrasse, intégrées dans un tissu urbain médiéval préservé."),
                HeritageItem(59, "🏘️", "Village architectural de Koumi", "Architecture et édifices historiques", "Province du Houet", "Constructions en terre avec terrasses fortifiées servant jadis de refuges défensifs contre les assauts extérieurs."),
                HeritageItem(60, "🏛️", "Maison du Peuple", "Architecture et édifices historiques", "Avenue de la Nation (Ouagadougou)", "Structure emblématique des années 1960 alliant modernisme brutaliste et motifs architecturaux burkinabè.")
            )
        )
    )

    val allItems: List<HeritageItem> by lazy {
        categories.flatMap { it.items }
    }
}
