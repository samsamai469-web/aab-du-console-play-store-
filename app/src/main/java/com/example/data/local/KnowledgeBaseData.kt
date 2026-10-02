package com.example.data.local

object KnowledgeBaseData {

    data class EntityRelation(
        val name: String,
        val type: String, // "PERSONNE", "ENTREPRISE", "SPORTIF", "CLUB", "INSTITUTION"
        val aliases: List<String> = emptyList(),
        val organization: String = "",
        val role: String = "",
        val sector: String = "",
        val description: String,
        val details: String,
        val verifiedSources: String = "Sources officielles / Registres publics"
    )

    val KNOWLEDGE_ENTITIES: List<EntityRelation> = listOf(
        // ==================== ÉCONOMIE, ENTREPRENEURS ET ENTREPRISES DU BURKINA FASO ====================
        EntityRelation(
            name = "Mahamadou Bonkoungou",
            type = "PERSONNE",
            aliases = listOf("Bonkoungou", "PDG EBOMAF"),
            organization = "Groupe EBOMAF",
            role = "Fondateur et Président Directeur Général (PDG)",
            sector = "BTP, Infrastructures, Aviation (Liza Transport), Banque (IB bank)",
            description = "Grand entrepreneur et industriel burkinabè, fondateur du Groupe EBOMAF.",
            details = "Fondé en 1989 à Dédougou. EBOMAF est un géant sous-régional du BTP actif au Burkina Faso, Bénin, Togo, Côte d'Ivoire, Guinée, Liberia, Djibouti. Propriétaire de Liza Transport International et d'International Business Bank (IB Bank)."
        ),
        EntityRelation(
            name = "EBOMAF",
            type = "ENTREPRISE",
            aliases = listOf("Groupe EBOMAF", "Entreprise Bonkoungou Mahamadou et Fils"),
            organization = "Groupe EBOMAF",
            role = "Multinationale BTP & Holding",
            sector = "BTP, Génie civil, Transports aériens, Banques",
            description = "Entreprise générale de Bâtiment et Travaux Publics fondée par Mahamadou Bonkoungou.",
            details = "Siège : Ouagadougou. Filiales majeures : EBOMAF BTP, Liza Transport International (LTI), IB Bank (ex-Banque de l'Habitat du Burkina). Chantiers d'envergure autoroutiers, aéroportuaires et immobiliers en Afrique de l'Ouest."
        ),
        EntityRelation(
            name = "Idrissa Nassa",
            type = "PERSONNE",
            aliases = listOf("Nassa", "PDG Coris Bank"),
            organization = "Groupe Coris / Coris Bank International",
            role = "Fondateur et Président du Conseil d'Administration",
            sector = "Banque, Finance, Assurances, Bourse",
            description = "Éminent banquier et homme d'affaires burkinabè, fondateur du premier groupe bancaire panafricain originaire du Burkina Faso.",
            details = "A débuté dans le commerce avant de racheter la Financière du Burkina en 2008 pour créer Coris Bank International. Également Président du Conseil National du Patronat Burkinabè (CNPB)."
        ),
        EntityRelation(
            name = "Coris Bank International",
            type = "ENTREPRISE",
            aliases = listOf("Coris Bank", "Groupe Coris", "CBI"),
            organization = "Groupe Coris",
            role = "Groupe bancaire et financier panafricain",
            sector = "Banque de détail, Banque d'affaires, Assurances (Coris Assurances), Mésofinance",
            description = "Leader bancaire au Burkina Faso et présent dans plus de 9 pays (UEMOA et au-delà).",
            details = "Siège à Ouagadougou. Cotée à la BRVM. Filiales : Coris Bank International (Burkina, Côte d'Ivoire, Mali, Sénégal, Togo, Bénin, Niger, Guinée-Bissau, Guinée), Coris Bourse, Coris Asset Management, Coris Assurances."
        ),
        EntityRelation(
            name = "Inoussa Kanazoé",
            type = "PERSONNE",
            aliases = listOf("Kanis", "Kanazoé"),
            organization = "Groupe Kanis International / CIM METAL Group",
            role = "Fondateur et Président Directeur Général",
            sector = "Cimenterie, Métallurgie, Agroalimentaire, Négoce, Logistique",
            description = "Capitaine d'industrie burkinabè, pionnier de la production locale de ciment et de fer.",
            details = "Fondateur de CIMFASO, CIMASSO (Burkina), CIMIVOIRE (Côte d'Ivoire), Kanis Logistics. Grand acteur de la distribution de produits de grande consommation."
        ),
        EntityRelation(
            name = "CIM METAL Group",
            type = "ENTREPRISE",
            aliases = listOf("CIMFASO", "CIMASSO", "Kanis Group"),
            organization = "CIM METAL Group",
            role = "Groupe industriel de premier plan",
            sector = "Cimenterie, Matériaux de construction, Métallurgie",
            description = "Groupe industriel produisant le ciment de qualité supérieure au Burkina Faso et en Afrique de l'Ouest.",
            details = "Comprend les usines CIMFASO à Ouagadougou et CIMASSO à Bobo-Dioulasso, ainsi que Cimenteries en Côte d'Ivoire. Fondateur : Inoussa Kanazoé."
        ),
        EntityRelation(
            name = "Apollinaire Compaoré",
            type = "PERSONNE",
            aliases = listOf("Compaoré Planor", "PDG Telecel"),
            organization = "Groupe Planor Afrique",
            role = "Fondateur et Président Directeur Général",
            sector = "Télécommunications (Telecel Faso), Banque (Wari / UBA participations), Assurances, Distribution",
            description = "Self-made man et pionnier des télécommunications privées et du commerce au Burkina Faso.",
            details = "Fondateur de Telecel Faso (3e opérateur télécom au Burkina), Alpha Télécom au Mali, et du réseau de distribution Planor Afrique."
        ),
        EntityRelation(
            name = "Telecel Faso",
            type = "ENTREPRISE",
            aliases = listOf("Telecel"),
            organization = "Groupe Planor Afrique",
            role = "Opérateur de télécommunications",
            sector = "Téléphonie mobile, Internet 4G, Mobile Money",
            description = "L'un des principaux opérateurs historiques de téléphonie et de services numériques au Burkina Faso.",
            details = "Siège à Ouagadougou. Fondé par Apollinaire Compaoré. Propose des services d'appels, forfaits internet et services financiers mobiles."
        ),
        EntityRelation(
            name = "ONATEL-SA",
            type = "ENTREPRISE",
            aliases = listOf("Moov Africa Burkina", "ONATEL", "Moov Africa"),
            organization = "Groupe Maroc Telecom / État burkinabè",
            role = "Opérateur historique national de télécommunications",
            sector = "Télécommunications, Fibre optique, Fixe, Mobile (Moov Africa), Moov Money",
            description = "Opérateur global des télécommunications au Burkina Faso, leader historique de la téléphonie fixe et mobile.",
            details = "Cotée à la BRVM. Filiale du Groupe Maroc Telecom avec participation de l'État burkinabè."
        ),
        EntityRelation(
            name = "Orange Burkina Faso",
            type = "ENTREPRISE",
            aliases = listOf("Orange BF", "Airtel Burkina (anciennement)"),
            organization = "Groupe Orange",
            role = "Opérateur de télécommunications et services numériques",
            sector = "Téléphonie mobile, Très Haut Débit, Orange Money, Cloud",
            description = "Grand opérateur de télécommunications et leader du service de paiement mobile Orange Money au Burkina.",
            details = "Présent sur l'ensemble du territoire national avec un réseau 4G/4G+ étendu et services d'inclusion financière."
        ),
        EntityRelation(
            name = "SONABHY",
            type = "ENTREPRISE",
            aliases = listOf("Société Nationale Burkinabè d'Hydrocarbures"),
            organization = "Société d'État",
            role = "Entreprise publique stratégique",
            sector = "Énergie, Importation et Stockage d'Hydrocarbures et Gaz",
            description = "Société d'État détenant le monopole d'importation et de stockage sécurisé des produits pétroliers et gaziers au Burkina Faso.",
            details = "Siège à Ouagadougou. Dépôts stratégiques à Bingo (Ouagadougou) et Péni (Bobo-Dioulasso)."
        ),
        EntityRelation(
            name = "SONABEL",
            type = "ENTREPRISE",
            aliases = listOf("Société Nationale d'Électricité du Burkina"),
            organization = "Société d'État",
            role = "Entreprise publique d'électricité",
            sector = "Énergie, Production, Transport et Distribution d'électricité",
            description = "Société nationale chargée de la fourniture d'énergie électrique, développant activement le solaire et les interconnexions régionales.",
            details = "Exploite des centrales thermiques, hydroélectriques et de grands parcs solaires (comme Zagtouli, Nagréongo)."
        ),
        EntityRelation(
            name = "SOFITEX",
            type = "ENTREPRISE",
            aliases = listOf("Société Burkinabè des Fibres Textiles"),
            organization = "Société anonyme d'économie mixte",
            role = "Société cotonnière agro-industrielle",
            sector = "Agro-industrie, Coton, Égrenage, Exportation",
            description = "Poumon historique de la filière cotonnière au Burkina Faso, encadrant les producteurs et gérant les usines d'égrenage.",
            details = "Siège à Bobo-Dioulasso. Traite la majorité de la récolte d'or blanc burkinabè."
        ),
        EntityRelation(
            name = "Oumarou Kanazoé",
            type = "PERSONNE",
            aliases = listOf("El Hadj Oumarou Kanazoé", "OK"),
            organization = "Entreprise Oumarou Kanazoé (OK)",
            role = "Fondateur historique et grand bâtisseur",
            sector = "BTP, Routes, Hydraulique, Bâtiments, Philanthropie",
            description = "Figure légendaire du monde des affaires et du BTP au Burkina Faso et en Afrique de l'Ouest (1927-2011).",
            details = "Pionnier ayant construit des milliers de kilomètres de routes goudronnées, barrages et monuments emblématiques au Burkina Faso."
        ),
        EntityRelation(
            name = "Lassiné Diawara",
            type = "PERSONNE",
            aliases = listOf("Diawara"),
            organization = "Groupe Diawara / BOA Burkina / CCI-BF",
            role = "Capitaine d'industrie et Président honoraire",
            sector = "Industrie, Tabac (MABUCIG), Banques (Bank of Africa), Logistique",
            description = "Grand industriel basé à Bobo-Dioulasso, acteur majeur de la Chambre de Commerce et d'Industrie.",
            details = "Président du Conseil d'Administration de Bank of Africa Burkina Faso et de la Manufacture Burkinabè de Cigarettes (MABUCIG)."
        ),

        // ==================== FOOTBALL BURKINABÈ ET LES ÉTALONS ====================
        EntityRelation(
            name = "Bertrand Traoré",
            type = "SPORTIF",
            aliases = listOf("Traoré Bertrand", "Capitaine des Étalons"),
            organization = "Étalons du Burkina Faso / Ajax Amsterdam",
            role = "Attaquant / Ailier droit / Capitaine",
            sector = "Football professionnel",
            description = "Star et capitaine de l'équipe nationale des Étalons du Burkina Faso.",
            details = "Carrière : Chelsea FC, Vitesse Arnhem, Ajax Amsterdam, Olympique Lyonnais, Aston Villa, Villarreal CF. Finaliste CAN 2013 et demi-finaliste CAN 2017 & 2021."
        ),
        EntityRelation(
            name = "Edmond Tapsoba",
            type = "SPORTIF",
            aliases = listOf("Tapsoba", "Eddy"),
            organization = "Bayer Leverkusen / Étalons du Burkina Faso",
            role = "Défenseur central",
            sector = "Football professionnel",
            description = "Défenseur international burkinabè de classe mondiale, champion d'Allemagne invaincu avec le Bayer Leverkusen (Bundesliga 2023-2024).",
            details = "Formé à Salitas FC et US Ouagadougou, révélé au Vitória Guimarães, pilier incontournable du Bayer Leverkusen de Xabi Alonso et des Étalons."
        ),
        EntityRelation(
            name = "Dango Ouattara",
            type = "SPORTIF",
            aliases = listOf("Ouattara Dango"),
            organization = "AFC Bournemouth / Étalons du Burkina Faso",
            role = "Attaquant / Ailier polyvalent",
            sector = "Football professionnel (Premier League)",
            description = "Attaquant international burkinabè rapide et puissant évoluant en Premier League anglaise.",
            details = "Formé au Majestic FC (Burkina Faso), révélé au FC Lorient en Ligue 1 avant son transfert record à Bournemouth."
        ),
        EntityRelation(
            name = "Issa Kaboré",
            type = "SPORTIF",
            aliases = listOf("Kaboré Issa"),
            organization = "Étalons du Burkina Faso / Benfica / Manchester City",
            role = "Latéral droit",
            sector = "Football professionnel",
            description = "Meilleur jeune joueur de la CAN 2021, latéral ultra-rapide appartenant à Manchester City.",
            details = "Formé au Rahimo FC (Bobo-Dioulasso), a joué à Malines, Troyes, Marseille, Luton Town, Benfica."
        ),
        EntityRelation(
            name = "Hervé Koffi",
            type = "SPORTIF",
            aliases = listOf("Koffi Kouakou Hervé", "Le Chat"),
            organization = "RC Lens / Étalons du Burkina Faso",
            role = "Gardien de but",
            sector = "Football professionnel",
            description = "Gardien de but titulaire spectaculaire des Étalons du Burkina Faso.",
            details = "Passé par l'ASEC Mimosas, LOSC Lille, Belenenses, Charleroi, et désormais au RC Lens en Ligue 1."
        ),
        EntityRelation(
            name = "Charles Kaboré",
            type = "SPORTIF",
            aliases = listOf("Kaboré Charles"),
            organization = "Légende des Étalons",
            role = "Milieu défensif / Ancien capitaine emblématique",
            sector = "Football",
            description = "Recordman historique de sélections avec les Étalons du Burkina Faso.",
            details = "Ancien capitaine exemplaire, champion de France avec l'Olympique de Marseille (2010), a brillé à Krasnodar et au Dynamo Moscou."
        ),
        EntityRelation(
            name = "Jonathan Pitroipa",
            type = "SPORTIF",
            aliases = listOf("Pitroipa"),
            organization = "Légende des Étalons",
            role = "Ailier / Dribbleur d'exception",
            sector = "Football",
            description = "Meilleur joueur de la Coupe d'Afrique des Nations 2013 (CAN 2013).",
            details = "A illuminé la Bundesliga (Hambourg SV, SC Fribourg) et la Ligue 1 (Stade Rennais). Formé à Planète Champion."
        ),
        EntityRelation(
            name = "Moumouni Dagano",
            type = "SPORTIF",
            aliases = listOf("Dagano"),
            organization = "Légende des Étalons",
            role = "Avant-centre / Buteur historique",
            sector = "Football",
            description = "Meilleur buteur de l'histoire de la sélection nationale des Étalons du Burkina Faso.",
            details = "A marqué l'histoire à l'Étoile Filante de Ouagadougou, au KRC Genk, à Guingamp et Sochaux."
        ),
        EntityRelation(
            name = "Étalons du Burkina Faso",
            type = "SELECTION",
            aliases = listOf("Les Étalons", "Équipe nationale du Burkina Faso"),
            organization = "Fédération Burkinabè de Football (FBF)",
            role = "Sélection nationale",
            sector = "Football international",
            description = "Équipe nationale de football du Burkina Faso, fierté du peuple burkinabè.",
            details = "Faits d'armes majeurs : Vice-champion d'Afrique à la CAN 2013 (Afrique du Sud), Médaille de Bronze à la CAN 2017 (Gabon), 4e place à la CAN 1998 et CAN 2021. Stade fétiche : Stade du 4-Août à Ouagadougou."
        ),
        EntityRelation(
            name = "ASFA Yennenga",
            type = "CLUB",
            aliases = listOf("ASFA-Y", "Les Jaune et Vert"),
            organization = "Club burkinabè",
            role = "Club de football de première division (Ligue 1 Burkina)",
            sector = "Football",
            description = "L'un des plus grands clubs historiques de Ouagadougou et multiples champions du Burkina Faso.",
            details = "Basé à Ouagadougou. Grand rival de l'Étoile Filante de Ouagadougou (EFO) dans le derby de la capitale."
        ),
        EntityRelation(
            name = "Étoile Filante de Ouagadougou",
            type = "CLUB",
            aliases = listOf("EFO", "La Reine des stades", "Les Bleu et Blanc"),
            organization = "Club burkinabè",
            role = "Club de football le plus titré du Burkina Faso",
            sector = "Football",
            description = "Club historique légendaire détenant le record de championnats nationaux et de Coupes du Faso.",
            details = "Fondé en 1955 à Ouagadougou. Couleurs : Bleu et Blanc."
        ),
        EntityRelation(
            name = "AS SONABEL",
            type = "CLUB",
            aliases = listOf("Les Électriciens"),
            organization = "Club burkinabè",
            role = "Club de football de première division",
            sector = "Football",
            description = "Club de première division soutenu par la société nationale d'électricité SONABEL, champion du Faso 2021.",
            details = "Basé à Ouagadougou."
        ),
        EntityRelation(
            name = "Rahimo FC",
            type = "CLUB",
            aliases = listOf("Rahimo"),
            organization = "Centre de formation et club d'élite",
            role = "Club formateur d'excellence",
            sector = "Football / Formation",
            description = "Club et académie fondés par l'ancien international Rahim Ouédraogo à Bama / Bobo-Dioulasso.",
            details = "A formé des talents de classe mondiale dont Issa Kaboré. Double champion national championnat & coupe."
        ),
        EntityRelation(
            name = "Hugues Fabrice Zango",
            type = "SPORTIF",
            aliases = listOf("Zango", "Docteur Zango"),
            organization = "Athlétisme mondial / Burkina Faso",
            role = "Champion du Monde et Médaillé Olympique de Triple Saut",
            sector = "Athlétisme",
            description = "Premier médaillé olympique de l'histoire du Burkina Faso (Tokyo 2020) et Champion du Monde de triple saut (Budapest 2023).",
            details = "Recordman du monde en salle du triple saut (18,07 m). Également docteur en génie électrique, incarnant l'excellence sportive et académique burkinabè."
        ),
        EntityRelation(
            name = "Iron Biby",
            type = "SPORTIF",
            aliases = listOf("Cheick Ahmed al-Hassan Sanou", "Biby"),
            organization = "Strongman / Force athlétique mondiale",
            role = "Recordman du Monde de Log Lift (Force athlétique)",
            sector = "Strongman / Force",
            description = "L'homme le plus fort du monde en épaulé-jeté de bûche (Log Lift), fierté nationale burkinabè originaire de Bobo-Dioulasso.",
            details = "Détenteur absolu du record du monde Guinness avec 231 kg soulevés au-dessus de la tête."
        ),
        EntityRelation(
            name = "Marthe Koala",
            type = "SPORTIF",
            aliases = listOf("Koala Marthe"),
            organization = "Athlétisme burkinabè",
            role = "Championne d'Afrique en Saut en longueur et Heptathlon",
            sector = "Athlétisme féminin",
            description = "Athlète émérite burkinabè multi-médaillée d'or aux championnats d'Afrique et Jeux de la Francophonie.",
            details = "Détentrice de plusieurs records nationaux en saut en longueur et 100m haies."
        ),

        // ==================== FOOTBALL & SPORT MONDIAL (EXEMPLES CLÉS ET RELATIONS) ====================
        EntityRelation(
            name = "Francisco Conceição",
            type = "SPORTIF",
            aliases = listOf("Conceição", "Chico Conceição"),
            organization = "Juventus FC / Sélection du Portugal",
            role = "Ailier droit / Attaquant",
            sector = "Football international (Serie A / UEFA)",
            description = "International portugais, ailier explosif et dribbleur prêté avec option à la Juventus.",
            details = "Fils de Sergio Conceição. Formé au Sporting CP et FC Porto, passé par l'Ajax Amsterdam avant son éclosion au FC Porto et son transfert à la Juventus. Buteur décisif à l'Euro 2024 avec la Seleção portugaise."
        )
    )

