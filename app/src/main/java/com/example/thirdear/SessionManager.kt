package com.example.thirdear

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ThirdEarPrefs", Context.MODE_PRIVATE)

    fun generateTimestampedName(): String {
        val sdf = SimpleDateFormat("yyMMMdd - HHmm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getLatestSessionName(): String? {
        return listSessions("Most Recent").firstOrNull()
    }

    fun saveSession(name: String, text: String) {
        val fileName = if (name.endsWith(".txt")) name else "$name.txt"
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(text.toByteArray())
        }
    }

    fun loadSession(name: String): String {
        val fileName = if (name.endsWith(".txt")) name else "$name.txt"
        return try {
            context.openFileInput(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    fun listSessions(sortType: String = "Most Recent"): List<String> {
        val files = context.fileList()
            .filter { it.endsWith(".txt") }
            .map { it.removeSuffix(".txt") }
        
        return when (sortType) {
            "A to Z" -> files.sortedBy { it.lowercase() }
            "Oldest to newest" -> files.sortedBy { it } // Filenames based on YYMMMdd are naturally sortable
            "Most Recent" -> files.sortedByDescending { it }
            else -> files.sortedByDescending { it }
        }
    }

    fun renameSession(oldName: String, newName: String) {
        val oldFile = File(context.filesDir, if (oldName.endsWith(".txt")) oldName else "$oldName.txt")
        val newFile = File(context.filesDir, if (newName.endsWith(".txt")) newName else "$newName.txt")
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }
    }

    fun deleteSession(name: String) {
        val file = File(context.filesDir, if (name.endsWith(".txt")) name else "$name.txt")
        if (file.exists()) {
            file.delete()
        }
    }

    // Settings Persistence
    fun saveFontSize(size: Float) = prefs.edit().putFloat("fontSize", size).apply()
    fun loadFontSize(): Float = prefs.getFloat("fontSize", 18f)

    fun saveSortType(type: String) = prefs.edit().putString("sortType", type).apply()
    fun loadSortType(): String = prefs.getString("sortType", "Most Recent") ?: "Most Recent"
}
