package com.example.thirdear

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionManager(private val context: Context) {

    fun generateTimestampedName(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return "Session_${sdf.format(Date())}"
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

    fun listSessions(): List<String> {
        return context.fileList()
            .filter { it.endsWith(".txt") }
            .map { it.removeSuffix(".txt") }
            .sortedByDescending { it }
    }

    fun renameSession(oldName: String, newName: String) {
        val oldFile = File(context.filesDir, if (oldName.endsWith(".txt")) oldName else "$oldName.txt")
        val newFile = File(context.filesDir, if (newName.endsWith(".txt")) newName else "$newName.txt")
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }
    }
}
