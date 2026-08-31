package com.example.util

import java.util.regex.Pattern

/**
 * Intelligent Multi-Document OCR Classifier & Structured Formatter.
 * Automatically parses, categorizes, formats, and validates extracted OCR text
 * from various Bangladesh document types (Birth Registration Certificates, NID,
 * Student Admission Forms, Marksheets/Certificates, and General Documents).
 */
object DocumentOcrFormatter {

    enum class DocCategory(val titleBn: String, val titleEn: String, val icon: String) {
        BIRTH_CERTIFICATE("জন্ম নিবন্ধন সনদ", "Birth Registration Certificate", "📜"),
        NATIONAL_ID("জাতীয় পরিচয়পত্র (NID)", "National ID Card", "🪪"),
        STUDENT_ADMISSION("শিক্ষার্থী ভর্তি / প্রত্যয়নপত্র", "Student Admission / Testimonial", "🎓"),
        ACADEMIC_MARKSHEET("নম্বরপত্র / গ্রেডশিট", "Academic Marksheet / Result", "📊"),
        GENERAL_DOCUMENT("সাধারণ অফিসিয়াল নথি", "General Document", "📄")
    }

    data class FormattedField(
        val key: String,
        val labelBn: String,
        val labelEn: String,
        val value: String,
        val category: String = "সাধারণ",
        val isImportant: Boolean = false
    )

    data class FormattedDocResult(
        val category: DocCategory,
        val documentTitleBn: String,
        val documentTitleEn: String,
        val issuingAuthority: String,
        val fields: List<FormattedField>,
        val formattedSummary: String,
        val studentInfo: GoogleDriveOcrHelper.ParsedStudentInfo,
        val rawText: String
    )

    /**
     * Main entry point to format raw OCR text into structured document result
     */
    fun formatOcrText(rawText: String): FormattedDocResult {
        val cleanRaw = rawText.trim()
        val detectedCategory = detectCategory(cleanRaw)

        return when (detectedCategory) {
            DocCategory.BIRTH_CERTIFICATE -> formatBirthCertificate(cleanRaw)
            DocCategory.NATIONAL_ID -> formatNationalId(cleanRaw)
            DocCategory.STUDENT_ADMISSION -> formatStudentAdmission(cleanRaw)
            DocCategory.ACADEMIC_MARKSHEET -> formatAcademicMarksheet(cleanRaw)
            DocCategory.GENERAL_DOCUMENT -> formatGeneralDocument(cleanRaw)
        }
    }

    /**
     * Detects the category of document based on high-confidence keywords
     */
    fun detectCategory(text: String): DocCategory {
        val lower = text.lowercase()
        val banglaDigitsAndText = text.replace(" ", "")

        // 1. Birth Registration Certificate
        if (text.contains("জন্ম নিবন্ধন") || lower.contains("birth registration") ||
            lower.contains("birth and death") || text.contains("bdris") || lower.contains("bdris.gov.bd") ||
            (lower.contains("birth") && lower.contains("certificate")) ||
            text.contains("Office of the Registrar") || text.contains("ইউনিয়ন পরিষদ") && text.contains("জন্ম")
        ) {
            return DocCategory.BIRTH_CERTIFICATE
        }

        // 2. National ID (NID)
        if (text.contains("জাতীয় পরিচয়পত্র") || lower.contains("national id card") ||
            lower.contains("nid") || text.contains("গণপ্রজাতন্ত্রী বাংলাদেশ সরকার") && text.contains("NID No") ||
            text.contains("স্মার্ট কার্ড") || lower.contains("blood group") && (lower.contains("card") || text.contains("ভোটার"))
        ) {
            return DocCategory.NATIONAL_ID
        }

        // 3. Marksheet / Academic Result
        if (text.contains("নম্বরপত্র") || text.contains("গ্রেড") || lower.contains("marksheet") ||
            lower.contains("transcript") || lower.contains("grade sheet") || lower.contains("gpa") ||
            text.contains("ফলাফল") || text.contains("জিপিএ") || text.contains("শিক্ষা বোর্ড") ||
            lower.contains("education board")
        ) {
            return DocCategory.ACADEMIC_MARKSHEET
        }

        // 4. Student Admission / School Testimonial
        if (text.contains("ভর্তি") || text.contains("প্রত্যয়নপত্র") || text.contains("প্রশংসাপত্র") ||
            text.contains("শিক্ষার্থী") || text.contains("বিদ্যালয়") || text.contains("মাদ্রাসা") ||
            lower.contains("admission form") || lower.contains("testimonial") || lower.contains("student") ||
            text.contains("শ্রেণি") && text.contains("রোল")
        ) {
            return DocCategory.STUDENT_ADMISSION
        }

        // Default
        return DocCategory.GENERAL_DOCUMENT
    }

