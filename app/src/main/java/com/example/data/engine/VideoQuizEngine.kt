package com.example.data.engine

import android.content.Context
import android.content.SharedPreferences
import com.example.data.engine.VideoQuizData.QuizBadge
import com.example.data.engine.VideoQuizData.QuizComment
import com.example.data.engine.VideoQuizData.VideoQuizItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Moteur Global de Gamification & Quiz Vidéo "Quiz Burkina" 🇧🇫
 */
class VideoQuizEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("video_quiz_burkina_prefs", Context.MODE_PRIVATE)

    companion object {
        const val MAX_LIVES = 5
        const val LIFE_REGEN_MINUTES = 30
        const val XP_CORRECT_ANSWER = 100
        const val XP_FAST_ANSWER = 50
        const val XP_STREAK_5 = 250
        const val XP_PERFECT_SCORE = 1000

        fun getLevelTitle(xp: Int): String {
            return when {
                xp >= 100000 -> "Maître du Burkina 🇧🇫"
                xp >= 50000 -> "Ambassadeur du Faso 🌟"
                xp >= 20000 -> "Expert National 👑"
                xp >= 5000 -> "Connaisseur Émérite 🏅"
                xp >= 1000 -> "Curieux Passionné 🎓"
                else -> "Débutant Explorateur 🌱"
            }
        }

        fun getNextLevelXp(xp: Int): Int {
            return when {
                xp >= 100000 -> 200000
                xp >= 50000 -> 100000
                xp >= 20000 -> 50000
                xp >= 5000 -> 20000
                xp >= 1000 -> 5000
                else -> 1000
            }
        }

        fun getLevelProgress(xp: Int): Float {
            val (currentBase, target) = when {
                xp >= 100000 -> Pair(100000, 200000)
                xp >= 50000 -> Pair(50000, 100000)
                xp >= 20000 -> Pair(20000, 50000)
                xp >= 5000 -> Pair(5000, 20000)
                xp >= 1000 -> Pair(1000, 5000)
                else -> Pair(0, 1000)
            }
            return ((xp - currentBase).toFloat() / (target - currentBase).toFloat()).coerceIn(0f, 1f)
        }
    }

    // État réactif
    private val _userXp = MutableStateFlow(prefs.getInt("user_xp", 1250))
    val userXp: StateFlow<Int> = _userXp.asStateFlow()

    private val _unlockedLevel = MutableStateFlow(prefs.getInt("unlocked_level", 1))
    val unlockedLevel: StateFlow<Int> = _unlockedLevel.asStateFlow()

    private val _lives = MutableStateFlow(calculateCurrentLives())
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val _currentStreak = MutableStateFlow(prefs.getInt("current_streak", 3))
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _maxStreak = MutableStateFlow(prefs.getInt("max_streak", 8))
    val maxStreak: StateFlow<Int> = _maxStreak.asStateFlow()

    private val _quizzesPlayed = MutableStateFlow(prefs.getInt("quizzes_played", 24))
    val quizzesPlayed: StateFlow<Int> = _quizzesPlayed.asStateFlow()

    private val _correctAnswersCount = MutableStateFlow(prefs.getInt("correct_answers", 20))
    val correctAnswersCount: StateFlow<Int> = _correctAnswersCount.asStateFlow()

    private val _likedQuizIds = MutableStateFlow(prefs.getStringSet("liked_quizzes", emptySet()) ?: emptySet())
    val likedQuizIds: StateFlow<Set<String>> = _likedQuizIds.asStateFlow()

    private val _bookmarkedQuizIds = MutableStateFlow(prefs.getStringSet("bookmarked_quizzes", emptySet()) ?: emptySet())
    val bookmarkedQuizIds: StateFlow<Set<String>> = _bookmarkedQuizIds.asStateFlow()

    private val _quizItems = MutableStateFlow<List<VideoQuizItem>>(VideoQuizData.INITIAL_QUIZ_LIBRARY)
    val quizItems: StateFlow<List<VideoQuizItem>> = _quizItems.asStateFlow()

    // Liste exclusive des quiz vidéo d'actualité 2026 pour le flux vidéo principal
    private val _newsQuizzes = MutableStateFlow<List<VideoQuizItem>>(VideoQuizData.NEWS_2026_QUIZ_LIBRARY)
    val newsQuizzes: StateFlow<List<VideoQuizItem>> = _newsQuizzes.asStateFlow()

    private val _comments = MutableStateFlow<List<QuizComment>>(VideoQuizData.INITIAL_COMMENTS)
    val comments: StateFlow<List<QuizComment>> = _comments.asStateFlow()

    private val _badges = MutableStateFlow<List<QuizBadge>>(VideoQuizData.INITIAL_BADGES)
    val badges: StateFlow<List<QuizBadge>> = _badges.asStateFlow()

    private fun calculateCurrentLives(): Int {
        val savedLives = prefs.getInt("current_lives", MAX_LIVES)
        val lastLifeLostTimestamp = prefs.getLong("last_life_lost_time", 0L)
        if (savedLives >= MAX_LIVES || lastLifeLostTimestamp == 0L) return MAX_LIVES

        val elapsedMinutes = (System.currentTimeMillis() - lastLifeLostTimestamp) / (60 * 1000L)
        val livesRegenerated = (elapsedMinutes / LIFE_REGEN_MINUTES).toInt()
        val currentLives = (savedLives + livesRegenerated).coerceAtMost(MAX_LIVES)

        prefs.edit().putInt("current_lives", currentLives).apply()
        return currentLives
    }

    fun recordQuizAnswer(
        quizItem: VideoQuizItem,
        isCorrect: Boolean,
        isFast: Boolean,
        isDailyChallenge: Boolean = false
    ): Int {
        var earnedXp = 0
        if (isCorrect) {
            val base = XP_CORRECT_ANSWER
            val speedBonus = if (isFast) XP_FAST_ANSWER else 0
            val streak = _currentStreak.value + 1
            _currentStreak.value = streak

            val streakBonus = if (streak % 5 == 0) XP_STREAK_5 else 0
            earnedXp = base + speedBonus + streakBonus

            if (isDailyChallenge) {
                earnedXp *= 2 // Double XP pour Défi du jour
            }

            val newXp = _userXp.value + earnedXp
            _userXp.value = newXp
            prefs.edit().putInt("user_xp", newXp).apply()

            if (streak > _maxStreak.value) {
                _maxStreak.value = streak
                prefs.edit().putInt("max_streak", streak).apply()
            }

            val newCorrect = _correctAnswersCount.value + 1
            _correctAnswersCount.value = newCorrect
            prefs.edit().putInt("correct_answers", newCorrect).apply()

        } else {
            _currentStreak.value = 0
            prefs.edit().putInt("current_streak", 0).apply()

            val currentLives = (_lives.value - 1).coerceAtLeast(0)
            _lives.value = currentLives
            prefs.edit()
                .putInt("current_lives", currentLives)
                .putLong("last_life_lost_time", System.currentTimeMillis())
                .apply()
        }

        val totalPlayed = _quizzesPlayed.value + 1
        _quizzesPlayed.value = totalPlayed
        prefs.edit().putInt("quizzes_played", totalPlayed).apply()

        updateBadgesProgress()
        return earnedXp
    }

    fun refillLives() {
        _lives.value = MAX_LIVES
        prefs.edit().putInt("current_lives", MAX_LIVES).putLong("last_life_lost_time", 0L).apply()
    }

    fun toggleLike(quizId: String) {
        val current = _likedQuizIds.value.toMutableSet()
        if (current.contains(quizId)) {
            current.remove(quizId)
        } else {
            current.add(quizId)
        }
        _likedQuizIds.value = current
        prefs.edit().putStringSet("liked_quizzes", current).apply()
    }

    fun toggleBookmark(quizId: String) {
        val current = _bookmarkedQuizIds.value.toMutableSet()
        if (current.contains(quizId)) {
            current.remove(quizId)
        } else {
            current.add(quizId)
        }
        _bookmarkedQuizIds.value = current
        prefs.edit().putStringSet("bookmarked_quizzes", current).apply()
    }

    fun addComment(text: String, authorName: String = "Vous") {
        if (text.isBlank()) return
        val newComment = QuizComment(
            id = "c_${System.currentTimeMillis()}",
            authorName = authorName,
            authorAvatar = "🇧🇫",
            text = text.trim(),
            timeAgo = "À l'instant",
            likes = 1
        )
        val updated = listOf(newComment) + _comments.value
        _comments.value = updated
    }

    fun addNewQuizItem(item: VideoQuizItem) {
        val updated = listOf(item) + _quizItems.value
        _quizItems.value = updated
    }

    private fun updateBadgesProgress() {
        val xp = _userXp.value
        val correct = _correctAnswersCount.value
        val maxStrk = _maxStreak.value

        val updated = _badges.value.map { badge ->
            when (badge.id) {
                "b1" -> badge.copy(isUnlocked = correct >= 1, progress = if (correct >= 1) 1f else 0f)
                "b2" -> badge.copy(isUnlocked = maxStrk >= 5, progress = (maxStrk.toFloat() / 5f).coerceIn(0f, 1f))
                "b3" -> badge.copy(isUnlocked = correct >= 10, progress = (correct.toFloat() / 10f).coerceIn(0f, 1f))
                "b4" -> badge.copy(isUnlocked = correct >= 20, progress = (correct.toFloat() / 20f).coerceIn(0f, 1f))
                "b5" -> badge.copy(isUnlocked = correct >= 30, progress = (correct.toFloat() / 30f).coerceIn(0f, 1f))
                "b6" -> badge.copy(isUnlocked = true, progress = 1f)
                "b7" -> badge.copy(isUnlocked = xp >= 10000, progress = (xp.toFloat() / 10000f).coerceIn(0f, 1f))
                "b8" -> badge.copy(isUnlocked = xp >= 50000, progress = (xp.toFloat() / 50000f).coerceIn(0f, 1f))
                else -> badge
            }
        }
        _badges.value = updated
    }

    fun getDailyChallengeQuestions(): List<VideoQuizItem> {
        val dateString = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val seed = dateString.hashCode().toLong()
        return _quizItems.value.shuffled(java.util.Random(seed))
    }

    /**
     * Récupère les quiz vidéo exclusifs du fil d'actualité 2026
     */
     fun getNewsFeedQuizzes(): List<VideoQuizItem> {
         return _newsQuizzes.value
     }

    /**
     * Récupère les quiz vidéo pour un niveau spécifique (Niveau 1 à 7, au moins 4 quiz par niveau)
     */
    fun getQuizzesForLevel(level: Int): List<VideoQuizItem> {
        val filtered = _quizItems.value.filter { it.level == level }
        return if (filtered.size >= 4) {
            filtered
        } else {
            // Fallback si besoin pour garantir au moins 4 quiz
            filtered + _quizItems.value.take(4 - filtered.size)
        }
    }

    /**
     * Récupère les quiz vidéo pour une catégorie (5 quiz par section dans Explorer)
     */
    fun getQuizzesByCategory(categoryId: String): List<VideoQuizItem> {
        return _quizItems.value.filter { it.categoryId == categoryId }
    }

    /**
     * Débloque le niveau suivant si le niveau actuel est complété sans faute.
     * Retourne true si un nouveau niveau a été débloqué.
     */
    fun unlockNextLevel(completedLevel: Int): Boolean {
        if (completedLevel >= _unlockedLevel.value && _unlockedLevel.value < 7) {
            val nextLevel = completedLevel + 1
            _unlockedLevel.value = nextLevel
            prefs.edit().putInt("unlocked_level", nextLevel).apply()

            // Bonus de déblocage de niveau : +500 XP
            val newXp = _userXp.value + 500
            _userXp.value = newXp
            prefs.edit().putInt("user_xp", newXp).apply()

            updateBadgesProgress()
            return true
        }
        return false
    }

    /**
     * Vérifie si un niveau est accessible (débloqué)
     */
    fun isLevelUnlocked(level: Int): Boolean {
        return level <= _unlockedLevel.value
    }
}
