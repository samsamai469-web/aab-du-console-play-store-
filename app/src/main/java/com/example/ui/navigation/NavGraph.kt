package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()

    @Serializable
    data class Translation(val initialText: String? = null, val initialTargetLang: String? = null) : Screen()

    @Serializable
    data object Learning : Screen()

    @Serializable
    data object News : Screen()

    @Serializable
    data object History : Screen()

    @Serializable
    data object Profile : Screen()
    @Serializable
    data object Favorites : Screen()
    @Serializable
    data object Notifications : Screen()
    @Serializable
    data object Storage : Screen()
    @Serializable
    data object Language : Screen()

    @Serializable
    data class Support(val initialType: String = "problem") : Screen()

    @Serializable
    data object About : Screen()

    @Serializable
    data object AdminMoore : Screen()
}