    fun searchKnowledge(query: String): List<EntityRelation> {
        val cleanQuery = query.lowercase().trim()
        val queryWords = cleanQuery.split(" ", "-", "'", ",", "?", "!").filter { it.length > 2 }
        if (queryWords.isEmpty()) return emptyList()

        return KNOWLEDGE_ENTITIES.filter { entity ->
            val nameMatch = entity.name.lowercase().contains(cleanQuery) || queryWords.any { entity.name.lowercase().contains(it) }
            val aliasMatch = entity.aliases.any { alias -> alias.lowercase().contains(cleanQuery) || queryWords.any { alias.lowercase().contains(it) } }
            val orgMatch = entity.organization.lowercase().contains(cleanQuery) || queryWords.any { entity.organization.lowercase().contains(it) }
            val sectorMatch = entity.sector.lowercase().contains(cleanQuery)
            val descMatch = entity.description.lowercase().contains(cleanQuery) || entity.details.lowercase().contains(cleanQuery)

            nameMatch || aliasMatch || orgMatch || (queryWords.size >= 2 && (sectorMatch || descMatch))
        }.take(4)
    }

    fun isRealTimeQuery(text: String): Boolean {
        val lower = text.lowercase()
        val realTimeKeywords = listOf(
            "aujourd'hui", "actuellement", "dernier match", "prochain match", "cette saison",
            "transfert", "classement actuel", "résultat", "blessure", "mercato", "récemment",
            "en ce moment", "qui est le pdg actuel", "qui dirige actuellement", "qui a gagné",
            "dernière minute", "nouveau", "nouvelle", "score", "match d'hier", "match de ce soir"
        )
        return realTimeKeywords.any { lower.contains(it) }
    }
}
