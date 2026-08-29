package com.example.data.model

import com.example.util.BanglaUtils
import java.text.SimpleDateFormat
import java.util.*

enum class AutoSyncMode(
    val key: String,
    val titleBn: String,
    val subtitleBn: String,
    val intervalMinutes: Long
) {
    MANUAL(
        key = "MANUAL",
        titleBn = "ম্যানুয়াল সিঙ্ক (Manual Only)",
        subtitleBn = "শুধু যখন ব্যবহারকারী নিজে সিঙ্ক বাটনে চাপবেন",
        intervalMinutes = 0L
    ),
    ON_DATA_CHANGE(
        key = "ON_DATA_CHANGE",
        titleBn = "ডেটা পরিবর্তনে অটো-সিঙ্ক (On Data Change)",
        subtitleBn = "শিক্ষার্থী, উপস্থিতি বা সেটিংস সেভ হলে সাথে সাথে ব্যাকগ্রাউন্ডে স্বয়ংক্রিয় সিঙ্ক",
        intervalMinutes = 0L
    ),
    INTERVAL_15_MIN(
        key = "INTERVAL_15_MIN",
        titleBn = "প্রতি ১৫ মিনিট (15 Minutes)",
        subtitleBn = "প্রতি ১৫ মিনিট পরপর ব্যাকগ্রাউন্ডে স্বয়ংক্রিয় সিঙ্ক",
        intervalMinutes = 15L
    ),
    INTERVAL_30_MIN(
        key = "INTERVAL_30_MIN",
        titleBn = "প্রতি ৩০ মিনিট (30 Minutes)",
        subtitleBn = "প্রতি ৩০ মিনিট পরপর ব্যাকগ্রাউন্ডে স্বয়ংক্রিয় সিঙ্ক",
        intervalMinutes = 30L
    ),
    INTERVAL_1_HOUR(
        key = "INTERVAL_1_HOUR",
        titleBn = "প্রতি ১ ঘণ্টা (1 Hour)",
        subtitleBn = "প্রতি ১ ঘণ্টা পরপর ক্লাউডে ব্যাকআপ সিঙ্ক",
        intervalMinutes = 60L
    ),
    INTERVAL_6_HOURS(
        key = "INTERVAL_6_HOURS",
        titleBn = "প্রতি ৬ ঘণ্টা (6 Hours)",
        subtitleBn = "প্রতি ৬ ঘণ্টা পরপর ক্লাউড সিঙ্ক",
        intervalMinutes = 360L
    ),
    INTERVAL_DAILY(
        key = "INTERVAL_DAILY",
        titleBn = "দৈনিক ১ বার (Daily)",
        subtitleBn = "প্রতিদিন একবার স্বয়ংক্রিয় ক্লাউড ব্যাকআপ",
        intervalMinutes = 1440L
    );

    companion object {
        fun fromKey(key: String?): AutoSyncMode {
            return values().firstOrNull { it.key == key } ?: ON_DATA_CHANGE
        }
    }
}

data class SyncContentPreferences(
    val autoSyncMode: AutoSyncMode = AutoSyncMode.ON_DATA_CHANGE,
    val syncImages: Boolean = true,
    val syncPdfsAndDocuments: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val isAutoSyncRunning: Boolean = false,
    val lastAutoSyncStatus: String? = null
) {
    val lastSyncFormatted: String
        get() {
            if (lastSyncTimestamp <= 0L) return "এখনো সিঙ্ক করা হয়নি"
            val now = System.currentTimeMillis()
            val diffMs = now - lastSyncTimestamp
            val diffMinutes = diffMs / (1000 * 60)
            return when {
                diffMinutes < 1 -> "এইমাত্র"
                diffMinutes < 60 -> "${BanglaUtils.toBanglaDigits(diffMinutes.toInt())} মিনিট আগে"
                diffMinutes < 1440 -> "${BanglaUtils.toBanglaDigits((diffMinutes / 60).toInt())} ঘণ্টা আগে"
                else -> {
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    BanglaUtils.toBanglaDigits(sdf.format(Date(lastSyncTimestamp)))
                }
            }
        }
}
