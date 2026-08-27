package com.example.util

import com.example.data.local.entity.*
import org.json.JSONObject
import java.util.UUID

enum class CsvDataType(val displayNameBn: String, val displayNameEn: String) {
    STUDENTS("শিক্ষার্থী তথ্য", "Students Data"),
    TEACHERS("শিক্ষক ও স্টাফ", "Teachers & Staff"),
    ATTENDANCE("উপস্থিতি রেকর্ড", "Attendance Records"),
    EXAM_RESULTS("পরীক্ষার ফলাফল", "Exam Results"),
    ROUTINE("ক্লাস রুটিন", "Class Routine")
}

data class CsvFieldDef(
    val key: String,
    val labelBn: String,
    val labelEn: String,
    val isRequired: Boolean = false,
    val isCustom: Boolean = false,
    val aliases: List<String> = emptyList()
)

object CsvUtils {

    const val SKIP_FIELD_KEY = "__SKIP__"
    private const val UTF8_BOM = "\uFEFF"

    /**
     * Returns standard and custom field descriptors for the given CsvDataType.
     */
    fun getFieldsForType(type: CsvDataType, customFields: List<CustomFieldEntity> = emptyList()): List<CsvFieldDef> {
        return when (type) {
            CsvDataType.STUDENTS -> {
                val standard = listOf(
                    CsvFieldDef("studentClass", "শ্রেণি", "Class", isRequired = true, aliases = listOf("শ্রেণি", "শ্রেণী", "ক্লাস", "class", "grade", "standard", "class_name", "classname", "student_class")),
                    CsvFieldDef("rollNumber", "রোল নম্বর", "Roll Number", isRequired = true, aliases = listOf("রোল", "রোল নং", "রোল নম্বর", "roll", "roll no", "roll_no", "roll_number", "rollno", "class_roll")),
                    CsvFieldDef("name", "শিক্ষার্থীর নাম", "Student Name", isRequired = true, aliases = listOf("নাম", "শিক্ষার্থীর নাম", "ছাত্র/ছাত্রীর নাম", "পূর্ণ নাম", "name", "student name", "student_name", "full name", "fullname")),
                    CsvFieldDef("fatherName", "পিতার নাম", "Father's Name", aliases = listOf("পিতা", "পিতার নাম", "বাবার নাম", "father", "father name", "father's name", "fathers_name", "father_name")),
                    CsvFieldDef("motherName", "মাতার নাম", "Mother's Name", aliases = listOf("মাতা", "মাতার নাম", "মায়ের নাম", "mother", "mother name", "mother's name", "mothers_name", "mother_name")),
                    CsvFieldDef("mobile", "মোবাইল নম্বর", "Mobile Number", aliases = listOf("মোবাইল", "মোবাইল নম্বর", "ফোন", "ফোন নম্বর", "contact", "mobile", "mobile no", "phone", "phone number", "cell", "guardian mobile", "parent contact")),
                    CsvFieldDef("birthDate", "জন্ম তারিখ", "Date of Birth", aliases = listOf("জন্ম তারিখ", "জন্মতারিখ", "জন্মদিন", "dob", "birth date", "birthdate", "date of birth")),
                    CsvFieldDef("village", "গ্রাম / এলাকা", "Village / Area", aliases = listOf("গ্রাম", "এলাকা", "বাসস্থান", "ঠিকানা", "village", "area", "address")),
                    CsvFieldDef("gender", "লিঙ্গ", "Gender", aliases = listOf("লিঙ্গ", "ছাত্র/ছাত্রী", "gender", "sex")),
                    CsvFieldDef("section", "শাখা", "Section", aliases = listOf("শাখা", "সেকশন", "section")),
                    CsvFieldDef("birthRegNumber", "জন্ম নিবন্ধন নম্বর", "Birth Reg No", aliases = listOf("জন্ম নিবন্ধন", "জন্ম নিবন্ধন নম্বর", "birth reg", "birth registration", "birth_reg_no", "brn", "nid")),
                    CsvFieldDef("academicYear", "শিক্ষাবর্ষ", "Academic Year", aliases = listOf("শিক্ষাবর্ষ", "বছর", "academic year", "year", "session")),
                    CsvFieldDef("address", "স্থায়ী ঠিকানা", "Address", aliases = listOf("ঠিকানা", "স্থায়ী ঠিকানা", "address", "permanent address")),
                    CsvFieldDef("status", "অবস্থা", "Status", aliases = listOf("অবস্থা", "ভর্তি অবস্থা", "status", "enrollment status")),
                    CsvFieldDef("isSpecialNeeds", "বিশেষ চাহিদা সম্পন্ন", "Special Needs", aliases = listOf("বিশেষ চাহিদা", "প্রতিবন্ধী", "special needs", "disability")),
                    CsvFieldDef("id", "আইডি নম্বর", "Student ID", aliases = listOf("আইডি", "শিক্ষার্থী আইডি", "id", "student id", "student_id", "stu_id"))
                )

                val custom = customFields.map { cf ->
                    CsvFieldDef(
                        key = "custom_${cf.name}",
                        labelBn = "${cf.name} (কাস্টম)",
                        labelEn = "${cf.name} (Custom)",
                        isCustom = true,
                        aliases = listOf(cf.name.lowercase().trim(), cf.name.trim())
                    )
                }
                standard + custom
            }

            CsvDataType.TEACHERS -> listOf(
                CsvFieldDef("name", "শিক্ষক / কর্মচারীর নাম", "Staff / Teacher Name", isRequired = true, aliases = listOf("নাম", "শিক্ষকের নাম", "স্টাফ নাম", "পূর্ণ নাম", "name", "teacher name", "staff name", "full name")),
                CsvFieldDef("role", "পদবী / ভূমিকা", "Designation / Role", isRequired = true, aliases = listOf("পদবী", "পদবি", "ভূমিকা", "পদ", "role", "designation", "position", "user role")),
                CsvFieldDef("phone", "মোবাইল নম্বর", "Phone / Mobile", aliases = listOf("মোবাইল", "মোবাইল নম্বর", "ফোন", "phone", "mobile", "contact", "cell")),
                CsvFieldDef("email", "ইমেইল ঠিকানা", "Email Address", aliases = listOf("ইমেইল", "ই-মেইল", "email", "e-mail", "mail")),
                CsvFieldDef("status", "স্ট্যাটাস", "Status", aliases = listOf("অবস্থা", "স্ট্যাটাস", "status", "active status")),
                CsvFieldDef("userId", "ইউজার আইডি", "User ID", aliases = listOf("আইডি", "ইউজার আইডি", "id", "user id", "userid"))
            )

            CsvDataType.ATTENDANCE -> listOf(
                CsvFieldDef("date", "তারিখ (YYYY-MM-DD)", "Date", isRequired = true, aliases = listOf("তারিখ", "দিন", "date")),
                CsvFieldDef("className", "শ্রেণি", "Class", isRequired = true, aliases = listOf("শ্রেণি", "শ্রেণী", "class", "className")),
                CsvFieldDef("presentBoys", "উপস্থিত ছাত্র", "Present Boys", aliases = listOf("উপস্থিত ছাত্র", "উপস্থিত বালক", "present boys", "present_boys")),
                CsvFieldDef("presentGirls", "উপস্থিত ছাত্রী", "Present Girls", aliases = listOf("উপস্থিত ছাত্রী", "উপস্থিত বালিকা", "present girls", "present_girls")),
                CsvFieldDef("absentBoys", "অনুপস্থিত ছাত্র", "Absent Boys", aliases = listOf("অনুপস্থিত ছাত্র", "অনুপস্থিত বালক", "absent boys", "absent_boys")),
                CsvFieldDef("absentGirls", "অনুপস্থিত ছাত্রী", "Absent Girls", aliases = listOf("অনুপস্থিত ছাত্রী", "অনুপস্থিত বালিকা", "absent girls", "absent_girls")),
                CsvFieldDef("notes", "মন্তব্য", "Notes", aliases = listOf("মন্তব্য", "নোট", "notes", "remarks"))
            )

            CsvDataType.EXAM_RESULTS -> listOf(
                CsvFieldDef("examName", "পরীক্ষার নাম", "Exam Name", isRequired = true, aliases = listOf("পরীক্ষার নাম", "পরীক্ষা", "exam", "exam name", "exam_name")),
                CsvFieldDef("className", "শ্রেণি", "Class", isRequired = true, aliases = listOf("শ্রেণি", "শ্রেণী", "class", "className")),
                CsvFieldDef("rollNumber", "রোল নম্বর", "Roll Number", isRequired = true, aliases = listOf("রোল", "রোল নং", "roll", "roll number", "roll_no")),
                CsvFieldDef("studentName", "শিক্ষার্থীর নাম", "Student Name", aliases = listOf("নাম", "শিক্ষার্থীর নাম", "student name", "name")),
                CsvFieldDef("subject", "বিষয়", "Subject", isRequired = true, aliases = listOf("বিষয়", "বিষয়", "subject", "sub")),
                CsvFieldDef("marks", "প্রাপ্ত নম্বর", "Marks", isRequired = true, aliases = listOf("প্রাপ্ত নম্বর", "নম্বর", "মার্কস", "marks", "score", "total_marks")),
                CsvFieldDef("grade", "গ্রেড", "Grade", aliases = listOf("গ্রেড", "লেটার গ্রেড", "grade", "letter grade")),
                CsvFieldDef("gpa", "জিপিএ", "GPA", aliases = listOf("জিপিএ", "পয়েন্ট", "gpa", "point")),
                CsvFieldDef("section", "শাখা", "Section", aliases = listOf("শাখা", "সেকশন", "section")),
                CsvFieldDef("date", "তারিখ", "Date", aliases = listOf("তারিখ", "date"))
            )

            CsvDataType.ROUTINE -> listOf(
                CsvFieldDef("className", "শ্রেণি", "Class", isRequired = true, aliases = listOf("শ্রেণি", "শ্রেণী", "class")),
                CsvFieldDef("day", "বার / দিন", "Day", isRequired = true, aliases = listOf("বার", "দিন", "day")),
                CsvFieldDef("periodName", "পিরিয়ড", "Period", isRequired = true, aliases = listOf("পিরিয়ড", "পিরিয়ড", "ঘণ্টা", "period", "period name")),
                CsvFieldDef("subject", "বিষয়", "Subject", isRequired = true, aliases = listOf("বিষয়", "বিষয়", "subject")),
                CsvFieldDef("teacher", "শিক্ষক", "Teacher", aliases = listOf("শিক্ষক", "শিক্ষকের নাম", "teacher")),
                CsvFieldDef("startTime", "শুরুর সময়", "Start Time", aliases = listOf("শুরুর সময়", "সময়", "start time", "time")),
                CsvFieldDef("endTime", "শেষের সময়", "End Time", aliases = listOf("শেষের সময়", "end time")),
                CsvFieldDef("roomNo", "কক্ষ নম্বর", "Room No", aliases = listOf("কক্ষ", "কক্ষ নম্বর", "room", "room no")),
                CsvFieldDef("routineType", "রুটিনের ধরণ", "Routine Type", aliases = listOf("ধরণ", "রুটিন ধরণ", "routine type", "type"))
            )
        }
    }

