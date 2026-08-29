package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Master Data Structure representing the full zero-server multi-tenant school database.
 * Serialized to and from 'school_database_master.json' stored on Google Drive.
 */
data class SchoolDatabaseModel(
    val schemaVersion: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val schoolInfo: SchoolInfoModel,
    val usersList: List<UserModel> = emptyList(),
    val studentsList: List<StudentModel> = emptyList(),
    val attendanceList: List<AttendanceRecordModel> = emptyList()
) {

    fun toJson(indent: Boolean = true): String {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        root.put("lastUpdated", lastUpdated)
        root.put("appId", "ANWESHA_SCHOOL_MANAGEMENT")

        // School Info
        val infoObj = JSONObject().apply {
            put("schoolName", schoolInfo.schoolName)
            put("eiinCode", schoolInfo.eiinCode)
            put("adminName", schoolInfo.adminName)
            put("adminEmail", schoolInfo.adminEmail)
            put("adminPhone", schoolInfo.adminPhone)
            put("createdDate", schoolInfo.createdDate)
            put("address", schoolInfo.address)
            put("tagline", schoolInfo.tagline)
            put("logoUri", schoolInfo.logoUri ?: "")
            put("headTeacherName", schoolInfo.headTeacherName)
            put("internalVillages", schoolInfo.internalVillages)
        }
        root.put("schoolInfo", infoObj)

        // Users List
        val usersArr = JSONArray()
        for (u in usersList) {
            val uObj = JSONObject().apply {
                put("userId", u.userId)
                put("name", u.name)
                put("email", u.email)
                put("phone", u.phone)
                put("role", u.role)
                put("status", u.status)
                put("securityPinHash", u.securityPinHash)
                put("createdDate", u.createdDate)
            }
            usersArr.put(uObj)
        }
        root.put("usersList", usersArr)

        // Students List
        val studentsArr = JSONArray()
        for (s in studentsList) {
            val sObj = JSONObject().apply {
                put("studentId", s.studentId)
                put("studentClass", s.studentClass)
                put("section", s.section)
                put("rollNumber", s.rollNumber)
                put("name", s.name)
                put("parentContact", s.parentContact)
                put("fatherName", s.fatherName)
                put("motherName", s.motherName)
                put("gender", s.gender)
                put("village", s.village)
                put("birthDate", s.birthDate)
                put("birthRegNumber", s.birthRegNumber)
                put("address", s.address)
                put("academicYear", s.academicYear)
                put("isSpecialNeeds", s.isSpecialNeeds)
                put("status", s.status)
                put("photoUri", s.photoUri ?: "")
                put("customValuesJson", s.customValuesJson)
            }
            studentsArr.put(sObj)
        }
        root.put("studentsList", studentsArr)

        // Attendance List
        val attendanceArr = JSONArray()
        for (a in attendanceList) {
            val aObj = JSONObject().apply {
                put("id", a.id)
                put("date", a.date)
                put("className", a.className)
                put("rollNumber", a.rollNumber ?: -1)
                put("studentId", a.studentId ?: "")
                put("status", a.status)
                put("remarks", a.remarks ?: "")
                put("presentBoys", a.presentBoys)
                put("presentGirls", a.presentGirls)
                put("absentBoys", a.absentBoys)
                put("absentGirls", a.absentGirls)
            }
            attendanceArr.put(aObj)
        }
        root.put("attendanceList", attendanceArr)

        return if (indent) root.toString(2) else root.toString()
    }

    companion object {
        fun fromJson(jsonString: String): SchoolDatabaseModel? {
            return try {
                val root = JSONObject(jsonString)
                val schemaVersion = root.optInt("schemaVersion", 1)
                val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())

                // Parse School Info
                val infoObj = root.optJSONObject("schoolInfo") ?: JSONObject()
                val schoolInfo = SchoolInfoModel(
                    schoolName = infoObj.optString("schoolName", "অন্বেষা বিদ্যালয়"),
                    eiinCode = infoObj.optString("eiinCode", "123456"),
                    adminName = infoObj.optString("adminName", "প্রধান শিক্ষক"),
                    adminEmail = infoObj.optString("adminEmail", ""),
                    adminPhone = infoObj.optString("adminPhone", ""),
                    createdDate = infoObj.optString("createdDate", currentDateString()),
                    address = infoObj.optString("address", ""),
                    tagline = infoObj.optString("tagline", "জ্ঞান, মনন ও স্বপ্নের সোপান"),
                    logoUri = if (infoObj.optString("logoUri").isNotBlank()) infoObj.optString("logoUri") else null,
                    headTeacherName = infoObj.optString("headTeacherName", infoObj.optString("adminName", "")),
                    internalVillages = infoObj.optString("internalVillages", "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর")
                )

                // Parse Users
                val usersList = mutableListOf<UserModel>()
                val usersArr = root.optJSONArray("usersList")
                if (usersArr != null) {
                    for (i in 0 until usersArr.length()) {
                        val uObj = usersArr.getJSONObject(i)
                        usersList.add(
                            UserModel(
                                userId = uObj.optString("userId", "USR-$i"),
                                name = uObj.optString("name", "User $i"),
                                email = uObj.optString("email", ""),
                                phone = uObj.optString("phone", ""),
                                role = uObj.optString("role", "Admin"),
                                status = uObj.optString("status", "Active"),
                                securityPinHash = uObj.optString("securityPinHash", ""),
                                createdDate = uObj.optString("createdDate", currentDateString())
                            )
                        )
                    }
                }

                // Parse Students
                val studentsList = mutableListOf<StudentModel>()
                val studentsArr = root.optJSONArray("studentsList") ?: root.optJSONArray("students")
                if (studentsArr != null) {
                    for (i in 0 until studentsArr.length()) {
                        val sObj = studentsArr.getJSONObject(i)
                        val sId = sObj.optString("studentId", sObj.optString("id", "STU-$i"))
                        val sContact = sObj.optString("parentContact", sObj.optString("mobile", ""))
                        studentsList.add(
                            StudentModel(
                                studentId = sId,
                                studentClass = sObj.optString("studentClass", "১ম শ্রেণি"),
                                section = sObj.optString("section", "ক"),
                                rollNumber = sObj.optInt("rollNumber", i + 1),
                                name = sObj.optString("name", "শিক্ষার্থী"),
                                parentContact = sContact,
                                fatherName = sObj.optString("fatherName", ""),
                                motherName = sObj.optString("motherName", ""),
                                gender = sObj.optString("gender", "ছাত্র"),
                                village = sObj.optString("village", ""),
                                birthDate = sObj.optString("birthDate", ""),
                                birthRegNumber = sObj.optString("birthRegNumber", ""),
                                address = sObj.optString("address", ""),
                                academicYear = sObj.optString("academicYear", "২০২৬"),
                                isSpecialNeeds = sObj.optBoolean("isSpecialNeeds", false),
                                status = sObj.optString("status", "Current"),
                                photoUri = if (sObj.optString("photoUri").isNotBlank()) sObj.optString("photoUri") else null,
                                customValuesJson = sObj.optString("customValuesJson", "{}")
                            )
                        )
                    }
                }

                // Parse Attendance
                val attendanceList = mutableListOf<AttendanceRecordModel>()
                val attendanceArr = root.optJSONArray("attendanceList")
                if (attendanceArr != null) {
                    for (i in 0 until attendanceArr.length()) {
                        val aObj = attendanceArr.getJSONObject(i)
                        val rNo = aObj.optInt("rollNumber", -1)
                        attendanceList.add(
                            AttendanceRecordModel(
                                id = aObj.optString("id", UUID.randomUUID().toString()),
                                date = aObj.optString("date", currentDateString()),
                                className = aObj.optString("className", "১ম শ্রেণি"),
                                rollNumber = if (rNo > 0) rNo else null,
                                studentId = if (aObj.optString("studentId").isNotBlank()) aObj.optString("studentId") else null,
                                status = aObj.optString("status", "Present"),
                                remarks = aObj.optString("remarks", null),
                                presentBoys = aObj.optInt("presentBoys", 0),
                                presentGirls = aObj.optInt("presentGirls", 0),
                                absentBoys = aObj.optInt("absentBoys", 0),
                                absentGirls = aObj.optInt("absentGirls", 0)
                            )
                        )
                    }
                }

                SchoolDatabaseModel(
                    schemaVersion = schemaVersion,
                    lastUpdated = lastUpdated,
                    schoolInfo = schoolInfo,
                    usersList = usersList,
                    studentsList = studentsList,
                    attendanceList = attendanceList
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun createInitial(
            schoolName: String,
            eiinCode: String,
            adminName: String,
            adminEmail: String,
            adminPhone: String,
            pinHash: String
        ): SchoolDatabaseModel {
            val adminUser = UserModel(
                userId = "ADMIN-${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}",
                name = adminName,
                email = adminEmail,
                phone = adminPhone,
                role = "Admin",
                status = "Active",
                securityPinHash = pinHash,
                createdDate = currentDateString()
            )

            val info = SchoolInfoModel(
                schoolName = schoolName,
                eiinCode = eiinCode,
                adminName = adminName,
                adminEmail = adminEmail,
                adminPhone = adminPhone,
                createdDate = currentDateString(),
                headTeacherName = adminName
            )

            return SchoolDatabaseModel(
                schemaVersion = 1,
                lastUpdated = System.currentTimeMillis(),
                schoolInfo = info,
                usersList = listOf(adminUser),
                studentsList = emptyList(),
                attendanceList = emptyList()
            )
        }

        private fun currentDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}

data class SchoolInfoModel(
    val schoolName: String,
    val eiinCode: String,
    val adminName: String,
    val adminEmail: String,
    val adminPhone: String,
    val createdDate: String,
    val address: String = "",
    val tagline: String = "জ্ঞান, মনন ও স্বপ্নের সোপান",
    val logoUri: String? = null,
    val headTeacherName: String = "",
    val internalVillages: String = "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"
)

data class UserModel(
    val userId: String,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val role: String, // "Admin", "Teacher", "Parent"
    val status: String = "Active", // "Active", "Pending"
    val securityPinHash: String = "",
    val createdDate: String = ""
)

data class StudentModel(
    val studentId: String,
    val studentClass: String,
    val section: String = "ক",
    val rollNumber: Int,
    val name: String,
    val parentContact: String,
    val fatherName: String = "",
    val motherName: String = "",
    val gender: String = "ছাত্র",
    val village: String = "",
    val birthDate: String = "",
    val birthRegNumber: String = "",
    val address: String = "",
    val academicYear: String = "২০২৬",
    val isSpecialNeeds: Boolean = false,
    val status: String = "Current",
    val photoUri: String? = null,
    val customValuesJson: String = "{}"
)

data class AttendanceRecordModel(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val className: String,
    val rollNumber: Int? = null,
    val studentId: String? = null,
    val status: String = "Present", // "Present", "Absent", "Late"
    val remarks: String? = null,
    val presentBoys: Int = 0,
    val presentGirls: Int = 0,
    val absentBoys: Int = 0,
    val absentGirls: Int = 0
)
