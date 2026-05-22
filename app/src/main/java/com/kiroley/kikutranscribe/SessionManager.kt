package com.kiroley.kikutranscribe

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Session(
    val name: String,
    val content: String,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val lastViewedAt: Long
)

class SessionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("KikuPrefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun generateTimestampedName(): String {
        val sdf = SimpleDateFormat("yyMMMdd - HHmm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun saveSession(session: Session) {
        val fileName = if (session.name.endsWith(".json")) session.name else "${session.name}.json"
        val jsonString = json.encodeToString(session)
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(jsonString.toByteArray())
        }
    }

    fun loadSession(name: String): Session? {
        val fileName = if (name.endsWith(".json")) name else "$name.json"
        return try {
            val jsonString = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            json.decodeFromString<Session>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    fun listSessions(sortType: String = "Most Recent"): List<String> {
        val sessionFiles = context.fileList()
            .filter { it.endsWith(".json") }
        
        val sessions = sessionFiles.mapNotNull { loadSession(it) }

        return when (sortType) {
            "Oldest to newest" -> sessions.sortedBy { it.lastModifiedAt }.map { it.name }
            "Most Recent" -> sessions.sortedByDescending { it.lastModifiedAt }.map { it.name }
            else -> sessions.sortedByDescending { it.lastModifiedAt }.map { it.name }
        }
    }

    fun getMostRecentlyViewedSessionName(): String? {
        val sessionFiles = context.fileList().filter { it.endsWith(".json") }
        val sessions = sessionFiles.mapNotNull { loadSession(it) }
        return sessions.maxByOrNull { it.lastViewedAt }?.name
    }

    fun updateLastViewed(name: String) {
        val session = loadSession(name) ?: return
        val updatedSession = session.copy(lastViewedAt = System.currentTimeMillis())
        saveSession(updatedSession)
    }

    fun renameSession(oldName: String, newName: String) {
        val oldFileName = if (oldName.endsWith(".json")) oldName else "$oldName.json"
        val newFileName = if (newName.endsWith(".json")) newName else "$newName.json"
        
        val session = loadSession(oldName) ?: return
        val renamedSession = session.copy(name = newName.removeSuffix(".json"))
        
        saveSession(renamedSession)
        
        if (oldFileName != newFileName) {
            val oldFile = File(context.filesDir, oldFileName)
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }

    fun deleteSession(name: String) {
        val fileName = if (name.endsWith(".json")) name else "$name.json"
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    // Settings Persistence
    fun saveFontSize(size: Float) = prefs.edit { putFloat("fontSize", size) }
    fun loadFontSize(): Float = prefs.getFloat("fontSize", 18f)

    fun saveSortType(type: String) = prefs.edit { putString("sortType", type) }
    fun loadSortType(): String = prefs.getString("sortType", "Most Recent") ?: "Most Recent"
}