    /**
     * Automatically matches raw CSV column headers to the best target field definition.
     */
    fun autoDetectColumnMapping(
        headers: List<String>,
        availableFields: List<CsvFieldDef>
    ): Map<Int, String?> {
        val mapping = mutableMapOf<Int, String?>()
        val assignedFields = mutableSetOf<String>()

        headers.forEachIndexed { index, rawHeader ->
            val cleanHeader = rawHeader.trim().lowercase()
                .replace("_", " ")
                .replace("-", " ")
                .replace(":", "")
                .replace(".", "")
                .trim()

            var matchedKey: String? = null

            // 1. Exact match on clean header or field key
            for (field in availableFields) {
                if (assignedFields.contains(field.key)) continue

                val cleanKey = field.key.lowercase()
                val cleanLabelBn = field.labelBn.lowercase().replace(" (কাস্টম)", "")
                val cleanLabelEn = field.labelEn.lowercase().replace(" (custom)", "")

                if (cleanHeader == cleanKey || cleanHeader == cleanLabelBn || cleanHeader == cleanLabelEn) {
                    matchedKey = field.key
                    break
                }
            }

            // 2. Match against aliases
            if (matchedKey == null) {
                for (field in availableFields) {
                    if (assignedFields.contains(field.key)) continue

                    if (field.aliases.any { alias ->
                            val cleanAlias = alias.lowercase().trim()
                            cleanHeader == cleanAlias ||
                                    cleanHeader.contains(cleanAlias) ||
                                    cleanAlias.contains(cleanHeader)
                        }) {
                        matchedKey = field.key
                        break
                    }
                }
            }

            mapping[index] = matchedKey
            if (matchedKey != null) {
                assignedFields.add(matchedKey)
            }
        }

        return mapping
    }

