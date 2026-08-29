package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class SegmentSyncStatus {
    SYNCED,             // File is synced and hash matches cloud
    MODIFIED_LOCALLY,   // Data changed locally, needs upload
    NEW_PENDING,        // Newly added segment, not yet on cloud
    SYNCING,            // Currently uploading
    SKIPPED_UNCHANGED,  // Upload skipped because unchanged
    ERROR               // Sync error
}

data class BackupSegmentItem(
    val segmentKey: String,
    val fileName: String,
    val titleBn: String,
    val recordCount: Int,
    val jsonContent: String,
    val contentHash: String = computeHash(jsonContent),
    val lastModified: Long = System.currentTimeMillis(),
    val status: SegmentSyncStatus = SegmentSyncStatus.NEW_PENDING
) {
    companion object {
        fun computeHash(content: String): String {
            return try {
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(content.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                content.hashCode().toString()
            }
        }
    }
}

data class SegmentManifestEntry(
    val segmentKey: String,
    val fileName: String,
    val titleBn: String,
    val recordCount: Int,
    val contentHash: String,
    val lastUpdated: Long
)

data class BackupManifestModel(
    val schoolName: String,
    val eiinCode: String,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val backupDateFormatted: String = "",
    val appVersion: String = "3.2",
    val totalRecords: Int = 0,
    val segments: Map<String, SegmentManifestEntry> = emptyMap()
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("schoolName", schoolName)
            put("eiinCode", eiinCode)
            put("backupTimestamp", backupTimestamp)
            put("backupDateFormatted", backupDateFormatted)
            put("appVersion", appVersion)
            put("totalRecords", totalRecords)

            val segsObj = JSONObject()
            segments.forEach { (key, entry) ->
                val entryObj = JSONObject().apply {
                    put("segmentKey", entry.segmentKey)
                    put("fileName", entry.fileName)
                    put("titleBn", entry.titleBn)
                    put("recordCount", entry.recordCount)
                    put("contentHash", entry.contentHash)
                    put("lastUpdated", entry.lastUpdated)
                }
                segsObj.put(key, entryObj)
            }
            put("segments", segsObj)
        }
        return root.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): BackupManifestModel? {
            return try {
                val root = JSONObject(jsonStr)
                val segmentsMap = mutableMapOf<String, SegmentManifestEntry>()
                val segsObj = root.optJSONObject("segments")
                if (segsObj != null) {
                    val keys = segsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val e = segsObj.getJSONObject(key)
                        segmentsMap[key] = SegmentManifestEntry(
                            segmentKey = e.optString("segmentKey", key),
                            fileName = e.optString("fileName", "$key.json"),
                            titleBn = e.optString("titleBn", key),
                            recordCount = e.optInt("recordCount", 0),
                            contentHash = e.optString("contentHash", ""),
                            lastUpdated = e.optLong("lastUpdated", 0L)
                        )
                    }
                }

                BackupManifestModel(
                    schoolName = root.optString("schoolName", ""),
                    eiinCode = root.optString("eiinCode", ""),
                    backupTimestamp = root.optLong("backupTimestamp", System.currentTimeMillis()),
                    backupDateFormatted = root.optString("backupDateFormatted", ""),
                    appVersion = root.optString("appVersion", "3.2"),
                    totalRecords = root.optInt("totalRecords", 0),
                    segments = segmentsMap
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
