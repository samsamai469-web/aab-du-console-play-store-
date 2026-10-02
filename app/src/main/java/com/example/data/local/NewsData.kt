package com.example.data.local

import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val category: String, // "Burkina Faso", "Afrique", "Monde", "Sport", "Économie", "Technologie", "Éducation"
    val date: String,
    val source: String,
    val url: String,
    val imageUrl: String = "",
    val imageRes: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val videoUrl: String = "",
    val youtubeId: String = "",
    val isVideo: Boolean = false,
    val fullContent: String = ""
)
