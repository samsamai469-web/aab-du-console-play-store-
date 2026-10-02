package com.example.data.repository

import com.example.data.local.AuditDao
import com.example.data.local.LinguisticDao
import com.example.data.local.LinguisticKnowledge
import com.example.data.local.SecurityAuditLogEntity
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.Flow

class LinguisticRepository(
    private val linguisticDao: LinguisticDao,
    private val auditDao: AuditDao? = null
) {
    val allKnowledge: Flow<List<LinguisticKnowledge>> = linguisticDao.getAllKnowledge()

    suspend fun insert(knowledge: LinguisticKnowledge) {
        linguisticDao.insertKnowledge(knowledge)
    }

    suspend fun insertAll(list: List<LinguisticKnowledge>) {
        linguisticDao.insertAll(list)
    }

    suspend fun getLessonWords(difficulty: String, userId: String, limit: Int): List<LinguisticKnowledge> {
        val list = linguisticDao.getLessonWords(difficulty, userId, limit)
        return if (list.isNotEmpty()) list else linguisticDao.getRandomWords(limit, 0)
    }

    fun search(query: String): Flow<List<LinguisticKnowledge>> {
        return linguisticDao.searchKnowledgeWithPriority(query, "Mooré")
    }

    suspend fun insertKnowledgeSecure(
        knowledge: LinguisticKnowledge,
        adminToken: String?,
        actorUserId: String = "guest"
    ): Pair<Boolean, String> {
        val isValidAdmin = SecurityUtils.verifyAdminToken(adminToken)
        if (!isValidAdmin) {
            auditDao?.insertLog(
                SecurityAuditLogEntity(
                    eventType = "UNAUTHORIZED_DICTIONARY_INSERT_ATTEMPT",
                    severity = "WARNING",
                    actorUserId = actorUserId,
                    action = "Tentative d'ajout direct au dictionnaire sans jeton admin valide",
                    details = "Word: ${knowledge.word}"
                )
            )
            return Pair(false, "Accès refusé. Privilèges administrateur requis.")
        }

        val officialItem = knowledge.copy(status = "validated", confidenceLevel = 100, source = "Administrateur Officiel Sam AI")
        linguisticDao.insertKnowledge(officialItem)
        auditDao?.insertLog(
            SecurityAuditLogEntity(
                eventType = "ADMIN_DICTIONARY_INSERT",
                severity = "INFO",
                actorUserId = actorUserId,
                action = "Ajout officiel au dictionnaire",
                details = "Word: ${knowledge.word} -> ${knowledge.translation}"
            )
        )
        return Pair(true, "Mot ajouté avec succès au dictionnaire officiel.")
    }

    suspend fun updateStatusWithHistorySecure(
        id: Long,
        status: String,
        history: String,
        adminToken: String?,
        actorUserId: String = "guest"
    ): Pair<Boolean, String> {
        if (!SecurityUtils.verifyAdminToken(adminToken)) {
            return Pair(false, "Action non autorisée. Jeton administrateur invalide.")
        }
        linguisticDao.updateStatusWithHistory(id, status, history)
        auditDao?.insertLog(
            SecurityAuditLogEntity(
                eventType = "ADMIN_DICTIONARY_UPDATE_STATUS",
                severity = "INFO",
                actorUserId = actorUserId,
                action = "Mise à jour du statut d'une entrée",
                details = "ID: $id, NewStatus: $status, History: $history"
            )
        )
        return Pair(true, "Statut mis à jour avec succès.")
    }

    suspend fun deleteKnowledgeSecure(
        id: Long,
        adminToken: String?,
        actorUserId: String = "guest"
    ): Pair<Boolean, String> {
        if (!SecurityUtils.verifyAdminToken(adminToken)) {
            return Pair(false, "Action non autorisée. Jeton administrateur invalide.")
        }
        linguisticDao.deleteKnowledge(id)
        auditDao?.insertLog(
            SecurityAuditLogEntity(
                eventType = "ADMIN_DICTIONARY_DELETE",
                severity = "WARNING",
                actorUserId = actorUserId,
                action = "Suppression d'une entrée du dictionnaire",
                details = "ID: $id"
            )
        )
        return Pair(true, "Entrée supprimée avec succès.")
    }
}
