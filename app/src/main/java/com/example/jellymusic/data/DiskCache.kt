package com.example.jellymusic.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

class DiskCache(context: Context) {
    private val dir = File(context.cacheDir, "jellyfin_api").apply { mkdirs() }

    fun get(key: String, maxAgeMs: Long): String? {
        val file = fileFor(key)
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age < 0 || age > maxAgeMs) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun modifiedAt(key: String): Long {
        val file = fileFor(key)
        return if (file.exists()) file.lastModified() else 0L
    }

    fun getAny(key: String): String? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun put(key: String, value: String) {
        runCatching {
            fileFor(key).writeText(value)
        }
    }

    fun clear() {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun fileFor(key: String): File = File(dir, hash(key) + ".json")

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}