package com.example.data.local

import kotlinx.serialization.Serializable

@Serializable
enum class AuthProvider(val displayName: String) {
    GUEST("Invité"),
    GOOGLE("Google"),
    EMAIL("Email"),
    PHONE("Téléphone"),
    WHATSAPP("WhatsApp")
}

@Serializable
data class UserProfile(
    val isLoggedIn: Boolean = false,
    val userId: String = "guest",
    val googleSubjectId: String? = null,
    val name: String = "Invité",
    val email: String = "",
    val role: String = "user",
    val provider: AuthProvider = AuthProvider.GUEST,
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    val isCloudSynced: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true) || com.example.util.SecurityUtils.isSuperAdminEmail(email)

    val displayName: String
        get() = name
}

