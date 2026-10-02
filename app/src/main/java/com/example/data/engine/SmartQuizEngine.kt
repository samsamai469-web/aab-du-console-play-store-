package com.example.data.engine

import com.example.data.local.LinguisticKnowledge
import java.util.Collections

object SmartQuizEngine {
    data class QuizCategoryItem(
        val id: String,
        val title: String,
        val icon: String
    )

    val CATEGORIES = listOf(
        QuizCategoryItem("all", "Tous", "🌍"),
        QuizCategoryItem("Salutations", "Salutations", "🤝"),
        QuizCategoryItem("Famille", "Famille", "👨‍👩‍👧"),
        QuizCategoryItem("Commerce", "Commerce", "🛍️"),
        QuizCategoryItem("Nourriture", "Nourriture", "🍲"),
        QuizCategoryItem("Nombres", "Nombres", "🔢")
    )

    enum class QuestionType {
        TARGET_TO_FRENCH,
        FRENCH_TO_TARGET,
        AUDIO_LISTENING,
        TRUE_FALSE
    }

    data class QuizQuestion(
        val id: String,
        val prompt: String,
        val targetWord: String,
        val correctAnswer: String,
        val options: List<String>,
        val explanation: String = "",
        val phonetic: String = "",
        val type: QuestionType = QuestionType.TARGET_TO_FRENCH,
        val language: String = "Mooré",
        val category: String = "Général",
        val contextBadge: String = category,
        val audioWord: String = targetWord,
        val wordId: Long = 0L
    )

    fun generateQuiz(
        language: String = "Mooré",
        difficulty: String = "beginner",
        categoryFilter: String = "Tous",
        questionCount: Int = 5,
        databaseKnowledge: List<LinguisticKnowledge> = emptyList()
    ): List<QuizQuestion> {
        return generateQuiz(
            knowledgeList = databaseKnowledge,
            language = language,
            category = categoryFilter,
            count = questionCount
        )
    }

    fun generateQuiz(
        knowledgeList: List<LinguisticKnowledge>,
        language: String = "Mooré",
        category: String = "Tous",
        count: Int = 5
    ): List<QuizQuestion> {
        val pool = knowledgeList.filter {
            (it.language.equals(language, ignoreCase = true) || language == "Tous" || language == "all") &&
            (category == "Tous" || category == "all" || it.category.equals(category, ignoreCase = true))
        }.ifEmpty { knowledgeList }

        if (pool.isEmpty()) return emptyList()

        val shuffled = pool.shuffled()
        val questions = mutableListOf<QuizQuestion>()

        shuffled.take(count).forEachIndexed { index, item ->
            val wrongOptions = pool
                .filter { it.translation != item.translation }
                .map { it.translation }
                .distinct()
                .shuffled()
                .take(3)
                .toMutableList()

            val type = when (index % 4) {
                0 -> QuestionType.TARGET_TO_FRENCH
                1 -> QuestionType.FRENCH_TO_TARGET
                2 -> QuestionType.AUDIO_LISTENING
                else -> QuestionType.TRUE_FALSE
            }

            val allOptions = if (type == QuestionType.TRUE_FALSE) {
                listOf("Vrai", "Faux")
            } else if (type == QuestionType.FRENCH_TO_TARGET) {
                val wrongTarget = pool
                    .filter { it.word != item.word }
                    .map { it.word }
                    .distinct()
                    .shuffled()
                    .take(3)
                    .toMutableList()
                (wrongTarget + item.word).shuffled()
            } else {
                (wrongOptions + item.translation).shuffled()
            }

            val prompt = when (type) {
                QuestionType.TARGET_TO_FRENCH -> "Que signifie « ${item.word} » en Français ?"
                QuestionType.FRENCH_TO_TARGET -> "Comment dit-on « ${item.translation} » en $language ?"
                QuestionType.AUDIO_LISTENING -> "Écoutez le mot et choisissez sa traduction exacte :"
                QuestionType.TRUE_FALSE -> "« ${item.word} » signifie bien « ${item.translation} » :"
            }

            val correct = when (type) {
                QuestionType.TARGET_TO_FRENCH, QuestionType.AUDIO_LISTENING -> item.translation
                QuestionType.FRENCH_TO_TARGET -> item.word
                QuestionType.TRUE_FALSE -> "Vrai"
            }

            questions.add(
                QuizQuestion(
                    id = "q_$index",
                    prompt = prompt,
                    targetWord = item.word,
                    correctAnswer = correct,
                    options = allOptions,
                    explanation = "« ${item.word} » se traduit par « ${item.translation} » (${item.category}).",
                    phonetic = item.pronunciation.ifBlank { item.word },
                    type = type,
                    language = item.language,
                    category = item.category,
                    contextBadge = item.category,
                    audioWord = item.word,
                    wordId = item.id
                )
            )
        }

        return questions
    }
}
