package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

object CacheManager {
    suspend fun calculateCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        var size = 0L
        try {
            size += getFolderSize(context.cacheDir)
            context.externalCacheDir?.let {
                size += getFolderSize(it)
            }
            context.codeCacheDir?.let {
                size += getFolderSize(it)
            }
        } catch (_: Exception) {}
        size
    }

    suspend fun clearAppCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            deleteDir(context.cacheDir)
            context.externalCacheDir?.let { deleteDir(it) }
            context.codeCacheDir?.let { deleteDir(it) }
        } catch (_: Exception) {}
    }

    private fun getFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            bytes += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return bytes
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        val children = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (child.isDirectory) {
                    deleteDir(child)
                } else {
                    child.delete()
                }
            }
        }
        return true
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 Ko"
        val units = arrayOf("B", "Ko", "Mo", "Go")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