    /**
     * Parse and format BDRIS Birth Registration Certificate
     */
    private fun formatBirthCertificate(text: String): FormattedDocResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullJoined = lines.joinToString(" ")

        var brn = ""
        var dateOfBirth = ""
        var dateOfReg = ""
        var dateOfIssue = ""
        var gender = ""
        var nameBn = ""
        var nameEn = ""
        var fatherBn = ""
        var fatherEn = ""
        var motherBn = ""
        var motherEn = ""
        var nationalityBn = "বাংলাদেশী"
        var nationalityEn = "Bangladeshi"
        var placeOfBirthBn = ""
        var placeOfBirthEn = ""
        var addressBn = ""
        var addressEn = ""
        var issuingOffice = ""

        // Extract BRN (17 digit number)
        val brnRegex = Regex("(?:Birth Registration Number|BRN|নিবন্ধন নম্বর|নম্বর)[:\\s]*([0-9\u09E6-\u09EF]{16,20})", RegexOption.IGNORE_CASE)
        val brnMatch = brnRegex.find(fullJoined)
        if (brnMatch != null) {
            brn = BanglaUtils.toEnglishDigits(brnMatch.groupValues[1].trim())
        } else {
            // Fallback find any 17-digit contiguous number
            val seventeenDigit = Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").find(fullJoined)
            if (seventeenDigit != null) {
                brn = BanglaUtils.toEnglishDigits(seventeenDigit.value)
            }
        }