    /**
     * Parses RFC-4180 CSV string into a header row and list of data rows.
     */
    fun parseCsvContent(csvContent: String): Pair<List<String>, List<List<String>>> {
        val cleanContent = if (csvContent.startsWith(UTF8_BOM)) csvContent.substring(1) else csvContent
        val lines = mutableListOf<List<String>>()

        var currentField = StringBuilder()
        var currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        // Auto-detect delimiter (, or ;)
        val firstLineSample = cleanContent.lines().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = if (firstLineSample.count { it == ';' } > firstLineSample.count { it == ',' }) ';' else ','

        while (i < cleanContent.length) {
            val c = cleanContent[i]

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < cleanContent.length && cleanContent[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        currentRow.add(currentField.toString().trim())
                        currentField = StringBuilder()
                    }
                    '\r' -> {
                        // Skip \r in \r\n
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString().trim())
                        if (currentRow.any { it.isNotBlank() }) {
                            lines.add(currentRow)
                        }
                        currentRow = mutableListOf()
                        currentField = StringBuilder()
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }

        // Final row if not empty
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                lines.add(currentRow)
            }
        }

        if (lines.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        val headerRow = lines[0]
        val dataRows = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()
        return Pair(headerRow, dataRows)
    }

    /**
     * Converts mapped rows to StudentEntity objects.
     */
    fun buildStudentsFromMappedRows(
        dataRows: List<List<String>>,
        columnMapping: Map<Int, String?>,
        existingCount: Int = 0
    ): List<StudentEntity> {
        val students = mutableListOf<StudentEntity>()

        dataRows.forEachIndexed { rowIndex, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed

            var id = "STU-2026-${String.format("%03d", existingCount + rowIndex + 1)}"
            var studentClass = "১ম শ্রেণি"
            var section = "ক"
            var rollNumber = rowIndex + 1
            var name = ""
            var fatherName = ""
            var motherName = ""
            var birthDate = ""
            var mobile = ""
            var village = ""
            var academicYear = "২০২৬"
            var address = ""
            var birthRegNumber = ""
            var gender = "ছাত্র"
            var isSpecialNeeds = false
            var status = "Current"
            val customValues = mutableMapOf<String, String>()

            row.forEachIndexed { colIndex, cellValue ->
                val fieldKey = columnMapping[colIndex] ?: return@forEachIndexed
                val trimmedVal = cellValue.trim()
                if (trimmedVal.isBlank()) return@forEachIndexed

                if (fieldKey.startsWith("custom_")) {
                    val customName = fieldKey.removePrefix("custom_")
                    customValues[customName] = trimmedVal
                    return@forEachIndexed
                }

                when (fieldKey) {
                    "id" -> id = trimmedVal
                    "studentClass" -> studentClass = normalizeClass(trimmedVal)
                    "section" -> section = trimmedVal
                    "rollNumber" -> {
                        val parsedRoll = BanglaUtils.toEnglishDigits(trimmedVal).filter { it.isDigit() }.toIntOrNull()
                        if (parsedRoll != null && parsedRoll > 0) rollNumber = parsedRoll
                    }
                    "name" -> name = trimmedVal
                    "fatherName" -> fatherName = trimmedVal
                    "motherName" -> motherName = trimmedVal
                    "birthDate" -> birthDate = trimmedVal
                    "mobile" -> mobile = trimmedVal
                    "village" -> village = trimmedVal
                    "academicYear" -> academicYear = trimmedVal
                    "address" -> address = trimmedVal
                    "birthRegNumber" -> birthRegNumber = trimmedVal
                    "gender" -> gender = if (trimmedVal.contains("মেয়ে") || trimmedVal.contains("ছাত্রী") || trimmedVal.contains("female", ignoreCase = true) || trimmedVal.contains("girl", ignoreCase = true)) "ছাত্রী" else "ছাত্র"
                    "isSpecialNeeds" -> isSpecialNeeds = trimmedVal.contains("হ্যাঁ") || trimmedVal.contains("yes", ignoreCase = true) || trimmedVal.contains("true", ignoreCase = true)
                    "status" -> status = trimmedVal
                }
            }

            if (name.isNotBlank()) {
                val customJson = if (customValues.isNotEmpty()) {
                    val obj = JSONObject()
                    customValues.forEach { (k, v) -> obj.put(k, v) }
                    obj.toString()
                } else "{}"

                students.add(
                    StudentEntity(
                        id = id,
                        studentClass = studentClass,
                        section = section,
                        rollNumber = rollNumber,
                        name = name,
                        fatherName = fatherName,
                        motherName = motherName,
                        birthDate = birthDate,
                        mobile = mobile,
                        village = village,
                        academicYear = academicYear,
                        address = address,
                        birthRegNumber = birthRegNumber,
                        gender = gender,
                        isSpecialNeeds = isSpecialNeeds,
                        status = status,
                        customValuesJson = customJson
                    )
                )
            }
        }

        return students
    }

    /**
     * Converts mapped rows to UserEntity (Teachers & Staff) objects.
     */
    fun buildUsersFromMappedRows(
        dataRows: List<List<String>>,
        columnMapping: Map<Int, String?>
    ): List<UserEntity> {
        val users = mutableListOf<UserEntity>()

        dataRows.forEachIndexed { rowIndex, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed

            var userId = "USER-${UUID.randomUUID().toString().take(8)}"
            var name = ""
            var email = ""
            var phone = ""
            var role = "Teacher"
            var status = "Active"

            row.forEachIndexed { colIndex, cellValue ->
                val fieldKey = columnMapping[colIndex] ?: return@forEachIndexed
                val trimmedVal = cellValue.trim()
                if (trimmedVal.isBlank()) return@forEachIndexed

                when (fieldKey) {
                    "userId" -> userId = trimmedVal
                    "name" -> name = trimmedVal
                    "email" -> email = trimmedVal
                    "phone" -> phone = trimmedVal
                    "role" -> role = when {
                        trimmedVal.contains("প্রধান", ignoreCase = true) || trimmedVal.contains("head", ignoreCase = true) -> "Head Teacher"
                        trimmedVal.contains("অ্যাডমিন", ignoreCase = true) || trimmedVal.contains("admin", ignoreCase = true) -> "Admin"
                        trimmedVal.contains("স্টাফ", ignoreCase = true) || trimmedVal.contains("staff", ignoreCase = true) -> "Staff"
                        else -> "Teacher"
                    }
                    "status" -> status = if (trimmedVal.contains("নিষ্ক্রিয়") || trimmedVal.contains("inactive", ignoreCase = true)) "Inactive" else "Active"
                }
            }

            if (name.isNotBlank()) {
                users.add(
                    UserEntity(
                        userId = userId,
                        name = name,
                        email = email,
                        phone = phone,
                        role = role,
                        status = status,
                        createdDate = "২০২৬"
                    )
                )
            }
        }

        return users
    }

    /**
     * Normalizes Bengali and English class names.
     */
    private fun normalizeClass(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("প্রাক") || lower.contains("play") || lower.contains("nursery") || lower.contains("kg") -> "প্রাক-প্রাথমিক ৪+"
            lower.contains("১") || lower.contains("1") || lower.contains("one") || lower.contains("first") -> "১ম শ্রেণি"
            lower.contains("২") || lower.contains("2") || lower.contains("two") || lower.contains("second") -> "২য় শ্রেণি"
            lower.contains("৩") || lower.contains("3") || lower.contains("three") || lower.contains("third") -> "৩য় শ্রেণি"
            lower.contains("৪") || lower.contains("4") || lower.contains("four") || lower.contains("fourth") -> "৪র্থ শ্রেণি"
            lower.contains("৫") || lower.contains("5") || lower.contains("five") || lower.contains("fifth") -> "৫ম শ্রেণি"
            else -> raw
        }
    }

    /**
     * Generates RFC-4180 CSV string with UTF-8 BOM for students.
     */
    fun exportStudentsToCsv(
        students: List<StudentEntity>,
        selectedFieldKeys: List<String>,
        customFields: List<CustomFieldEntity> = emptyList()
    ): String {
        val allFields = getFieldsForType(CsvDataType.STUDENTS, customFields)
        val activeFields = allFields.filter { selectedFieldKeys.contains(it.key) }

        val sb = StringBuilder()
        sb.append(UTF8_BOM)

        // Headers
        sb.append(activeFields.joinToString(",") { escapeCsvCell(it.labelBn) }).append("\n")

        // Rows
        students.forEach { s ->
            val customMap = parseCustomValues(s.customValuesJson)
            val rowValues = activeFields.map { field ->
                val rawVal = when (field.key) {
                    "id" -> s.id
                    "studentClass" -> s.studentClass
                    "section" -> s.section
                    "rollNumber" -> s.rollNumber.toString()
                    "name" -> s.name
                    "fatherName" -> s.fatherName
                    "motherName" -> s.motherName
                    "birthDate" -> s.birthDate
                    "mobile" -> s.mobile
                    "village" -> s.village
                    "academicYear" -> s.academicYear
                    "address" -> s.address
                    "birthRegNumber" -> s.birthRegNumber
                    "gender" -> s.gender
                    "isSpecialNeeds" -> if (s.isSpecialNeeds) "হ্যাঁ" else "না"
                    "status" -> s.status
                    else -> {
                        if (field.key.startsWith("custom_")) {
                            val customName = field.key.removePrefix("custom_")
                            customMap[customName] ?: ""
                        } else ""
                    }
                }
                escapeCsvCell(rawVal)
            }
            sb.append(rowValues.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    /**
     * Generates CSV for Teachers & Staff.
     */
    fun exportTeachersToCsv(users: List<UserEntity>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.append("নাম,পদবী,মোবাইল,ইমেইল,অবস্থা,ইউজার আইডি\n")
        users.forEach { u ->
            sb.append(
                listOf(u.name, u.role, u.phone, u.email, u.status, u.userId)
                    .joinToString(",") { escapeCsvCell(it) }
            ).append("\n")
        }
        return sb.toString()
    }

    /**
     * Generates sample template CSV.
     */
    fun getSampleCsvTemplate(type: CsvDataType): String {
        return when (type) {
            CsvDataType.STUDENTS -> {
                "$UTF8_BOM" +
                        "শ্রেণি,রোল,শিক্ষার্থীর নাম,পিতার নাম,মাতার নাম,মোবাইল,গ্রাম,জন্ম তারিখ,লিঙ্গ\n" +
                        "১ম শ্রেণি,১,আব্দুল্লাহ আল নোমান,মোঃ রফিকুল ইসলাম,নাসিমা আক্তার,01711000111,রামপুর,2019-01-15,ছাত্র\n" +
                        "১ম শ্রেণি,২,ফাতেমা তুজ জোহরা,মোঃ জাকির হোসেন,রাবেয়া বেগম,01812345678,আমতলী,2019-03-22,ছাত্রী\n" +
                        "২য় শ্রেণি,১,মুহাম্মদ তানভীর,মোঃ মিজানুর রহমান,শিরিন আক্তার,01987654321,কৃষ্ণপুর,2018-05-10,ছাত্র"
            }
            CsvDataType.TEACHERS -> {
                "$UTF8_BOM" +
                        "নাম,পদবী,মোবাইল,ইমেইল,অবস্থা\n" +
                        "মোঃ আব্দুল কাদের,প্রধান শিক্ষক,01712345678,headmaster@anwesha.edu.bd,Active\n" +
                        "মোছাঃ রোকসানা খাতুন,সহকারী শিক্ষক,01819876543,roksana@anwesha.edu.bd,Active\n" +
                        "মোঃ জসিম উদ্দিন,সহকারী শিক্ষক,01911223344,jasim@anwesha.edu.bd,Active"
            }
            CsvDataType.ATTENDANCE -> {
                "$UTF8_BOM" +
                        "তারিখ,শ্রেণি,উপস্থিত ছাত্র,উপস্থিত ছাত্রী,অনুপস্থিত ছাত্র,অনুপস্থিত ছাত্রী,মন্তব্য\n" +
                        "2026-08-27,১ম শ্রেণি,18,17,2,1,স্বাভাবিক উপস্থিতি\n" +
                        "2026-08-27,২য় শ্রেণি,20,18,1,2,বৃষ্টির কারণে কিছু অনুপস্থিত"
            }
            CsvDataType.EXAM_RESULTS -> {
                "$UTF8_BOM" +
                        "পরীক্ষার নাম,শ্রেণি,রোল,শিক্ষার্থীর নাম,বিষয়,প্রাপ্ত নম্বর,গ্রেড,জিপিএ\n" +
                        "১ম সাময়িক পরীক্ষা ২০২৬,১ম শ্রেণি,১,আব্দুল্লাহ আল নোমান,বাংলা,92,A+,5.0\n" +
                        "১ম সাময়িক পরীক্ষা ২০২৬,১ম শ্রেণি,১,আব্দুল্লাহ আল নোমান,ইংরেজি,88,A+,5.0\n" +
                        "১ম সাময়িক পরীক্ষা ২০২৬,১ম শ্রেণি,২,ফাতেমা তুজ জোহরা,বাংলা,95,A+,5.0"
            }
            CsvDataType.ROUTINE -> {
                "$UTF8_BOM" +
                        "শ্রেণি,বার,পিরিয়ড,বিষয়,শিক্ষক,শুরুর সময়,শেষের সময়,কক্ষ\n" +
                        "১ম শ্রেণি,রবিবার,১ম পিরিয়ড,বাংলা,রোকসানা খাতুন,09:00 AM,09:45 AM,১০১\n" +
                        "১ম শ্রেণি,রবিবার,২য় পিরিয়ড,গণিত,জসিম উদ্দিন,09:45 AM,10:30 AM,১০১"
            }
        }
    }

    private fun escapeCsvCell(value: String): String {
        var str = value
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }

    private fun parseCustomValues(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
