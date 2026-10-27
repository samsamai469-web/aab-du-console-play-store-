package com.example

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.cache.SmartTranslationCache
import com.example.data.local.AppDatabase
import com.example.data.local.DictionaryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SamAiApplication : Application(), ImageLoaderFactory {

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE translations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Log.w("SamAiApplication", "Migration 13->14 notice: ${e.message}")
            }
        }
    }

    val database: AppDatabase by lazy {
        try {
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "sam_ai_db"
            )
                .addMigrations(MIGRATION_13_14)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        } catch (e: Throwable) {
            Log.e("SamAiApplication", "Error building persistent DB, using in-memory fallback", e)
            try {
                Room.inMemoryDatabaseBuilder(applicationContext, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            } catch (fatal: Throwable) {
                Log.e("SamAiApplication", "Fatal DB error, emergency init", fatal)
                Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).build()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialiser Firebase de façon synchrone avant toute coroutine
        try {
            com.example.data.remote.FirebaseAuthService.isFirebaseReady(this)
        } catch (t: Throwable) {
            Log.w("SamAiApplication", "Firebase sync init fallback: ${t.message}")
        }

        // Nettoyage complet du cache d'images pour purger immédiatement tout ancien visuel
        try {
            cacheDir.resolve("image_cache").deleteRecursively()
        } catch (_: Throwable) {}

        // 2. Seeding et mise à jour de la base de données en arrière-plan
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("sam_traduction_prefs", MODE_PRIVATE)
                val seedVersion = prefs.getInt("db_seed_version", 0)
                val currentTargetVersion = 7

                if (seedVersion < currentTargetVersion) {
                    val dao = database.linguisticDao()
                    // Prepare authoritative list in memory first (ultra fast)
                    val combinedList = mutableListOf<com.example.data.local.LinguisticKnowledge>()

                    // 1. Official Dioula Corpus & Numbers
                    combinedList.addAll(com.example.data.local.DioulaKnowledgeData.seedDioulaDatabase())

                    // 2. Official Authoritative Reference updates for Mooré
                    DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEach { ref ->
                        combinedList.add(
                            com.example.data.local.LinguisticKnowledge(
                                word = ref.moore,
                                translation = ref.french,
                                language = "Mooré",
                                variants = ref.variants,
                                synonyms = ref.synonyms,
                                category = ref.category,
                                status = "validated",
                                confidenceLevel = 100,
                                difficulty = ref.difficulty,
                                level = 1,
                                pronunciation = ref.pronunciation,
                                source = "Mise à jour officielle Mooré de Sam Traduction"
                            )
                        )
                    }

                    // 3. Full dictionary Mooré entries
                    val fullList = DictionaryData.seedDatabase()
                    val existingKeys = combinedList.map { "${it.word.lowercase().trim()}_${it.translation.lowercase().trim()}" }.toSet()
                    fullList.forEach { entry ->
                        val key = "${entry.word.lowercase().trim()}_${entry.translation.lowercase().trim()}"
                        if (key !in existingKeys) {
                            combinedList.add(entry)
                        }
                    }

                    // Insert all in a single batch operation
                    dao.insertAll(combinedList)
                    prefs.edit().putInt("db_seed_version", currentTargetVersion).apply()
                }
            } catch (e: Throwable) {
                Log.e("SamAiApplication", "Database seeding non-blocking error", e)
            }
        }
    }

    private var imageLoaderInstance: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        return imageLoaderInstance ?: try {
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .allowHardware(true)
                .respectCacheHeaders(false)
                .crossfade(true)
                .build().also {
                    imageLoaderInstance = it
                }
        } catch (e: Throwable) {
            Log.e("SamAiApplication", "Error creating custom ImageLoader, using default", e)
            ImageLoader.Builder(this).build().also { imageLoaderInstance = it }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            imageLoaderInstance?.memoryCache?.trimMemory(level)
            if (level >= TRIM_MEMORY_BACKGROUND) {
                SmartTranslationCache.autoPrune()
            }
        } catch (_: Throwable) {
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            imageLoaderInstance?.memoryCache?.clear()
            SmartTranslationCache.onLowMemory()
        } catch (_: Throwable) {
        }
    }
}