        // Extract Dates (dd/MM/yyyy)
        val dateMatches = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})").findAll(fullJoined).map { it.value }.toList()
        if (dateMatches.isNotEmpty()) {
            if (dateMatches.size >= 1) dateOfReg = dateMatches[0]
            if (dateMatches.size >= 2) dateOfIssue = dateMatches[1]
            if (dateMatches.size >= 3) dateOfBirth = dateMatches[2]
        }

        // If BRN has year (first 4 digits of 17 digit BRN), we can validate birth year
        if (brn.length == 17 && dateOfBirth.isBlank()) {
            val birthYear = brn.substring(0, 4)
            // check for word description like "Twenty Eighth of March Nineteen Ninety Six"
            val wordDateMatch = Regex("(?:Date of Birth|জন্ম তারিখ)[:\\s]*([A-Za-z0-9\\s,]+(?:Nineteen|Two Thousand|Twenty|Twenty-Eight|March|January|February|April|May|June|July|August|September|October|November|December)[A-Za-z0-9\\s,]*)", RegexOption.IGNORE_CASE).find(fullJoined)
            if (wordDateMatch != null) {
                dateOfBirth = wordDateMatch.groupValues[1].trim()
            } else {
                dateOfBirth = birthYear
            }
        }

        // Gender / Sex
        if (fullJoined.contains("Sex : Female", ignoreCase = true) || fullJoined.contains("Female", ignoreCase = true) || fullJoined.contains("নারী") || fullJoined.contains("ছাত্রী")) {
            gender = "নারী (Female)"
        } else if (fullJoined.contains("Sex : Male", ignoreCase = true) || fullJoined.contains("Male", ignoreCase = true) || fullJoined.contains("পুরুষ") || fullJoined.contains("ছাত্র")) {
            gender = "পুরুষ (Male)"
        } else {
            gender = "অনির্দিষ্ট"
        }

        // Extract Issuing Office
        for (i in 0 until minOf(8, lines.size)) {
            val line = lines[i]
            if (line.contains("Union Parishad", ignoreCase = true) || line.contains("ইউনিয়ন পরিষদ") ||
                line.contains("Pourashava", ignoreCase = true) || line.contains("পৌরসভা") ||
                line.contains("City Corporation", ignoreCase = true) || line.contains("সিটি কর্পোরেশন") ||
                line.contains("Office of the Registrar", ignoreCase = true) || line.contains("Chandanaish", ignoreCase = true)
            ) {
                if (issuingOffice.isBlank()) issuingOffice = line
                else issuingOffice += ", $line"
            }
        }

        // Extract Names (Line by line smart contextual scanning)
        for (i in 0 until lines.size) {
            val line = lines[i]
            val lower = line.lowercase()

            // Person Name
            if (line.contains("নাম") && !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("বিদ্যালয়") && !line.contains("কার্যালয়")) {
                val clean = cleanFieldVal(line, "নাম", "Name")
                if (clean.isNotBlank() && nameBn.isBlank()) {
                    nameBn = extractBanglaOnly(clean).ifBlank { clean }
                }
            }
            if (lower.contains("name") && !lower.contains("father") && !lower.contains("mother") && !lower.contains("school") && !lower.contains("office")) {
                val clean = cleanFieldVal(line, "Name", "নাম")
                if (clean.isNotBlank() && nameEn.isBlank()) {
                    nameEn = extractEnglishOnly(clean).ifBlank { clean }
                }
            }

            // Father Name
            if (line.contains("পিতা") || line.contains("পিতার নাম")) {
                val clean = cleanFieldVal(line, "পিতা", "পিতার নাম", "Father")
                if (clean.isNotBlank()) {
                    fatherBn = extractBanglaOnly(clean).ifBlank { clean }
                }
                // Check if next line contains English Father name
                if (i + 1 < lines.size && lines[i + 1].contains("Father", ignoreCase = true)) {
                    fatherEn = cleanFieldVal(lines[i + 1], "Father", "Father's Name")
                }
            }
            if (lower.contains("father") && fatherEn.isBlank()) {
                fatherEn = cleanFieldVal(line, "Father", "Father's Name", "পিতা")
            }

            // Mother Name
            if (line.contains("মাতা") || line.contains("মাতার নাম")) {
                val clean = cleanFieldVal(line, "মাতা", "মাতার নাম", "Mother")
                if (clean.isNotBlank()) {
                    motherBn = extractBanglaOnly(clean).ifBlank { clean }
                }
                // Check next line for Mother English
                if (i + 1 < lines.size && lines[i + 1].contains("Mother", ignoreCase = true)) {
                    motherEn = cleanFieldVal(lines[i + 1], "Mother", "Mother's Name")
                }
            }
            if (lower.contains("mother") && motherEn.isBlank()) {
                motherEn = cleanFieldVal(line, "Mother", "Mother's Name", "মাতা")
            }

            // Place of Birth
            if (line.contains("জন্মস্থান") || lower.contains("place of birth")) {
                val clean = cleanFieldVal(line, "জন্মস্থান", "Place of Birth", "Place of birth")
                if (clean.isNotBlank()) {
                    if (containsBangla(clean)) placeOfBirthBn = clean
                    else placeOfBirthEn = clean
                }
            }

            // Address
            if (line.contains("স্থায়ী ঠিকানা") || lower.contains("permanent address") || line.contains("ঠিকানা")) {
                val clean = cleanFieldVal(line, "স্থায়ী ঠিকানা", "Permanent Address", "ঠিকানা", "Address")
                if (clean.isNotBlank()) {
                    if (containsBangla(clean)) addressBn = clean
                    else addressEn = clean
                }
            }
        }

        // Multi-line Address builder if fragmented
        if (addressBn.isBlank() || addressBn.length < 15) {
            val addrSbBn = StringBuilder()
            val addrSbEn = StringBuilder()
            var startAddr = false
            for (line in lines) {
                if (line.contains("ঠিকানা") || line.contains("Address", ignoreCase = true) || line.contains("হাউজিং") || line.contains("ওয়ার্ড") || line.contains("সোসাইটি")) {
                    startAddr = true
                }
                if (startAddr && !line.contains("Seal", ignoreCase = true) && !line.contains("Signature", ignoreCase = true) && !line.contains("bdris", ignoreCase = true)) {
                    if (containsBangla(line)) {
                        addrSbBn.append(cleanFieldVal(line, "স্থায়ী ঠিকানা", "ঠিকানা")).append(" ")
                    } else if (line.length > 5) {
                        addrSbEn.append(cleanFieldVal(line, "Permanent Address", "Address")).append(" ")
                    }
                }
            }
            if (addrSbBn.isNotBlank() && addressBn.isBlank()) addressBn = addrSbBn.toString().trim()
            if (addrSbEn.isNotBlank() && addressEn.isBlank()) addressEn = addrSbEn.toString().trim()
        }

        // Fallbacks for names if bundled
        if (nameBn.isBlank()) {
            val nameMatch = Regex("(?:নাম|Name)[:\\s]*([^\n]+)").find(text)
            if (nameMatch != null) nameBn = nameMatch.groupValues[1].trim()
        }

        val fields = mutableListOf<FormattedField>()
        if (brn.isNotBlank()) {
            fields.add(FormattedField("brn", "জন্ম নিবন্ধন নম্বর", "Birth Reg No (BRN)", brn, "সনদ বিবরণ", true))
        }
        if (nameBn.isNotBlank()) {
            fields.add(FormattedField("name_bn", "শিক্ষার্থী/ব্যক্তির নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        }
        if (nameEn.isNotBlank()) {
            fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        }
        if (dateOfBirth.isNotBlank()) {
            fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dateOfBirth, "ব্যক্তিগত তথ্য", true))
        }
        if (gender.isNotBlank()) {
            fields.add(FormattedField("gender", "লিঙ্গ", "Sex / Gender", gender, "ব্যক্তিগত তথ্য"))
        }
        if (fatherBn.isNotBlank()) {
            fields.add(FormattedField("father_bn", "পিতার নাম (বাংলা)", "Father's Name (Bangla)", fatherBn, "পারিবারিক তথ্য", true))
        }
        if (fatherEn.isNotBlank()) {
            fields.add(FormattedField("father_en", "পিতার নাম (ইংরেজি)", "Father's Name (English)", fatherEn, "পারিবারিক তথ্য"))
        }
        if (motherBn.isNotBlank()) {
            fields.add(FormattedField("mother_bn", "মাতার নাম (বাংলা)", "Mother's Name (Bangla)", motherBn, "পারিবারিক তথ্য", true))
        }
        if (motherEn.isNotBlank()) {
            fields.add(FormattedField("mother_en", "মাতার নাম (ইংরেজি)", "Mother's Name (English)", motherEn, "পারিবারিক তথ্য"))
        }
        if (dateOfReg.isNotBlank()) {
            fields.add(FormattedField("reg_date", "নিবন্ধনের তারিখ", "Date of Registration", dateOfReg, "সনদ বিবরণ"))
        }
        if (dateOfIssue.isNotBlank()) {
            fields.add(FormattedField("issue_date", "প্রদানের তারিখ", "Date of Issuance", dateOfIssue, "সনদ বিবরণ"))
        }
        if (placeOfBirthBn.isNotBlank() || placeOfBirthEn.isNotBlank()) {
            val pVal = if (placeOfBirthBn.isNotBlank() && placeOfBirthEn.isNotBlank()) "$placeOfBirthBn ($placeOfBirthEn)" else placeOfBirthBn.ifBlank { placeOfBirthEn }
            fields.add(FormattedField("pob", "জন্মস্থান", "Place of Birth", pVal, "ঠিকানা"))
        }
        if (addressBn.isNotBlank()) {
            fields.add(FormattedField("address_bn", "স্থায়ী ঠিকানা (বাংলা)", "Permanent Address (BN)", addressBn, "ঠিকানা"))
        }
        if (addressEn.isNotBlank()) {
            fields.add(FormattedField("address_en", "স্থায়ী ঠিকানা (ইংরেজি)", "Permanent Address (EN)", addressEn, "ঠিকানা"))
        }
        if (issuingOffice.isNotBlank()) {
            fields.add(FormattedField("office", "প্রদানকারী কার্যালয়", "Issuing Office", issuingOffice, "সনদ বিবরণ"))
        }

        val primaryName = nameBn.ifBlank { nameEn }.ifBlank { "শিক্ষার্থী" }
        val primaryGender = if (gender.contains("Female") || gender.contains("নারী")) "ছাত্রী" else "ছাত্র"

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = primaryName,
            fatherName = fatherBn.ifBlank { fatherEn },
            motherName = motherBn.ifBlank { motherEn },
            birthDate = dateOfBirth,
            birthRegNumber = brn,
            studentClass = "১ম শ্রেণি",
            rollNumber = 1,
            mobile = "",
            address = addressBn.ifBlank { addressEn },
            village = placeOfBirthBn.ifBlank { placeOfBirthEn },
            gender = primaryGender
        )

        val summary = buildSummaryString(
            docTitle = "📜 জন্ম নিবন্ধন সনদ (BDRIS Certificate)",
            fields = fields
        )

        return FormattedDocResult(
            category = DocCategory.BIRTH_CERTIFICATE,
            documentTitleBn = "জন্ম নিবন্ধন সনদ",
            documentTitleEn = "Birth Registration Certificate",
            issuingAuthority = issuingOffice.ifBlank { "রেজিস্ট্রার কার্যালয়, জন্ম ও মৃত্যু নিবন্ধন" },
            fields = fields,
            formattedSummary = summary,
            studentInfo = studentInfo,
            rawText = text
        )
    }

    /**
     * Parse and format National ID Card (NID)
     */
    private fun formatNationalId(text: String): FormattedDocResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val full = lines.joinToString(" ")

        var nidNo = ""
        var nameBn = ""
        var nameEn = ""
        var fatherName = ""
        var motherName = ""
        var dob = ""
        var bloodGroup = ""
        var address = ""

        // NID No (10-digit Smart or 13/17-digit)
        val nidMatch = Regex("(?:NID No|NID|জাতীয় পরিচয়পত্র নং|নং)[:\\s]*([0-9\u09E6-\u09EF]{10,17})", RegexOption.IGNORE_CASE).find(full)
        if (nidMatch != null) {
            nidNo = BanglaUtils.toEnglishDigits(nidMatch.groupValues[1])
        } else {
            val tenDigit = Regex("\\b[0-9]{10}\\b").find(full)
            val seventeenDigit = Regex("\\b[0-9]{17}\\b").find(full)
            if (tenDigit != null) nidNo = tenDigit.value
            else if (seventeenDigit != null) nidNo = seventeenDigit.value
        }

        // DOB
        val dobMatch = Regex("(?:Date of Birth|জন্ম তারিখ)[:\\s]*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})", RegexOption.IGNORE_CASE).find(full)
        if (dobMatch != null) {
            dob = dobMatch.groupValues[1].trim()
        }

        // Blood group
        val bloodMatch = Regex("(?:Blood Group|রক্তের গ্রুপ)[:\\s]*([A-Za-z+-]+)", RegexOption.IGNORE_CASE).find(full)
        if (bloodMatch != null) {
            bloodGroup = bloodMatch.groupValues[1].trim()
        }

        for (line in lines) {
            if (line.contains("নাম") && !line.contains("পিতা") && !line.contains("মাতা") && nameBn.isBlank()) {
                nameBn = cleanFieldVal(line, "নাম", "Name")
            }
            if (line.contains("Name", ignoreCase = true) && !line.contains("Father", ignoreCase = true) && !line.contains("Mother", ignoreCase = true) && nameEn.isBlank()) {
                nameEn = cleanFieldVal(line, "Name", "নাম")
            }
            if ((line.contains("পিতা") || line.contains("Father", ignoreCase = true)) && fatherName.isBlank()) {
                fatherName = cleanFieldVal(line, "পিতা", "পিতার নাম", "Father", "Father's Name")
            }
            if ((line.contains("মাতা") || line.contains("Mother", ignoreCase = true)) && motherName.isBlank()) {
                motherName = cleanFieldVal(line, "মাতা", "মাতার নাম", "Mother", "Mother's Name")
            }
            if ((line.contains("ঠিকানা") || line.contains("Address", ignoreCase = true)) && address.isBlank()) {
                address = cleanFieldVal(line, "ঠিকানা", "Address", "বাসা/হোল্ডিং")
            }
        }

        val fields = mutableListOf<FormattedField>()
        if (nidNo.isNotBlank()) fields.add(FormattedField("nid", "জাতীয় পরিচয়পত্র নম্বর (NID)", "National ID No", nidNo, "পরিচিতি", true))
        if (nameBn.isNotBlank()) fields.add(FormattedField("name_bn", "নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        if (nameEn.isNotBlank()) fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        if (dob.isNotBlank()) fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dob, "ব্যক্তিগত তথ্য", true))
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য"))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (bloodGroup.isNotBlank()) fields.add(FormattedField("blood", "রক্তের গ্রুপ", "Blood Group", bloodGroup, "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = nameBn.ifBlank { nameEn },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dob,
            birthRegNumber = nidNo,
            studentClass = "১ম শ্রেণি",
            rollNumber = 1,
            mobile = "",
            address = address,
            village = "",
            gender = "ছাত্র"
        )

        return FormattedDocResult(
            category = DocCategory.NATIONAL_ID,
            documentTitleBn = "জাতীয় পরিচয়পত্র (NID)",
            documentTitleEn = "National ID Card",
            issuingAuthority = "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার / নির্বাচন কমিশন",
            fields = fields,
            formattedSummary = buildSummaryString("🪪 জাতীয় পরিচয়পত্র (NID)", fields),
            studentInfo = studentInfo,
            rawText = text
        )
    }

    /**
     * Parse and format Student Admission Form or School Testimonial
     */
    private fun formatStudentAdmission(text: String): FormattedDocResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val full = lines.joinToString(" ")

        var studentName = ""
        var studentClass = "১ম শ্রেণি"
        var rollNo = "1"
        var section = ""
        var schoolName = ""
        var guardianName = ""
        var fatherName = ""
        var motherName = ""
        var mobile = ""
        var address = ""
        var dob = ""
        var brn = ""
        var gpa = ""

        // Mobile
        val mobileMatch = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").find(full)
        if (mobileMatch != null) mobile = BanglaUtils.toEnglishDigits(mobileMatch.value)

        // Class & Roll
        for (line in lines) {
            val lower = line.lowercase()
            if (line.contains("বিদ্যালয়") || line.contains("স্কুল") || line.contains("মাদ্রাসা") || lower.contains("school") || lower.contains("madrasah")) {
                if (schoolName.isBlank() && !line.contains("প্রধান")) schoolName = line
            }
            if ((line.contains("শিক্ষার্থীর নাম") || line.contains("নাম") || lower.contains("student name")) && studentName.isBlank() && !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("বিদ্যালয়")) {
                studentName = cleanFieldVal(line, "শিক্ষার্থীর নাম", "নাম", "Student Name", "Name")
            }
            if (line.contains("শ্রেণি") || lower.contains("class")) {
                studentClass = extractClassFromLine(line)
            }
            if (line.contains("রোল") || line.contains("ক্রমিক") || lower.contains("roll")) {
                val rollMatch = Regex("[0-9\u09E6-\u09EF]{1,4}").find(line)
                if (rollMatch != null) rollNo = BanglaUtils.toEnglishDigits(rollMatch.value)
            }
            if (line.contains("শাখা") || lower.contains("section")) {
                section = cleanFieldVal(line, "শাখা", "Section")
            }
            if ((line.contains("পিতা") || lower.contains("father")) && fatherName.isBlank()) {
                fatherName = cleanFieldVal(line, "পিতার নাম", "পিতা", "Father's Name", "Father")
            }
            if ((line.contains("মাতা") || lower.contains("mother")) && motherName.isBlank()) {
                motherName = cleanFieldVal(line, "মাতার নাম", "মাতা", "Mother's Name", "Mother")
            }
            if ((line.contains("ঠিকানা") || lower.contains("address")) && address.isBlank()) {
                address = cleanFieldVal(line, "ঠিকানা", "Address", "গ্রাম")
            }
            if ((line.contains("জন্ম তারিখ") || lower.contains("dob") || lower.contains("birth")) && dob.isBlank()) {
                val dateMatch = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})").find(line)
                dob = dateMatch?.value ?: cleanFieldVal(line, "জন্ম তারিখ", "Date of Birth")
            }
            if ((line.contains("নিবন্ধন") || lower.contains("brn")) && brn.isBlank()) {
                val brnMatch = Regex("[0-9\u09E6-\u09EF]{10,18}").find(line)
                if (brnMatch != null) brn = BanglaUtils.toEnglishDigits(brnMatch.value)
            }
            if (line.contains("জিপিএ") || lower.contains("gpa")) {
                val gpaMatch = Regex("[0-5]\\.[0-9]{1,2}").find(line)
                if (gpaMatch != null) gpa = gpaMatch.value
            }
        }

        val fields = mutableListOf<FormattedField>()
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "একাডেমিক তথ্য", true))
        fields.add(FormattedField("class", "শ্রেণি", "Class", studentClass, "একাডেমিক তথ্য", true))
        fields.add(FormattedField("roll", "রোল নং", "Roll No", rollNo, "একাডেমিক তথ্য", true))
        if (section.isNotBlank()) fields.add(FormattedField("section", "শাখা", "Section", section, "একাডেমিক তথ্য"))
        if (schoolName.isNotBlank()) fields.add(FormattedField("school", "প্রতিষ্ঠান / বিদ্যালয়", "Institution", schoolName, "প্রতিষ্ঠান"))
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য"))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (mobile.isNotBlank()) fields.add(FormattedField("mobile", "মোবাইল নম্বর", "Mobile Contact", mobile, "যোগাযোগ", true))
        if (dob.isNotBlank()) fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dob, "ব্যক্তিগত তথ্য"))
        if (brn.isNotBlank()) fields.add(FormattedField("brn", "জন্ম নিবন্ধন নং", "BRN", brn, "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))
        if (gpa.isNotBlank()) fields.add(FormattedField("gpa", "প্রাপ্ত জিপিএ / ফলাফল", "GPA / Result", gpa, "ফলাফল"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = studentName.ifBlank { "শিক্ষার্থী" },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dob,
            birthRegNumber = brn,
            studentClass = studentClass,
            rollNumber = rollNo.toIntOrNull() ?: 1,
            mobile = mobile,
            address = address,
            village = "",
            gender = if (studentName.contains("আক্তার") || studentName.contains("খাতুন") || studentName.contains("বেগম") || studentName.contains("Chowdhury")) "ছাত্রী" else "ছাত্র"
        )

        return FormattedDocResult(
            category = DocCategory.STUDENT_ADMISSION,
            documentTitleBn = "শিক্ষার্থী ভর্তি / প্রত্যয়নপত্র",
            documentTitleEn = "Student Admission / Testimonial",
            issuingAuthority = schoolName.ifBlank { "বিদ্যালয় কর্তৃপক্ষ" },
            fields = fields,
            formattedSummary = buildSummaryString("🎓 শিক্ষার্থী ভর্তি / প্রত্যয়নপত্র", fields),
            studentInfo = studentInfo,
            rawText = text
        )
    }

    /**
     * Parse and format Academic Marksheet or Grade Sheet
     */
    private fun formatAcademicMarksheet(text: String): FormattedDocResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val full = lines.joinToString(" ")

        var examName = "বার্ষিক মূল্যায়ন / পাবলিক পরীক্ষা"
        var studentName = ""
        var rollNo = ""
        var regNo = ""
        var session = ""
        var gpa = ""
        var school = ""

        val gpaMatch = Regex("(?:GPA|জিপিএ|Grade Point)[:\\s]*([0-5]\\.[0-9]{1,2})", RegexOption.IGNORE_CASE).find(full)
        if (gpaMatch != null) gpa = gpaMatch.groupValues[1]

        for (line in lines) {
            val lower = line.lowercase()
            if (line.contains("পরীক্ষা") || lower.contains("examination") || lower.contains("exam")) {
                if (examName == "বার্ষিক মূল্যায়ন / পাবলিক পরীক্ষা") examName = line
            }
            if ((line.contains("নাম") || lower.contains("name")) && studentName.isBlank() && !line.contains("পিতা") && !line.contains("বিদ্যালয়")) {
                studentName = cleanFieldVal(line, "নাম", "Name", "Student's Name")
            }
            if ((line.contains("রোল") || lower.contains("roll")) && rollNo.isBlank()) {
                val rMatch = Regex("[0-9\u09E6-\u09EF]{1,8}").find(line)
                if (rMatch != null) rollNo = BanglaUtils.toEnglishDigits(rMatch.value)
            }
            if ((line.contains("রেজিস্ট্রেশন") || lower.contains("reg")) && regNo.isBlank()) {
                val regMatch = Regex("[0-9\u09E6-\u09EF]{6,16}").find(line)
                if (regMatch != null) regNo = BanglaUtils.toEnglishDigits(regMatch.value)
            }
            if ((line.contains("সেশন") || line.contains("সন") || lower.contains("session") || lower.contains("year")) && session.isBlank()) {
                val yearMatch = Regex("(?:20|19)[0-9]{2}").find(line)
                if (yearMatch != null) session = yearMatch.value
            }
            if ((line.contains("বিদ্যালয়") || line.contains("কলেজ") || line.contains("স্কুল") || lower.contains("school") || lower.contains("college")) && school.isBlank()) {
                school = line
            }
        }

        val fields = mutableListOf<FormattedField>()
        fields.add(FormattedField("exam", "পরীক্ষার নাম", "Examination", examName, "ফলাফল বিবরণ", true))
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "শিক্ষার্থী তথ্য", true))
        if (rollNo.isNotBlank()) fields.add(FormattedField("roll", "রোল নম্বর", "Roll Number", rollNo, "শিক্ষার্থী তথ্য", true))
        if (regNo.isNotBlank()) fields.add(FormattedField("reg", "রেজিস্ট্রেশন নম্বর", "Registration No", regNo, "শিক্ষার্থী তথ্য"))
        if (gpa.isNotBlank()) fields.add(FormattedField("gpa", "প্রাপ্ত জিপিএ (GPA)", "GPA Obtained", gpa, "ফলাফল বিবরণ", true))
        if (session.isNotBlank()) fields.add(FormattedField("session", "পরীক্ষার সন / সেশন", "Session / Year", session, "ফলাফল বিবরণ"))
        if (school.isNotBlank()) fields.add(FormattedField("school", "শিক্ষা প্রতিষ্ঠান", "Institute", school, "প্রতিষ্ঠান"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = studentName.ifBlank { "শিক্ষার্থী" },
            studentClass = "১ম শ্রেণি",
            rollNumber = rollNo.toIntOrNull() ?: 1,
            birthRegNumber = regNo
        )

        return FormattedDocResult(
            category = DocCategory.ACADEMIC_MARKSHEET,
            documentTitleBn = "নম্বরপত্র / গ্রেডশিট",
            documentTitleEn = "Academic Marksheet / Result",
            issuingAuthority = school.ifBlank { "শিক্ষা বোর্ড / প্রতিষ্ঠান" },
            fields = fields,
            formattedSummary = buildSummaryString("📊 নম্বরপত্র / গ্রেডশিট", fields),
            studentInfo = studentInfo,
            rawText = text
        )
    }

    /**
     * Parse and format General Official Documents
     */
    private fun formatGeneralDocument(text: String): FormattedDocResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fields = mutableListOf<FormattedField>()

        var title = if (lines.isNotEmpty()) lines.first().take(60) else "অফিসিয়াল নথি"

        // Search for dates
        val dates = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})").findAll(text).map { it.value }.toList()
        if (dates.isNotEmpty()) {
            fields.add(FormattedField("date", "উল্লেখিত তারিখ", "Detected Date", dates.joinToString(", "), "ডকুমেন্ট তথ্য", true))
        }

        // Search for phone numbers
        val phones = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").findAll(text).map { BanglaUtils.toEnglishDigits(it.value) }.distinct().toList()
        if (phones.isNotEmpty()) {
            fields.add(FormattedField("phone", "মোবাইল / যোগাযোগ", "Contact Number", phones.joinToString(", "), "যোগাযোগ", true))
        }

        // Search for key-value lines
        var count = 1
        for (line in lines.take(15)) {
            if (line.contains(":") || line.contains("ঃ") || line.contains("=")) {
                val parts = line.split(":", "ঃ", "=")
                if (parts.size >= 2 && parts[0].trim().length in 2..30 && parts[1].trim().isNotBlank()) {
                    fields.add(
                        FormattedField(
                            key = "field_$count",
                            labelBn = parts[0].trim(),
                            labelEn = parts[0].trim(),
                            value = parts.drop(1).joinToString(":").trim(),
                            category = "মূল তথ্য"
                        )
                    )
                    count++
                }
            }
        }

        val studentInfo = GoogleDriveOcrHelper.parseStudentFromOcrText(text)

        return FormattedDocResult(
            category = DocCategory.GENERAL_DOCUMENT,
            documentTitleBn = "সাধারণ নথি / সনদ",
            documentTitleEn = "General Document",
            issuingAuthority = "প্রাতিষ্ঠানিক কার্যালয়",
            fields = fields,
            formattedSummary = buildSummaryString("📄 সাধারণ নথি", fields),
            studentInfo = studentInfo,
            rawText = text
        )
    }

    private fun buildSummaryString(docTitle: String, fields: List<FormattedField>): String {
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine(docTitle)
        sb.appendLine("========================================")
        var currentCat = ""
        fields.forEach { f ->
            if (f.category != currentCat) {
                currentCat = f.category
                sb.appendLine("\n[$currentCat]")
            }
            sb.appendLine("• ${f.labelBn}: ${f.value}")
        }
        sb.appendLine("========================================")
        return sb.toString()
    }

    private fun cleanFieldVal(line: String, vararg prefixes: String): String {
        var res = line
        for (prefix in prefixes) {
            res = res.replace(prefix, "", ignoreCase = true)
        }
        return res.trim(' ', ':', 'ঃ', '-', '=', '–', '\t', ',', '.')
    }

    private fun extractBanglaOnly(text: String): String {
        return text.filter { it in '\u0980'..'\u09FF' || it == ' ' || it in '০'..'৯' }.trim()
    }

    private fun extractEnglishOnly(text: String): String {
        return text.filter { it in 'A'..'Z' || it in 'a'..'z' || it == ' ' || it in '0'..'9' }.trim()
    }

    private fun containsBangla(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
    }

    private fun extractClassFromLine(line: String): String {
        val lower = line.lowercase()
        return when {
            line.contains("১ম") || line.contains("প্রথম") || lower.contains("class 1") || lower.contains("class-1") || lower.contains("class i") -> "১ম শ্রেণি"
            line.contains("২য়") || line.contains("২য়") || line.contains("দ্বিতীয়") || lower.contains("class 2") || lower.contains("class-2") || lower.contains("class ii") -> "২য় শ্রেণি"
            line.contains("৩য়") || line.contains("৩য়") || line.contains("তৃতীয়") || lower.contains("class 3") || lower.contains("class-3") || lower.contains("class iii") -> "৩য় শ্রেণি"
            line.contains("৪র্থ") || line.contains("চতুর্থ") || lower.contains("class 4") || lower.contains("class-4") || lower.contains("class iv") -> "৪র্থ শ্রেণি"
            line.contains("৫ম") || line.contains("পঞ্চম") || lower.contains("class 5") || lower.contains("class-5") || lower.contains("class v") -> "৫ম শ্রেণি"
            line.contains("প্রাক") || lower.contains("pre-primary") || lower.contains("nursery") || lower.contains("kg") || lower.contains("শিশু") -> "প্রাক-প্রাথমিক ৪+"
            else -> "১ম শ্রেণি"
        }
    }
}
