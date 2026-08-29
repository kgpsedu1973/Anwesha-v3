package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO, WARNING, ERROR
}

data class AppLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val errorCode: Int? = null,
    val stackTrace: String? = null
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

object AppErrorLogger {
    private val _logs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val logs: StateFlow<List<AppLogEntry>> = _logs.asStateFlow()

    private val idCounter = AtomicLong(1)
    private const val MAX_LOGS = 300

    init {
        // Initial setup log
        logInfo("System", "অ্যাপ এরর লগিং সিস্টেম সক্রিয় হয়েছে")
    }

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(LogLevel.INFO, tag, message, null, null)
    }

    fun logWarning(tag: String, message: String, errorCode: Int? = null) {
        Log.w(tag, message)
        addEntry(LogLevel.WARNING, tag, message, errorCode, null)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null, errorCode: Int? = null) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        Log.e(tag, "$message (Code: $errorCode)", throwable)
        addEntry(LogLevel.ERROR, tag, message, errorCode, stackTrace)
    }

    private fun addEntry(
        level: LogLevel,
        tag: String,
        message: String,
        errorCode: Int?,
        stackTrace: String?
    ) {
        val newEntry = AppLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            errorCode = errorCode,
            stackTrace = stackTrace
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, newEntry) // newest first
        if (currentList.size > MAX_LOGS) {
            _logs.value = currentList.take(MAX_LOGS)
        } else {
            _logs.value = currentList
        }
    }

    fun clear() {
        _logs.value = emptyList()
        logInfo("System", "সব লগ পরিষ্কার করা হয়েছে")
    }

    fun getExportableDiagnosticReport(context: Context? = null): String {
        val sb = StringBuilder()
        sb.append("=== ANWESHA SCHOOL ERROR & DIAGNOSTIC REPORT ===\n")
        sb.append("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Package Name: ${context?.packageName ?: "com.aistudio.anwesha.school"}\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Configured SHA-1: D5:D7:3F:14:27:6C:D9:B4:3E:39:64:04:5F:CE:1F:85:FF:26:05:F8\n")
        sb.append("--------------------------------------------------\n\n")

        val currentLogs = _logs.value
        if (currentLogs.isEmpty()) {
            sb.append("কোনো এরর লগ পাওয়া যায়নি।\n")
        } else {
            currentLogs.forEach { entry ->
                sb.append("[${entry.getFormattedTime()}] [${entry.level}] [${entry.tag}]\n")
                sb.append("Message: ${entry.message}\n")
                if (entry.errorCode != null) {
                    sb.append("Error Code: ${entry.errorCode}\n")
                }
                if (!entry.stackTrace.isNullOrBlank()) {
                    sb.append("StackTrace:\n${entry.stackTrace}\n")
                }
                sb.append("--------------------------------------------------\n")
            }
        }
        return sb.toString()
    }
}
