package com.example.util

import java.util.regex.Pattern

/**
 * Intelligent Multi-Concept Document OCR Classifier & Structured Formatter.
 * Adheres strictly to the 3 Core Guidelines:
 * 1. Field & Data Selection: Only extract fields actually present on the document; no extra dummy fields.
 * 2. Number Handling: English numbers output both original English and converted Bangla versions; Bangla numbers remain unchanged.
 * 3. Date Handling: Raw scanned date is preserved verbatim; standard date (DD/MM/YYYY) is prepared for system/student entry.
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

    data class ScannedDate(
        val rawText: String,      // Scanned original verbatim date (e.g. "12 Jan 2026", "১২ই মার্চ ১৯৯৫")
        val standardDate: String  // Standard formatted date (e.g. "12/01/2026", "12/03/1995")
    ) {
        fun displayValue(): String {
            return if (rawText.isNotBlank()) {
                val dualRaw = formatDualNumber(rawText)
                if (dualRaw != standardDate && standardDate.isNotBlank()) {
                    dualRaw
                } else {
                    dualRaw
                }
            } else standardDate
        }
    }

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
     * Dual-number formatting:
     * - If English numbers exist: returns original English number with Bengali conversion in parentheses e.g. "2910321344525 (২৯১০৩২১৩৪৪৫২৫)"
     * - If already in Bengali: remains untouched without changes e.g. "২৯১০৩২১৩৪৪৫২৫"
     */
    fun formatDualNumber(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed
        val hasEnglishDigits = trimmed.any { it in '0'..'9' }
        val hasBanglaDigits = trimmed.any { it in '০'..'৯' }

        // If it contains English digits and does not yet contain Bangla or parenthetical expansion
        if (hasEnglishDigits && !hasBanglaDigits && !trimmed.contains("(") && !trimmed.contains("（")) {
            val banglaVersion = BanglaUtils.toBanglaDigits(trimmed)
            return "$trimmed ($banglaVersion)"
        }
        // If it's already in Bangla, keep it as is without modification
        return trimmed
    }

    /**
     * Main entry point to format raw OCR text into structured document result using multi-concept pipeline.
     */
    fun formatOcrText(rawText: String): FormattedDocResult {
        val cleanRaw = rawText.trim()
        val detectedCategory = detectCategory(cleanRaw)

        return when (detectedCategory) {
            DocCategory.BIRTH_CERTIFICATE -> formatBirthCertificateMultiConcept(cleanRaw)
            DocCategory.NATIONAL_ID -> formatNationalIdMultiConcept(cleanRaw)
            DocCategory.STUDENT_ADMISSION -> formatStudentAdmissionMultiConcept(cleanRaw)
            DocCategory.ACADEMIC_MARKSHEET -> formatAcademicMarksheetMultiConcept(cleanRaw)
            DocCategory.GENERAL_DOCUMENT -> formatGeneralDocumentMultiConcept(cleanRaw)
        }
    }

    /**
     * Detects document category using fuzzy keywords and structural features.
     */
    fun detectCategory(text: String): DocCategory {
        val lower = text.lowercase()
        val normalized = normalizeNoise(text)

        // 1. Birth Registration Certificate
        if (normalized.contains("জন্ম নিবন্ধন") || lower.contains("birth registration") ||
            lower.contains("birth and death") || lower.contains("bdris") ||
            (lower.contains("birth") && lower.contains("certificate")) ||
            lower.contains("office of the registrar") ||
            (normalized.contains("ইউনিয়ন পরিষদ") && (normalized.contains("জন্ম") || lower.contains("birth"))) ||
            Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").containsMatchIn(text) && (lower.contains("father") || normalized.contains("পিতা"))
        ) {
            return DocCategory.BIRTH_CERTIFICATE
        }

        // 2. National ID (NID)
        if (normalized.contains("জাতীয় পরিচয়পত্র") || lower.contains("national id") ||
            lower.contains("nid no") || lower.contains("smart card") ||
            (normalized.contains("গণপ্রজাতন্ত্রী বাংলাদেশ সরকার") && (lower.contains("nid") || normalized.contains("পরিচয়পত্র"))) ||
            (lower.contains("blood group") && (normalized.contains("ভোটার") || lower.contains("id card")))
        ) {
            return DocCategory.NATIONAL_ID
        }

        // 3. Marksheet / Academic Result
        if (normalized.contains("নম্বরপত্র") || normalized.contains("গ্রেড") || lower.contains("marksheet") ||
            lower.contains("transcript") || lower.contains("grade sheet") || lower.contains("gpa") ||
            normalized.contains("ফলাফল") || normalized.contains("জিপিএ") || normalized.contains("শিক্ষা board") || normalized.contains("শিক্ষা বোর্ড") ||
            lower.contains("education board")
        ) {
            return DocCategory.ACADEMIC_MARKSHEET
        }

        // 4. Student Admission / School Testimonial
        if (normalized.contains("ভর্তি") || normalized.contains("প্রত্যয়নপত্র") || normalized.contains("প্রশংসাপত্র") ||
            normalized.contains("শিক্ষার্থী") || normalized.contains("বিদ্যালয়") || normalized.contains("মাদ্রাসা") ||
            lower.contains("admission form") || lower.contains("testimonial") || lower.contains("student") ||
            (normalized.contains("শ্রেণি") && normalized.contains("রোল"))
        ) {
            return DocCategory.STUDENT_ADMISSION
        }

        return DocCategory.GENERAL_DOCUMENT
    }

    // =========================================================================
    // CONCEPT 1: BIRTH CERTIFICATE MULTI-CONCEPT PARSING
    // =========================================================================

    private fun formatBirthCertificateMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val normalizedLines = rawLines.map { normalizeLine(it) }
        val fullJoined = normalizedLines.joinToString(" \n ")

        // 1.1 BRN Extraction (17-digit number)
        val rawBrn = extract17DigitBrn(fullJoined)

        // 1.2 Scanned Dates extraction (Verbatim Raw + Standard Formats)
        val scannedDates = extractAllScannedDates(fullJoined)

        var dobDate: ScannedDate? = null
        var regDate: ScannedDate? = null
        var issueDate: ScannedDate? = null

        val birthYearFromBrn = if (rawBrn.length == 17) rawBrn.substring(0, 4) else null

        if (scannedDates.isNotEmpty()) {
            if (birthYearFromBrn != null) {
                val dobMatch = scannedDates.find { it.standardDate.endsWith(birthYearFromBrn) || it.rawText.contains(birthYearFromBrn) }
                if (dobMatch != null) {
                    dobDate = dobMatch
                    val remaining = scannedDates.filter { it != dobMatch }
                    if (remaining.isNotEmpty()) regDate = remaining.first()
                    if (remaining.size > 1) issueDate = remaining.last()
                } else {
                    val (d, r, i) = assignScannedDatesChronologically(scannedDates)
                    dobDate = d
                    regDate = r
                    issueDate = i
                }
            } else {
                val (d, r, i) = assignScannedDatesChronologically(scannedDates)
                dobDate = d
                regDate = r
                issueDate = i
            }
        }

        // Check for DOB written in words
        val dobInWords = extractDobInWords(fullJoined)

        // 1.3 Gender / Sex
        val gender = detectGender(fullJoined)

        // Step 2: Names Extractions
        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        var nameBn = labelMap["name_bn"].orEmpty()
        var nameEn = labelMap["name_en"].orEmpty()
        var fatherBn = labelMap["father_bn"].orEmpty()
        var fatherEn = labelMap["father_en"].orEmpty()
        var motherBn = labelMap["mother_bn"].orEmpty()
        var motherEn = labelMap["mother_en"].orEmpty()
        var placeOfBirth = labelMap["pob"].orEmpty()
        var permanentAddress = labelMap["perm_address"].orEmpty()
        var presentAddress = labelMap["pres_address"].orEmpty()
        var issuingOffice = labelMap["office"].orEmpty()

        if (nameBn.isBlank() || fatherBn.isBlank() || motherBn.isBlank()) {
            val deInterleaved = deInterleaveTwoColumnTable(rawLines)
            if (nameBn.isBlank() && deInterleaved.containsKey("name")) nameBn = deInterleaved["name"].orEmpty()
            if (fatherBn.isBlank() && deInterleaved.containsKey("father")) fatherBn = deInterleaved["father"].orEmpty()
            if (motherBn.isBlank() && deInterleaved.containsKey("mother")) motherBn = deInterleaved["mother"].orEmpty()
        }

        if (nameBn.isBlank() || fatherBn.isBlank() || motherBn.isBlank()) {
            val nameTriplets = detectSequentialNameTriplets(rawLines)
            if (nameBn.isBlank() && nameTriplets.isNotEmpty()) nameBn = nameTriplets[0]
            if (fatherBn.isBlank() && nameTriplets.size > 1) fatherBn = nameTriplets[1]
            if (motherBn.isBlank() && nameTriplets.size > 2) motherBn = nameTriplets[2]
        }

        if (nameBn.isNotBlank() && nameEn.isBlank()) {
            val (bn, en) = splitBilingualText(nameBn)
            if (en.isNotBlank()) {
                nameBn = bn
                nameEn = en
            }
        }
        if (fatherBn.isNotBlank() && fatherEn.isBlank()) {
            val (bn, en) = splitBilingualText(fatherBn)
            if (en.isNotBlank()) {
                fatherBn = bn
                fatherEn = en
            }
        }
        if (motherBn.isNotBlank() && motherEn.isBlank()) {
            val (bn, en) = splitBilingualText(motherBn)
            if (en.isNotBlank()) {
                motherBn = bn
                motherEn = en
            }
        }

        if (permanentAddress.isBlank()) {
            permanentAddress = extractAddressHeuristically(rawLines)
        }
        if (issuingOffice.isBlank()) {
            issuingOffice = extractIssuingOfficeHeuristically(rawLines)
        }

        // Assemble Structured Output - ONLY include fields present in document
        val fields = mutableListOf<FormattedField>()
        if (rawBrn.isNotBlank()) {
            fields.add(FormattedField("brn", "জন্ম নিবন্ধন নম্বর (BRN)", "Birth Reg No", formatDualNumber(rawBrn), "সনদ বিবরণ", true))
        }
        if (nameBn.isNotBlank()) fields.add(FormattedField("name_bn", "নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        if (nameEn.isNotBlank()) fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        if (dobDate != null && dobDate.rawText.isNotBlank()) {
            fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dobDate.displayValue(), "ব্যক্তিগত তথ্য", true))
        }
        if (dobInWords.isNotBlank()) fields.add(FormattedField("dob_words", "জন্ম তারিখ (কথায়)", "DOB (In Words)", dobInWords, "ব্যক্তিগত তথ্য"))
        if (gender.isNotBlank()) fields.add(FormattedField("gender", "লিঙ্গ", "Sex / Gender", gender, "ব্যক্তিগত তথ্য", true))
        if (fatherBn.isNotBlank()) fields.add(FormattedField("father_bn", "পিতার নাম (বাংলা)", "Father's Name (BN)", fatherBn, "পারিবারিক তথ্য", true))
        if (fatherEn.isNotBlank()) fields.add(FormattedField("father_en", "পিতার নাম (ইংরেজি)", "Father's Name (EN)", fatherEn, "পারিবারিক তথ্য"))
        if (motherBn.isNotBlank()) fields.add(FormattedField("mother_bn", "মাতার নাম (বাংলা)", "Mother's Name (BN)", motherBn, "পারিবারিক তথ্য", true))
        if (motherEn.isNotBlank()) fields.add(FormattedField("mother_en", "মাতার নাম (ইংরেজি)", "Mother's Name (EN)", motherEn, "পারিবারিক তথ্য"))
        if (regDate != null && regDate.rawText.isNotBlank()) {
            fields.add(FormattedField("reg_date", "নিবন্ধনের তারিখ", "Date of Registration", regDate.displayValue(), "সনদ বিবরণ"))
        }
        if (issueDate != null && issueDate.rawText.isNotBlank()) {
            fields.add(FormattedField("issue_date", "প্রদানের তারিখ", "Date of Issuance", issueDate.displayValue(), "সনদ বিবরণ"))
        }
        if (placeOfBirth.isNotBlank()) fields.add(FormattedField("pob", "জন্মস্থান", "Place of Birth", placeOfBirth, "ঠিকানা"))
        if (permanentAddress.isNotBlank()) fields.add(FormattedField("perm_address", "স্থায়ী ঠিকানা", "Permanent Address", permanentAddress, "ঠিকানা", true))
        if (presentAddress.isNotBlank()) fields.add(FormattedField("pres_address", "বর্তমান ঠিকানা", "Present Address", presentAddress, "ঠিকানা"))
        if (issuingOffice.isNotBlank()) fields.add(FormattedField("office", "প্রদানকারী কার্যালয়", "Issuing Authority", issuingOffice, "সনদ বিবরণ"))

        val studentGender = if (gender.contains("নারী") || gender.contains("Female") || gender.contains("ছাত্রী")) "ছাত্রী" else "ছাত্র"

        // Student Info for device entry: standard date is used
        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = nameBn.ifBlank { nameEn }.ifBlank { "শিক্ষার্থী" },
            fatherName = fatherBn.ifBlank { fatherEn },
            motherName = motherBn.ifBlank { motherEn },
            birthDate = dobDate?.standardDate.orEmpty(),
            birthRegNumber = rawBrn,
            studentClass = "১ম শ্রেণি",
            rollNumber = 1,
            mobile = extractPhoneNumber(fullJoined),
            address = permanentAddress.ifBlank { presentAddress },
            village = placeOfBirth,
            gender = studentGender
        )

        return FormattedDocResult(
            category = DocCategory.BIRTH_CERTIFICATE,
            documentTitleBn = "জন্ম নিবন্ধন সনদ (BDRIS)",
            documentTitleEn = "Birth Registration Certificate",
            issuingAuthority = issuingOffice.ifBlank { "রেজিস্ট্রার জেনারেলের কার্যালয়, জন্ম ও মৃত্যু নিবন্ধন" },
            fields = fields,
            formattedSummary = buildSummaryString("📜 জন্ম নিবন্ধন সনদ (BDRIS Certificate)", fields),
            studentInfo = studentInfo,
            rawText = text
        )
    }

    // =========================================================================
    // CONCEPT 2: NATIONAL ID CARD (NID) MULTI-CONCEPT PARSING
    // =========================================================================

    private fun formatNationalIdMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullJoined = rawLines.joinToString(" \n ")

        // NID Number
        val rawNidNo = extractNidNumber(fullJoined)

        // Scanned Date of Birth
        val scannedDates = extractAllScannedDates(fullJoined)
        val dobDate = scannedDates.firstOrNull()

        // Blood Group
        val bloodGroup = extractBloodGroup(fullJoined)

        // Names & Address
        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        var nameBn = labelMap["name_bn"].orEmpty()
        var nameEn = labelMap["name_en"].orEmpty()
        var fatherName = labelMap["father_bn"].orEmpty().ifBlank { labelMap["father_en"].orEmpty() }
        var motherName = labelMap["mother_bn"].orEmpty().ifBlank { labelMap["mother_en"].orEmpty() }
        var address = labelMap["perm_address"].orEmpty().ifBlank { labelMap["pres_address"].orEmpty() }

        if (nameBn.isBlank() || fatherName.isBlank()) {
            val triplets = detectSequentialNameTriplets(rawLines)
            if (nameBn.isBlank() && triplets.isNotEmpty()) nameBn = triplets[0]
            if (fatherName.isBlank() && triplets.size > 1) fatherName = triplets[1]
            if (motherName.isBlank() && triplets.size > 2) motherName = triplets[2]
        }

        if (address.isBlank()) {
            address = extractAddressHeuristically(rawLines)
        }

        val fields = mutableListOf<FormattedField>()
        if (rawNidNo.isNotBlank()) {
            fields.add(FormattedField("nid", "জাতীয় পরিচয়পত্র নম্বর (NID)", "National ID No", formatDualNumber(rawNidNo), "পরিচিতি", true))
        }
        if (nameBn.isNotBlank()) fields.add(FormattedField("name_bn", "নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        if (nameEn.isNotBlank()) fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        if (dobDate != null && dobDate.rawText.isNotBlank()) {
            fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dobDate.displayValue(), "ব্যক্তিগত তথ্য", true))
        }
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য", true))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (bloodGroup.isNotBlank()) fields.add(FormattedField("blood", "রক্তের গ্রুপ", "Blood Group", bloodGroup, "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = nameBn.ifBlank { nameEn }.ifBlank { "নাগরিক" },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dobDate?.standardDate.orEmpty(),
            birthRegNumber = rawNidNo,
            studentClass = "১ম শ্রেণি",
            rollNumber = 1,
            mobile = extractPhoneNumber(fullJoined),
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

    // =========================================================================
    // CONCEPT 3: STUDENT ADMISSION / TESTIMONIAL MULTI-CONCEPT PARSING
    // =========================================================================

    private fun formatStudentAdmissionMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val full = rawLines.joinToString(" \n ")

        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        var studentName = labelMap["name_bn"].orEmpty().ifBlank { labelMap["name_en"].orEmpty() }
        var fatherName = labelMap["father_bn"].orEmpty().ifBlank { labelMap["father_en"].orEmpty() }
        var motherName = labelMap["mother_bn"].orEmpty().ifBlank { labelMap["mother_en"].orEmpty() }
        var studentClass = labelMap["student_class"].orEmpty().ifBlank { extractClassFromTextOnlyIfPresent(full) }
        var rollNo = labelMap["roll_no"].orEmpty().ifBlank { extractRollNumberOnlyIfPresent(full) }
        var mobile = labelMap["mobile"].orEmpty().ifBlank { extractPhoneNumber(full) }
        var address = labelMap["perm_address"].orEmpty()

        if (studentName.isBlank() || fatherName.isBlank()) {
            val triplets = detectSequentialNameTriplets(rawLines)
            if (studentName.isBlank() && triplets.isNotEmpty()) studentName = triplets[0]
            if (fatherName.isBlank() && triplets.size > 1) fatherName = triplets[1]
            if (motherName.isBlank() && triplets.size > 2) motherName = triplets[2]
        }

        val schoolName = extractSchoolName(rawLines)
        val dobDate = extractAllScannedDates(full).firstOrNull()
        val brn = extract17DigitBrn(full)

        val fields = mutableListOf<FormattedField>()
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "একাডেমিক তথ্য", true))
        if (studentClass.isNotBlank()) fields.add(FormattedField("class", "শ্রেণি", "Class", studentClass, "একাডেমিক তথ্য", true))
        if (rollNo.isNotBlank()) fields.add(FormattedField("roll", "রোল নং", "Roll No", formatDualNumber(rollNo), "একাডেমিক তথ্য", true))
        if (schoolName.isNotBlank()) fields.add(FormattedField("school", "প্রতিষ্ঠান / বিদ্যালয়", "Institution", schoolName, "প্রতিষ্ঠান", true))
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য"))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (mobile.isNotBlank()) fields.add(FormattedField("mobile", "মোবাইল নম্বর", "Mobile Contact", formatDualNumber(mobile), "যোগাযোগ", true))
        if (dobDate != null && dobDate.rawText.isNotBlank()) {
            fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dobDate.displayValue(), "ব্যক্তিগত তথ্য"))
        }
        if (brn.isNotBlank()) fields.add(FormattedField("brn", "জন্ম নিবন্ধন নং", "BRN", formatDualNumber(brn), "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))

        val studentGender = if (studentName.contains("আক্তার") || studentName.contains("খাতুন") || studentName.contains("বেগম") || studentName.contains("Khatun") || studentName.contains("Akter") || studentName.contains("Begum")) "ছাত্রী" else "ছাত্র"

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = studentName.ifBlank { "শিক্ষার্থী" },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dobDate?.standardDate.orEmpty(),
            birthRegNumber = brn,
            studentClass = studentClass.ifBlank { "১ম শ্রেণি" },
            rollNumber = BanglaUtils.toEnglishDigits(rollNo).toIntOrNull() ?: 1,
            mobile = mobile,
            address = address,
            village = "",
            gender = studentGender
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

    private fun formatAcademicMarksheetMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val full = rawLines.joinToString(" \n ")

        val examMatch = Regex("(?:Examination|Exam|পরীক্ষা)[:\\s]*([^\\n]+)", RegexOption.IGNORE_CASE).find(full)
        val examName = examMatch?.groupValues?.get(1)?.trim().orEmpty()

        val gpaMatch = Regex("(?:GPA|জিপিএ|Grade Point|ফলাফল)[:\\s]*([0-5][.\u0980-\u09FF0-9]{1,3})", RegexOption.IGNORE_CASE).find(full)
        val gpa = gpaMatch?.groupValues?.get(1).orEmpty()

        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        val studentName = labelMap["name_bn"].orEmpty().ifBlank { labelMap["name_en"].orEmpty() }.ifBlank { detectSequentialNameTriplets(rawLines).firstOrNull().orEmpty() }
        val rollNo = labelMap["roll_no"].orEmpty().ifBlank { extractRollNumberOnlyIfPresent(full) }
        val regNo = Regex("(?:Reg|রেজিস্ট্রেশন)[:\\s]*([0-9\u09E6-\u09EF]{6,16})", RegexOption.IGNORE_CASE).find(full)?.groupValues?.get(1).orEmpty()
        val school = extractSchoolName(rawLines)

        val fields = mutableListOf<FormattedField>()
        if (examName.isNotBlank()) fields.add(FormattedField("exam", "পরীক্ষার নাম", "Examination", examName, "ফলাফল বিবরণ", true))
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "শিক্ষার্থী তথ্য", true))
        if (rollNo.isNotBlank()) fields.add(FormattedField("roll", "রোল নম্বর", "Roll Number", formatDualNumber(rollNo), "শিক্ষার্থী তথ্য", true))
        if (regNo.isNotBlank()) fields.add(FormattedField("reg", "রেজিস্ট্রেশন নম্বর", "Registration No", formatDualNumber(regNo), "শিক্ষার্থী তথ্য"))
        if (gpa.isNotBlank()) fields.add(FormattedField("gpa", "প্রাপ্ত জিপিএ (GPA)", "GPA Obtained", formatDualNumber(gpa), "ফলাফল বিবরণ", true))
        if (school.isNotBlank()) fields.add(FormattedField("school", "শিক্ষা প্রতিষ্ঠান", "Institute", school, "প্রতিষ্ঠান"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = studentName.ifBlank { "শিক্ষার্থী" },
            studentClass = "১ম শ্রেণি",
            rollNumber = BanglaUtils.toEnglishDigits(rollNo).toIntOrNull() ?: 1,
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

    private fun formatGeneralDocumentMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fields = mutableListOf<FormattedField>()

        // Search dates with dual formatting
        val scannedDates = extractAllScannedDates(text)
        if (scannedDates.isNotEmpty()) {
            val dateStr = scannedDates.take(3).joinToString(", ") { it.displayValue() }
            fields.add(FormattedField("date", "উল্লেখিত তারিখ", "Detected Date", dateStr, "ডকুমেন্ট তথ্য", true))
        }

        val phones = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").findAll(text).map { it.value }.distinct().toList()
        if (phones.isNotEmpty()) {
            val phoneStr = phones.joinToString(", ") { formatDualNumber(it) }
            fields.add(FormattedField("phone", "মোবাইল / যোগাযোগ", "Contact Number", phoneStr, "যোগাযোগ", true))
        }

        // Fuzzy Key-Value pairs
        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        labelMap.forEach { (key, value) ->
            if (value.isNotBlank()) {
                val formattedVal = if (value.any { it in '0'..'9' }) formatDualNumber(value) else value
                fields.add(FormattedField(key, key, key, formattedVal, "মূল তথ্য"))
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

    // =========================================================================
    // ADVANCED EXTRACTION HELPERS & ALGORITHMS
    // =========================================================================

    /**
     * Extracts BRN (17-digit number), preserving original representation for dual formatting.
     */
    private fun extract17DigitBrn(text: String): String {
        val candidate = Regex("(?:BRN|Registration No|নিবন্ধন নম্বর|নম্বর)?[:\\s]*([0-9\u09E6-\u09EF\\s-]{16,22})", RegexOption.IGNORE_CASE).findAll(text)
        for (m in candidate) {
            val rawClean = m.groupValues[1].replace(" ", "").replace("-", "")
            val digits = BanglaUtils.toEnglishDigits(rawClean)
            if (digits.length == 17) {
                val year = digits.substring(0, 4).toIntOrNull()
                if (year != null && year in 1920..2035) {
                    return rawClean
                }
            }
        }

        val rawMatches = Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").findAll(text)
        for (m in rawMatches) {
            val digits = BanglaUtils.toEnglishDigits(m.value)
            val year = digits.substring(0, 4).toIntOrNull()
            if (year != null && year in 1920..2035) {
                return m.value
            }
        }

        return ""
    }

    /**
     * Extracts all dates preserving original verbatim raw text and creating normalized standard DD/MM/YYYY.
     * Supports:
     * - Numerical: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, YYYY-MM-DD (both English and Bengali digits)
     * - Month names in English: 12 Jan 2026, 01 January 1978, 12th Feb, 2021
     * - Month names in Bengali: ১২ই মার্চ ১৯৯৫, ১লা বৈশাখ ১৪৩০, ১৫ আগস্ট ২০২৩
     */
    fun extractAllScannedDates(text: String): List<ScannedDate> {
        val results = mutableListOf<ScannedDate>()
        val seenStandards = mutableSetOf<String>()

        // 1. Textual Month Dates (English & Bengali named months)
        val monthNamesPattern = Regex(
            "(\\b[0-9\u09E6-\u09EF]{1,2}(?:st|nd|rd|th|লা|রা|ঠা|ই|শে|ইশে)?\\s*(?:of\\s*)?[,.-]?\\s*" +
            "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?|" +
            "জানুয়ারি|জানুয়ারি|ফেব্রুয়ারি|ফেব্রুয়ারি|মার্চ|এপ্রিল|মে|জুন|জুলাই|আগস্ট|সেপ্টেম্বর|অক্টোবর|নভেম্বর|ডিসেম্বর|বৈশাখ|জ্যৈষ্ঠ|আষাঢ়|শ্রাবণ|ভাদ্র|আশ্বিন|কার্তিক|অগ্রহায়ণ|পৌষ|মাঘ|ফাল্গুন|চৈত্র)" +
            "\\s*[,.-]?\\s*[0-9\u09E6-\u09EF]{2,4}\\b)",
            RegexOption.IGNORE_CASE
        )

        for (m in monthNamesPattern.findAll(text)) {
            val raw = m.value.trim()
            val parsedStandard = parseTextualDateToStandard(raw)
            if (parsedStandard != null && parsedStandard !in seenStandards) {
                seenStandards.add(parsedStandard)
                results.add(ScannedDate(rawText = raw, standardDate = parsedStandard))
            }
        }

        // 2. Numerical Dates: DD/MM/YYYY or YYYY-MM-DD
        val numDateRegex = Regex("(\\b[0-9\u09E6-\u09EF]{1,2}[/.-][0-9\u09E6-\u09EF]{1,2}[/.-][0-9\u09E6-\u09EF]{2,4}\\b|\\b[0-9\u09E6-\u09EF]{4}[/.-][0-9\u09E6-\u09EF]{1,2}[/.-][0-9\u09E6-\u09EF]{1,2}\\b)")
        for (m in numDateRegex.findAll(text)) {
            val raw = m.value.trim()
            val normalizedDigits = BanglaUtils.toEnglishDigits(raw).replace('.', '/').replace('-', '/')
            val parts = normalizedDigits.split('/')
            if (parts.size == 3) {
                var d = 0
                var mth = 0
                var y = 0
                if (parts[0].length == 4) {
                    // YYYY/MM/DD
                    y = parts[0].toIntOrNull() ?: 0
                    mth = parts[1].toIntOrNull() ?: 0
                    d = parts[2].toIntOrNull() ?: 0
                } else {
                    // DD/MM/YYYY
                    d = parts[0].toIntOrNull() ?: 0
                    mth = parts[1].toIntOrNull() ?: 0
                    y = parts[2].toIntOrNull() ?: 0
                }

                if (d in 1..31 && mth in 1..12 && (y in 1900..2035 || y in 0..99)) {
                    val fullYear = if (y < 100) (if (y > 30) 1900 + y else 2000 + y) else y
                    val std = String.format("%02d/%02d/%04d", d, mth, fullYear)
                    if (std !in seenStandards) {
                        seenStandards.add(std)
                        results.add(ScannedDate(rawText = raw, standardDate = std))
                    }
                }
            }
        }

        return results
    }

    private fun parseTextualDateToStandard(text: String): String? {
        val eng = BanglaUtils.toEnglishDigits(text)
        val dayMatch = Regex("(\\d{1,2})").find(eng) ?: return null
        val yearMatch = Regex("(\\d{4})").find(eng) ?: return null

        val d = dayMatch.groupValues[1].toIntOrNull() ?: return null
        val y = yearMatch.groupValues[1].toIntOrNull() ?: return null
        val lower = text.lowercase()

        val month = when {
            lower.contains("jan") || lower.contains("জানুয়ারি") || lower.contains("জানুয়ারি") -> 1
            lower.contains("feb") || lower.contains("ফেব্রুয়ারি") || lower.contains("ফেব্রুয়ারি") -> 2
            lower.contains("mar") || lower.contains("মার্চ") -> 3
            lower.contains("apr") || lower.contains("এপ্রিল") -> 4
            lower.contains("may") || lower.contains("মে") -> 5
            lower.contains("jun") || lower.contains("জুন") -> 6
            lower.contains("jul") || lower.contains("জুলাই") -> 7
            lower.contains("aug") || lower.contains("আগস্ট") -> 8
            lower.contains("sep") || lower.contains("সেপ্টেম্বর") -> 9
            lower.contains("oct") || lower.contains("অক্টোবর") -> 10
            lower.contains("nov") || lower.contains("নভেম্বর") -> 11
            lower.contains("dec") || lower.contains("ডিসেম্বর") -> 12
            lower.contains("বৈশাখ") -> 4
            lower.contains("জ্যৈষ্ঠ") -> 5
            lower.contains("আষাঢ়") -> 6
            lower.contains("শ্রাবণ") -> 7
            lower.contains("ভাদ্র") -> 8
            lower.contains("আশ্বিন") -> 9
            lower.contains("কার্তিক") -> 10
            lower.contains("অগ্রহায়ণ") -> 11
            lower.contains("পৌষ") -> 12
            lower.contains("মাঘ") -> 1
            lower.contains("ফাল্গুন") -> 2
            lower.contains("চৈত্র") -> 3
            else -> return null
        }

        if (d in 1..31 && y in 1900..2035) {
            return String.format("%02d/%02d/%04d", d, month, y)
        }
        return null
    }

    private fun assignScannedDatesChronologically(dates: List<ScannedDate>): Triple<ScannedDate?, ScannedDate?, ScannedDate?> {
        if (dates.isEmpty()) return Triple(null, null, null)
        if (dates.size == 1) return Triple(dates[0], null, null)

        val sorted = dates.sortedBy { d ->
            val parts = d.standardDate.split('/')
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: 1
                val m = parts[1].toIntOrNull() ?: 1
                val y = parts[2].toIntOrNull() ?: 2000
                y * 10000 + m * 100 + day
            } else 0
        }

        return when (sorted.size) {
            1 -> Triple(sorted[0], null, null)
            2 -> Triple(sorted[0], sorted[1], null)
            else -> Triple(sorted[0], sorted[1], sorted[2])
        }
    }

    private fun extractDobInWords(text: String): String {
        val wordDateMatch = Regex("(?:Date of Birth|DOB|In Words|কথায়)[:\\s]*([A-Za-z0-9\u0980-\u09FF\\s,-]+(?:Nineteen|Two Thousand|Twenty|Thirty|March|January|February|April|May|June|July|August|September|October|November|December|First|Second|Third|হাজার|শত|এক|দুই|তিন|চার|পাঁচ|ছয়|সাত|আট|নয়|দশ)[A-Za-z0-9\u0980-\u09FF\\s,-]*)", RegexOption.IGNORE_CASE).find(text)
        return wordDateMatch?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun detectGender(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("sex : female") || lower.contains("sex:female") || lower.contains("female") || text.contains("নারী") || text.contains("মহিলা") || text.contains("স্ত্রী") || text.contains("ছাত্রী") -> "নারী (Female)"
            lower.contains("sex : male") || lower.contains("sex:male") || lower.contains("male") || text.contains("পুরুষ") || text.contains("ছাত্র") -> "পুরুষ (Male)"
            else -> ""
        }
    }

    /**
     * Extracts fields by scanning lines against a comprehensive dictionary of fuzzy label patterns.
     */
    private fun extractFieldsByFuzzyLabels(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        for (i in lines.indices) {
            val line = lines[i]
            val norm = normalizeNoise(line)

            // 1. Father's Name
            if (isFatherConcept(norm)) {
                val valStr = extractValueAfterLabel(line, listOf("পিতার নাম", "পিতারনাম", "পিতা", "Father's Name", "Fathers Name", "Father Name", "Father", "বাপের নাম", "Father :", "পিতা :", "পিতাঃ"))
                if (valStr.isNotBlank()) {
                    if (containsBangla(valStr)) result["father_bn"] = cleanNameString(valStr)
                    else result["father_en"] = cleanNameString(valStr)
                }
                if (i + 1 < lines.size && !isAnyLabel(lines[i + 1]) && lines[i + 1].length in 3..40) {
                    val nextLineVal = cleanNameString(lines[i + 1])
                    if (containsBangla(nextLineVal) && !result.containsKey("father_bn")) result["father_bn"] = nextLineVal
                    else if (!containsBangla(nextLineVal) && !result.containsKey("father_en")) result["father_en"] = nextLineVal
                }
            }

            // 2. Mother's Name
            else if (isMotherConcept(norm)) {
                val valStr = extractValueAfterLabel(line, listOf("মাতার নাম", "মাতারনাম", "মাতা", "Mother's Name", "Mothers Name", "Mother Name", "Mother", "মা", "Mother :", "মাতা :", "মাতাঃ"))
                if (valStr.isNotBlank()) {
                    if (containsBangla(valStr)) result["mother_bn"] = cleanNameString(valStr)
                    else result["mother_en"] = cleanNameString(valStr)
                }
                if (i + 1 < lines.size && !isAnyLabel(lines[i + 1]) && lines[i + 1].length in 3..40) {
                    val nextLineVal = cleanNameString(lines[i + 1])
                    if (containsBangla(nextLineVal) && !result.containsKey("mother_bn")) result["mother_bn"] = nextLineVal
                    else if (!containsBangla(nextLineVal) && !result.containsKey("mother_en")) result["mother_en"] = nextLineVal
                }
            }

            // 3. Student/Person Name
            else if (isPersonNameConcept(norm)) {
                val valStr = extractValueAfterLabel(line, listOf("শিক্ষার্থীর নাম", "ব্যক্তির নাম", "পূর্ণ নাম", "নাম", "Student's Name", "Student Name", "Child's Name", "Name of Child", "Name of Pupil", "Name :", "Name:", "নাম :", "নামঃ"))
                if (valStr.isNotBlank()) {
                    if (containsBangla(valStr)) result["name_bn"] = cleanNameString(valStr)
                    else result["name_en"] = cleanNameString(valStr)
                }
                if (i + 1 < lines.size && !isAnyLabel(lines[i + 1]) && lines[i + 1].length in 3..40) {
                    val nextLineVal = cleanNameString(lines[i + 1])
                    if (containsBangla(nextLineVal) && !result.containsKey("name_bn")) result["name_bn"] = nextLineVal
                    else if (!containsBangla(nextLineVal) && !result.containsKey("name_en")) result["name_en"] = nextLineVal
                }
            }

            // 4. Place of Birth
            else if (norm.contains("জন্মস্থান") || norm.contains("place of birth") || norm.contains("birth place")) {
                val pob = extractValueAfterLabel(line, listOf("জন্মস্থান", "Place of Birth", "Birth Place", "স্থান"))
                if (pob.isNotBlank()) result["pob"] = pob
            }

            // 5. Permanent Address
            else if (norm.contains("স্থায়ী ঠিকানা") || norm.contains("permanent address")) {
                val addr = extractValueAfterLabel(line, listOf("স্থায়ী ঠিকানা", "Permanent Address", "স্থায়ীঠিকানা"))
                if (addr.isNotBlank()) result["perm_address"] = addr
            }

            // 6. Present Address
            else if (norm.contains("বর্তমান ঠিকানা") || norm.contains("present address")) {
                val addr = extractValueAfterLabel(line, listOf("বর্তমান ঠিকানা", "Present Address", "বর্তমানঠিকানা"))
                if (addr.isNotBlank()) result["pres_address"] = addr
            }

            // 7. Student Class
            else if (norm.contains("শ্রেণি") || norm.contains("class")) {
                val cls = extractValueAfterLabel(line, listOf("শ্রেণি", "শ্রেণী", "Class"))
                if (cls.isNotBlank()) result["student_class"] = extractClassFromTextOnlyIfPresent(cls)
            }

            // 8. Roll No
            else if (norm.contains("রোল") || norm.contains("roll")) {
                val roll = extractValueAfterLabel(line, listOf("রোল নম্বর", "রোল নং", "রোল", "Roll No", "Roll Number", "Roll"))
                if (roll.isNotBlank()) result["roll_no"] = roll
            }

            // 9. Mobile No
            else if (norm.contains("মোবাইল") || norm.contains("mobile") || norm.contains("phone")) {
                val mob = extractValueAfterLabel(line, listOf("মোবাইল নম্বর", "মোবাইল", "Mobile No", "Mobile Number", "Mobile", "Phone"))
                if (mob.isNotBlank()) result["mobile"] = mob
            }
        }

        return result
    }

    private fun deInterleaveTwoColumnTable(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val labels = mutableListOf<String>()
        val values = mutableListOf<String>()

        for (line in lines) {
            val norm = normalizeNoise(line)
            if (isAnyLabel(norm)) {
                labels.add(norm)
            } else if (line.length in 3..50 && !line.contains("Government", ignoreCase = true) && !line.contains("কার্যালয়")) {
                values.add(line)
            }
        }

        if (labels.size >= 2 && values.size >= labels.size) {
            for (i in labels.indices) {
                val lbl = labels[i]
                val v = values.getOrNull(i).orEmpty()
                if (v.isNotBlank()) {
                    when {
                        isPersonNameConcept(lbl) -> result["name"] = v
                        isFatherConcept(lbl) -> result["father"] = v
                        isMotherConcept(lbl) -> result["mother"] = v
                    }
                }
            }
        }

        return result
    }

    private fun detectSequentialNameTriplets(lines: List<String>): List<String> {
        val candidates = mutableListOf<String>()
        val noiseWords = listOf("গণপ্রজাতন্ত্রী", "বাংলাদেশ", "সরকার", "রেজিস্ট্রার", "কার্যালয়", "সনদ", "নম্বর", "তারিখ", "Government", "Bangladesh", "Registrar", "Office", "Certificate", "Union", "Parishad", "District", "Zila", "Upazila")

        for (line in lines) {
            val clean = cleanNameString(line)
            if (clean.length in 4..40 && !clean.contains(Regex("[0-9০-৯]"))) {
                val containsNoise = noiseWords.any { clean.contains(it, ignoreCase = true) }
                if (!containsNoise && !isAnyLabel(clean)) {
                    candidates.add(clean)
                }
            }
        }
        return candidates.distinct()
    }

    private fun splitBilingualText(text: String): Pair<String, String> {
        val banglaParts = mutableListOf<String>()
        val englishParts = mutableListOf<String>()

        val tokens = text.split(" ")
        for (tok in tokens) {
            if (containsBangla(tok)) banglaParts.add(tok)
            else if (tok.any { it.isLetter() }) englishParts.add(tok)
        }

        return banglaParts.joinToString(" ").trim() to englishParts.joinToString(" ").trim()
    }

    private fun isPersonNameConcept(norm: String): Boolean {
        return norm.contains("নাম") || norm.contains("name") || norm.contains("ব্যক্তির নাম") || norm.contains("শিক্ষার্থীর নাম")
    }

    private fun isFatherConcept(norm: String): Boolean {
        return (norm.contains("পিতা") || norm.contains("father") || norm.contains("বাপের")) && !norm.contains("মাতা") && !norm.contains("mother")
    }

    private fun isMotherConcept(norm: String): Boolean {
        return norm.contains("মাতা") || norm.contains("mother") || norm.contains("মায়ের") || norm.contains("মা :")
    }

    private fun isAnyLabel(text: String): Boolean {
        val norm = normalizeNoise(text)
        return isPersonNameConcept(norm) || isFatherConcept(norm) || isMotherConcept(norm) ||
                norm.contains("জন্মস্থান") || norm.contains("ঠিকানা") || norm.contains("লিঙ্গ") || norm.contains("sex") || norm.contains("তারিখ") || norm.contains("date") || norm.contains("শ্রেণি") || norm.contains("রোল")
    }

    private fun extractValueAfterLabel(line: String, labels: List<String>): String {
        for (lbl in labels) {
            val idx = line.indexOf(lbl, ignoreCase = true)
            if (idx != -1) {
                var remainder = line.substring(idx + lbl.length).trim()
                remainder = remainder.replace(Regex("^[:ঃ=–—\\-_./|,]+"), "").trim()
                if (remainder.isNotBlank()) return remainder
            }
        }
        return ""
    }

    private fun cleanNameString(text: String): String {
        return text
            .replace(Regex("^(?:Name|নাম|Father|পিতা|Mother|মাতা|Student|Pupil)[:\\s/=-]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[:ঃ=–—\\-_./|,]+$"), "")
            .replace(Regex("^[\\s:ঃ=–—\\-_./|,]+"), "")
            .trim()
    }

    private fun extractNidNumber(text: String): String {
        val m = Regex("(?:NID|National ID|জাতীয় পরিচয়পত্র নং|নং)[:\\s]*([0-9\u09E6-\u09EF]{10,17})", RegexOption.IGNORE_CASE).find(text)
        if (m != null) return m.groupValues[1].replace(" ", "")

        val ten = Regex("\\b[0-9\u09E6-\u09EF]{10}\\b").find(text)
        if (ten != null) return ten.value

        val seventeen = Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").find(text)
        if (seventeen != null) return seventeen.value

        return ""
    }

    private fun extractBloodGroup(text: String): String {
        val m = Regex("(?:Blood Group|রক্তের গ্রুপ|Blood)[:\\s]*([ABOab0][+-]?)", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.get(1)?.uppercase() ?: ""
    }

    private fun extractPhoneNumber(text: String): String {
        val m = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").find(text)
        return m?.value.orEmpty()
    }

    private fun extractRollNumberOnlyIfPresent(text: String): String {
        val m = Regex("(?:Roll|রোল|ক্রমিক)[:\\s]*([0-9\u09E6-\u09EF]{1,4})", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.get(1).orEmpty()
    }

    private fun extractClassFromTextOnlyIfPresent(text: String): String {
        val lower = text.lowercase()
        return when {
            text.contains("১ম") || text.contains("প্রথম") || lower.contains("class 1") || lower.contains("class-1") || lower.contains("class i") -> "১ম শ্রেণি"
            text.contains("২য়") || text.contains("২য়") || text.contains("দ্বিতীয়") || lower.contains("class 2") || lower.contains("class-2") || lower.contains("class ii") -> "২য় শ্রেণি"
            text.contains("৩য়") || text.contains("৩য়") || text.contains("তৃতীয়") || lower.contains("class 3") || lower.contains("class-3") || lower.contains("class iii") -> "৩য় শ্রেণি"
            text.contains("৪র্থ") || text.contains("চতুর্থ") || lower.contains("class 4") || lower.contains("class-4") || lower.contains("class iv") -> "৪র্থ শ্রেণি"
            text.contains("৫ম") || text.contains("পঞ্চম") || lower.contains("class 5") || lower.contains("class-5") || lower.contains("class v") -> "৫ম শ্রেণি"
            text.contains("প্রাক") || lower.contains("pre-primary") || lower.contains("nursery") || lower.contains("kg") -> "প্রাক-প্রাথমিক ৪+"
            else -> ""
        }
    }

    private fun extractSchoolName(lines: List<String>): String {
        for (line in lines) {
            val lower = line.lowercase()
            if ((line.contains("বিদ্যালয়") || line.contains("স্কুল") || line.contains("মাদ্রাসা") || lower.contains("school") || lower.contains("madrasah") || lower.contains("academy")) &&
                !line.contains("প্রধান") && !line.contains("শিক্ষক")
            ) {
                return line.trim()
            }
        }
        return ""
    }

    private fun extractAddressHeuristically(lines: List<String>): String {
        val addrParts = mutableListOf<String>()
        for (line in lines) {
            val lower = line.lowercase()
            if (line.contains("গ্রাম") || line.contains("ডাকঘর") || line.contains("উপজেলা") || line.contains("জেলা") ||
                line.contains("রোড") || line.contains("ওয়ার্ড") || line.contains("বাসা") || line.contains("হোল্ডিং") ||
                lower.contains("village") || lower.contains("post") || lower.contains("upazila") || lower.contains("district") ||
                lower.contains("ward") || lower.contains("road")
            ) {
                val clean = extractValueAfterLabel(line, listOf("স্থায়ী ঠিকানা", "বর্তমান ঠিকানা", "ঠিকানা", "Permanent Address", "Address"))
                if (clean.isNotBlank() && clean !in addrParts) {
                    addrParts.add(clean)
                }
            }
        }
        return addrParts.joinToString(", ")
    }

    private fun extractIssuingOfficeHeuristically(lines: List<String>): String {
        for (line in lines.take(8)) {
            val lower = line.lowercase()
            if (line.contains("Union Parishad", ignoreCase = true) || line.contains("ইউনিয়ন পরিষদ") ||
                line.contains("Pourashava", ignoreCase = true) || line.contains("পৌরসভা") ||
                line.contains("City Corporation", ignoreCase = true) || line.contains("সিটি কর্পোরেশন") ||
                line.contains("Office of the Registrar", ignoreCase = true)
            ) {
                return line.trim()
            }
        }
        return ""
    }

    private fun normalizeNoise(text: String): String {
        return text
            .replace(Regex("[\\s_]+"), " ")
            .replace("জ ন্ম", "জন্ম")
            .replace("নি ব ন্ধ ন", "নিবন্ধন")
            .replace("পি তা", "পিতা")
            .replace("মা তা", "মাতা")
            .replace("না ম", "নাম")
            .replace("ঠা কা না", "ঠিকানা")
            .replace("Fa ther", "Father")
            .replace("Mo ther", "Mother")
            .replace("Na me", "Name")
            .trim()
    }

    private fun normalizeLine(line: String): String {
        return normalizeNoise(line)
    }

    private fun containsBangla(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
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
}
