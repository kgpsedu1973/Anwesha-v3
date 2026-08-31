package com.example.util

import java.util.regex.Pattern

/**
 * Intelligent Multi-Concept Document OCR Classifier & Structured Formatter.
 * Combines 6 Advanced Extraction Concepts:
 * 1. Fuzzy Bilingual Compound Labels & Regex Normalization
 * 2. Two-Column Table De-Interleaving & Row Reconstructor
 * 3. Pattern-Free Named Entity Recognition & Sequential Name Triplet Finder
 * 4. Autonomous Entity Extractors (17-digit BRN, Chronological Date Resolver, Gender, Address)
 * 5. Spatial Proximity Grid Alignment
 * 6. Interactive Re-arrangement & Verification Pipeline
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
            normalized.contains("ফলাফল") || normalized.contains("জিপিএ") || normalized.contains("শিক্ষা বোর্ড") ||
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
    // CONCEPT 1 & 4: BIRTH CERTIFICATE MULTI-CONCEPT PARSING
    // =========================================================================

    private fun formatBirthCertificateMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val normalizedLines = rawLines.map { normalizeLine(it) }
        val fullJoined = normalizedLines.joinToString(" \n ")

        // --- Step 1: Autonomous Entity Extractions (BRN, Dates, Sex) ---
        // 1.1 BRN Extraction (17-digit number)
        var brn = extract17DigitBrn(fullJoined)

        // 1.2 All Dates extraction
        val allDates = extractAllDates(fullJoined)

        var dateOfBirth = ""
        var dateOfReg = ""
        var dateOfIssue = ""

        // If BRN exists, its first 4 digits represent the Birth Year!
        val birthYearFromBrn = if (brn.length == 17) brn.substring(0, 4) else null

        if (allDates.isNotEmpty()) {
            if (birthYearFromBrn != null) {
                // Find date whose year matches birthYearFromBrn
                val dobMatch = allDates.find { it.contains(birthYearFromBrn) }
                if (dobMatch != null) {
                    dateOfBirth = dobMatch
                    val remainingDates = allDates.filter { it != dobMatch }
                    if (remainingDates.isNotEmpty()) dateOfReg = remainingDates.first()
                    if (remainingDates.size > 1) dateOfIssue = remainingDates.last()
                } else {
                    assignDatesChronologically(allDates).also { (dob, reg, issue) ->
                        dateOfBirth = dob
                        dateOfReg = reg
                        dateOfIssue = issue
                    }
                }
            } else {
                assignDatesChronologically(allDates).also { (dob, reg, issue) ->
                    dateOfBirth = dob
                    dateOfReg = reg
                    dateOfIssue = issue
                }
            }
        }

        // Check for DOB written in words
        val dobInWords = extractDobInWords(fullJoined)

        // 1.3 Gender / Sex
        val gender = detectGender(fullJoined)

        // --- Step 2: Multi-Concept Name Extractions (Student, Father, Mother) ---
        var nameBn = ""
        var nameEn = ""
        var fatherBn = ""
        var fatherEn = ""
        var motherBn = ""
        var motherEn = ""
        var placeOfBirth = ""
        var permanentAddress = ""
        var presentAddress = ""
        var issuingOffice = ""

        // Strategy A: Regex Keyword Label Matches
        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        nameBn = labelMap["name_bn"].orEmpty()
        nameEn = labelMap["name_en"].orEmpty()
        fatherBn = labelMap["father_bn"].orEmpty()
        fatherEn = labelMap["father_en"].orEmpty()
        motherBn = labelMap["mother_bn"].orEmpty()
        motherEn = labelMap["mother_en"].orEmpty()
        placeOfBirth = labelMap["pob"].orEmpty()
        permanentAddress = labelMap["perm_address"].orEmpty()
        presentAddress = labelMap["pres_address"].orEmpty()
        issuingOffice = labelMap["office"].orEmpty()

        // Strategy B: Two-Column De-Interleaving if labels and values were separated
        if (nameBn.isBlank() || fatherBn.isBlank() || motherBn.isBlank()) {
            val deInterleaved = deInterleaveTwoColumnTable(rawLines)
            if (nameBn.isBlank() && deInterleaved.containsKey("name")) nameBn = deInterleaved["name"].orEmpty()
            if (fatherBn.isBlank() && deInterleaved.containsKey("father")) fatherBn = deInterleaved["father"].orEmpty()
            if (motherBn.isBlank() && deInterleaved.containsKey("mother")) motherBn = deInterleaved["mother"].orEmpty()
        }

        // Strategy C: Pattern-Free Named Entity Triplet Detection (When labels are absent/mangled)
        if (nameBn.isBlank() || fatherBn.isBlank() || motherBn.isBlank()) {
            val nameTriplets = detectSequentialNameTriplets(rawLines)
            if (nameBn.isBlank() && nameTriplets.isNotEmpty()) nameBn = nameTriplets[0]
            if (fatherBn.isBlank() && nameTriplets.size > 1) fatherBn = nameTriplets[1]
            if (motherBn.isBlank() && nameTriplets.size > 2) motherBn = nameTriplets[2]
        }

        // Strategy D: English/Bangla separation cleanup
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

        // If place of birth or address is still blank, scan for geographic entities
        if (permanentAddress.isBlank()) {
            permanentAddress = extractAddressHeuristically(rawLines)
        }
        if (issuingOffice.isBlank()) {
            issuingOffice = extractIssuingOfficeHeuristically(rawLines)
        }

        // Assemble Structured Output
        val fields = mutableListOf<FormattedField>()
        if (brn.isNotBlank()) fields.add(FormattedField("brn", "জন্ম নিবন্ধন নম্বর (BRN)", "Birth Reg No", brn, "সনদ বিবরণ", true))
        if (nameBn.isNotBlank()) fields.add(FormattedField("name_bn", "নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        if (nameEn.isNotBlank()) fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        if (dateOfBirth.isNotBlank()) fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dateOfBirth, "ব্যক্তিগত তথ্য", true))
        if (dobInWords.isNotBlank()) fields.add(FormattedField("dob_words", "জন্ম তারিখ (কথায়)", "DOB (In Words)", dobInWords, "ব্যক্তিগত তথ্য"))
        if (gender.isNotBlank()) fields.add(FormattedField("gender", "লিঙ্গ", "Sex / Gender", gender, "ব্যক্তিগত তথ্য", true))
        if (fatherBn.isNotBlank()) fields.add(FormattedField("father_bn", "পিতার নাম (বাংলা)", "Father's Name (BN)", fatherBn, "পারিবারিক তথ্য", true))
        if (fatherEn.isNotBlank()) fields.add(FormattedField("father_en", "পিতার নাম (ইংরেজি)", "Father's Name (EN)", fatherEn, "পারিবারিক তথ্য"))
        if (motherBn.isNotBlank()) fields.add(FormattedField("mother_bn", "মাতার নাম (বাংলা)", "Mother's Name (BN)", motherBn, "পারিবারিক তথ্য", true))
        if (motherEn.isNotBlank()) fields.add(FormattedField("mother_en", "মাতার নাম (ইংরেজি)", "Mother's Name (EN)", motherEn, "পারিবারিক তথ্য"))
        if (dateOfReg.isNotBlank()) fields.add(FormattedField("reg_date", "নিবন্ধনের তারিখ", "Date of Registration", dateOfReg, "সনদ বিবরণ"))
        if (dateOfIssue.isNotBlank()) fields.add(FormattedField("issue_date", "প্রদানের তারিখ", "Date of Issuance", dateOfIssue, "সনদ বিবরণ"))
        if (placeOfBirth.isNotBlank()) fields.add(FormattedField("pob", "জন্মস্থান", "Place of Birth", placeOfBirth, "ঠিকানা"))
        if (permanentAddress.isNotBlank()) fields.add(FormattedField("perm_address", "স্থায়ী ঠিকানা", "Permanent Address", permanentAddress, "ঠিকানা", true))
        if (presentAddress.isNotBlank()) fields.add(FormattedField("pres_address", "বর্তমান ঠিকানা", "Present Address", presentAddress, "ঠিকানা"))
        if (issuingOffice.isNotBlank()) fields.add(FormattedField("office", "প্রদানকারী কার্যালয়", "Issuing Authority", issuingOffice, "সনদ বিবরণ"))

        val studentGender = if (gender.contains("নারী") || gender.contains("Female") || gender.contains("ছাত্রী")) "ছাত্রী" else "ছাত্র"

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = nameBn.ifBlank { nameEn }.ifBlank { "শিক্ষার্থী" },
            fatherName = fatherBn.ifBlank { fatherEn },
            motherName = motherBn.ifBlank { motherEn },
            birthDate = dateOfBirth,
            birthRegNumber = brn,
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

        // NID Number (10 digit smart, or 13/17 digit traditional)
        var nidNo = extractNidNumber(fullJoined)

        // Date of Birth
        var dob = ""
        val dobMatch = Regex("(?:Date of Birth|DOB|জন্ম তারিখ|তারিখ)[:\\s]*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})", RegexOption.IGNORE_CASE).find(fullJoined)
        if (dobMatch != null) {
            dob = dobMatch.groupValues[1].trim()
        } else {
            val dates = extractAllDates(fullJoined)
            if (dates.isNotEmpty()) dob = dates.first()
        }

        // Blood Group
        val bloodGroup = extractBloodGroup(fullJoined)

        // Names
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
        if (nidNo.isNotBlank()) fields.add(FormattedField("nid", "জাতীয় পরিচয়পত্র নম্বর (NID)", "National ID No", nidNo, "পরিচিতি", true))
        if (nameBn.isNotBlank()) fields.add(FormattedField("name_bn", "নাম (বাংলা)", "Name (Bangla)", nameBn, "ব্যক্তিগত তথ্য", true))
        if (nameEn.isNotBlank()) fields.add(FormattedField("name_en", "নাম (ইংরেজি)", "Name (English)", nameEn, "ব্যক্তিগত তথ্য"))
        if (dob.isNotBlank()) fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dob, "ব্যক্তিগত তথ্য", true))
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য", true))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (bloodGroup.isNotBlank()) fields.add(FormattedField("blood", "রক্তের গ্রুপ", "Blood Group", bloodGroup, "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = nameBn.ifBlank { nameEn }.ifBlank { "নাগরিক" },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dob,
            birthRegNumber = nidNo,
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
        var studentClass = labelMap["student_class"].orEmpty()
        var rollNo = labelMap["roll_no"].orEmpty()
        var mobile = labelMap["mobile"].orEmpty()
        var address = labelMap["perm_address"].orEmpty()

        if (mobile.isBlank()) mobile = extractPhoneNumber(full)
        if (studentClass.isBlank()) studentClass = extractClassFromText(full)
        if (rollNo.isBlank()) rollNo = extractRollNumber(full)

        if (studentName.isBlank() || fatherName.isBlank()) {
            val triplets = detectSequentialNameTriplets(rawLines)
            if (studentName.isBlank() && triplets.isNotEmpty()) studentName = triplets[0]
            if (fatherName.isBlank() && triplets.size > 1) fatherName = triplets[1]
            if (motherName.isBlank() && triplets.size > 2) motherName = triplets[2]
        }

        val schoolName = extractSchoolName(rawLines)
        val dob = extractAllDates(full).firstOrNull().orEmpty()
        val brn = extract17DigitBrn(full)

        val fields = mutableListOf<FormattedField>()
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "একাডেমিক তথ্য", true))
        fields.add(FormattedField("class", "শ্রেণি", "Class", studentClass.ifBlank { "১ম শ্রেণি" }, "একাডেমিক তথ্য", true))
        fields.add(FormattedField("roll", "রোল নং", "Roll No", rollNo.ifBlank { "1" }, "একাডেমিক তথ্য", true))
        if (schoolName.isNotBlank()) fields.add(FormattedField("school", "প্রতিষ্ঠান / বিদ্যালয়", "Institution", schoolName, "প্রতিষ্ঠান", true))
        if (fatherName.isNotBlank()) fields.add(FormattedField("father", "পিতার নাম", "Father's Name", fatherName, "পারিবারিক তথ্য"))
        if (motherName.isNotBlank()) fields.add(FormattedField("mother", "মাতার নাম", "Mother's Name", motherName, "পারিবারিক তথ্য"))
        if (mobile.isNotBlank()) fields.add(FormattedField("mobile", "মোবাইল নম্বর", "Mobile Contact", mobile, "যোগাযোগ", true))
        if (dob.isNotBlank()) fields.add(FormattedField("dob", "জন্ম তারিখ", "Date of Birth", dob, "ব্যক্তিগত তথ্য"))
        if (brn.isNotBlank()) fields.add(FormattedField("brn", "জন্ম নিবন্ধন নং", "BRN", brn, "ব্যক্তিগত তথ্য"))
        if (address.isNotBlank()) fields.add(FormattedField("address", "ঠিকানা", "Address", address, "ঠিকানা"))

        val studentGender = if (studentName.contains("আক্তার") || studentName.contains("খাতুন") || studentName.contains("বেগম") || studentName.contains("Khatun") || studentName.contains("Akter") || studentName.contains("Begum")) "ছাত্রী" else "ছাত্র"

        val studentInfo = GoogleDriveOcrHelper.ParsedStudentInfo(
            name = studentName.ifBlank { "শিক্ষার্থী" },
            fatherName = fatherName,
            motherName = motherName,
            birthDate = dob,
            birthRegNumber = brn,
            studentClass = studentClass.ifBlank { "১ম শ্রেণি" },
            rollNumber = rollNo.toIntOrNull() ?: 1,
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

        var examName = "বার্ষিক মূল্যায়ন / পাবলিক পরীক্ষা"
        val gpaMatch = Regex("(?:GPA|জিপিএ|Grade Point|ফলাফল)[:\\s]*([0-5]\\.[0-9]{1,2})", RegexOption.IGNORE_CASE).find(full)
        val gpa = gpaMatch?.groupValues?.get(1).orEmpty()

        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        val studentName = labelMap["name_bn"].orEmpty().ifBlank { labelMap["name_en"].orEmpty() }.ifBlank { detectSequentialNameTriplets(rawLines).firstOrNull().orEmpty() }
        val rollNo = labelMap["roll_no"].orEmpty().ifBlank { extractRollNumber(full) }
        val regNo = Regex("(?:Reg|রেজিস্ট্রেশন)[:\\s]*([0-9\u09E6-\u09EF]{6,16})", RegexOption.IGNORE_CASE).find(full)?.groupValues?.get(1).orEmpty()
        val school = extractSchoolName(rawLines)

        val fields = mutableListOf<FormattedField>()
        fields.add(FormattedField("exam", "পরীক্ষার নাম", "Examination", examName, "ফলাফল বিবরণ", true))
        if (studentName.isNotBlank()) fields.add(FormattedField("name", "শিক্ষার্থীর নাম", "Student Name", studentName, "শিক্ষার্থী তথ্য", true))
        if (rollNo.isNotBlank()) fields.add(FormattedField("roll", "রোল নম্বর", "Roll Number", rollNo, "শিক্ষার্থী তথ্য", true))
        if (regNo.isNotBlank()) fields.add(FormattedField("reg", "রেজিস্ট্রেশন নম্বর", "Registration No", regNo, "শিক্ষার্থী তথ্য"))
        if (gpa.isNotBlank()) fields.add(FormattedField("gpa", "প্রাপ্ত জিপিএ (GPA)", "GPA Obtained", gpa, "ফলাফল বিবরণ", true))
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

    private fun formatGeneralDocumentMultiConcept(text: String): FormattedDocResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fields = mutableListOf<FormattedField>()

        // Search dates & phones
        val dates = extractAllDates(text)
        if (dates.isNotEmpty()) {
            fields.add(FormattedField("date", "উল্লেখিত তারিখ", "Detected Date", dates.take(3).joinToString(", "), "ডকুমেন্ট তথ্য", true))
        }

        val phones = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").findAll(text).map { BanglaUtils.toEnglishDigits(it.value) }.distinct().toList()
        if (phones.isNotEmpty()) {
            fields.add(FormattedField("phone", "মোবাইল / যোগাযোগ", "Contact Number", phones.joinToString(", "), "যোগাযোগ", true))
        }

        // Fuzzy Key-Value pairs
        val labelMap = extractFieldsByFuzzyLabels(rawLines)
        labelMap.forEach { (key, value) ->
            if (value.isNotBlank()) {
                fields.add(FormattedField(key, key, key, value, "মূল তথ্য"))
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
     * Extracts BRN (17-digit number), tolerating spaces, dashes, or Bengali digits.
     */
    private fun extract17DigitBrn(text: String): String {
        // Pattern 1: Contiguous or space-separated 17 digits
        val candidate = Regex("(?:BRN|Registration No|নিবন্ধন নম্বর|নম্বর)?[:\\s]*([0-9\u09E6-\u09EF\\s-]{16,22})", RegexOption.IGNORE_CASE).findAll(text)
        for (m in candidate) {
            val digits = BanglaUtils.toEnglishDigits(m.groupValues[1].replace(Regex("[^0-9\u09E6-\u09EF]"), ""))
            if (digits.length == 17) {
                val year = digits.substring(0, 4).toIntOrNull()
                if (year != null && year in 1920..2030) {
                    return digits
                }
            }
        }

        // Pattern 2: Any raw 17-digit number in the text
        val rawMatches = Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").findAll(text)
        for (m in rawMatches) {
            val digits = BanglaUtils.toEnglishDigits(m.value)
            val year = digits.substring(0, 4).toIntOrNull()
            if (year != null && year in 1920..2030) {
                return digits
            }
        }

        return ""
    }

    /**
     * Extracts all dates in any format (DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, Bengali digits).
     */
    private fun extractAllDates(text: String): List<String> {
        val results = mutableListOf<String>()
        val dateRegex = Regex("(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|[০-৯]{1,2}[/.-][০-৯]{1,2}[/.-][০-৯]{2,4})")
        for (m in dateRegex.findAll(text)) {
            val normalized = BanglaUtils.toEnglishDigits(m.value).replace('.', '/').replace('-', '/')
            val parts = normalized.split('/')
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull() ?: 0
                val mth = parts[1].toIntOrNull() ?: 0
                val y = parts[2].toIntOrNull() ?: 0
                if (d in 1..31 && mth in 1..12 && (y in 1900..2035 || y in 0..99)) {
                    val fullYear = if (y < 100) (if (y > 30) 1900 + y else 2000 + y) else y
                    results.add(String.format("%02d/%02d/%04d", d, mth, fullYear))
                }
            }
        }
        return results.distinct()
    }

    /**
     * Assigns dates chronologically: Birth Date < Registration Date <= Issuance Date
     */
    private fun assignDatesChronologically(dates: List<String>): Triple<String, String, String> {
        if (dates.isEmpty()) return Triple("", "", "")
        if (dates.size == 1) return Triple(dates[0], "", "")

        // Parse to timestamps for sorting
        val parsed = dates.mapNotNull { dStr ->
            val parts = dStr.split('/')
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull() ?: 1
                val m = parts[1].toIntOrNull() ?: 1
                val y = parts[2].toIntOrNull() ?: 2000
                (y * 10000 + m * 100 + d) to dStr
            } else null
        }.sortedBy { it.first }

        return when (parsed.size) {
            1 -> Triple(parsed[0].second, "", "")
            2 -> Triple(parsed[0].second, parsed[1].second, parsed[1].second)
            else -> Triple(parsed[0].second, parsed[1].second, parsed[2].second)
        }
    }

    private fun extractDobInWords(text: String): String {
        val wordDateMatch = Regex("(?:Date of Birth|DOB|In Words|কথায়)[:\\s]*([A-Za-z0-9\\s,-]+(?:Nineteen|Two Thousand|Twenty|Thirty|March|January|February|April|May|June|July|August|September|October|November|December|First|Second|Third)[A-Za-z0-9\\s,-]*)", RegexOption.IGNORE_CASE).find(text)
        return wordDateMatch?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun detectGender(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("sex : female") || lower.contains("sex:female") || lower.contains("female") || text.contains("নারী") || text.contains("মহিলা") || text.contains("স্ত্রী") || text.contains("ছাত্রী") -> "নারী (Female)"
            lower.contains("sex : male") || lower.contains("sex:male") || lower.contains("male") || text.contains("পুরুষ") || text.contains("ছাত্র") -> "পুরুষ (Male)"
            else -> "অনির্দিষ্ট"
        }
    }

    /**
     * Extracts fields by scanning lines against a comprehensive dictionary of fuzzy label patterns.
     * Handles bilingual labels (e.g. "Father's Name / পিতার নাম : ..."), noise, colon variants, etc.
     */
    private fun extractFieldsByFuzzyLabels(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        for (i in lines.indices) {
            val line = lines[i]
            val norm = normalizeNoise(line)

            // Check each concept
            // 1. Father's Name (Concept: পিতা / Father / বাপের নাম)
            if (isFatherConcept(norm)) {
                val valStr = extractValueAfterLabel(line, listOf("পিতার নাম", "পিতারনাম", "পিতা", "Father's Name", "Fathers Name", "Father Name", "Father", "বাপের নাম", "Father :", "পিতা :", "পিতাঃ"))
                if (valStr.isNotBlank()) {
                    if (containsBangla(valStr)) result["father_bn"] = cleanNameString(valStr)
                    else result["father_en"] = cleanNameString(valStr)
                }
                // Check if next line contains complementary English/Bangla name
                if (i + 1 < lines.size && !isAnyLabel(lines[i + 1]) && lines[i + 1].length in 3..40) {
                    val nextLineVal = cleanNameString(lines[i + 1])
                    if (containsBangla(nextLineVal) && !result.containsKey("father_bn")) result["father_bn"] = nextLineVal
                    else if (!containsBangla(nextLineVal) && !result.containsKey("father_en")) result["father_en"] = nextLineVal
                }
            }

            // 2. Mother's Name (Concept: মাতা / Mother / মায়ের নাম)
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

            // 3. Student/Person Name (Concept: নাম / Name / শিক্ষার্থীর নাম)
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
            else if (norm.contains("জন্মস্থান") || norm.lowercase().contains("place of birth") || norm.lowercase().contains("birth place")) {
                val valStr = extractValueAfterLabel(line, listOf("জন্মস্থান", "Place of Birth", "Place of birth", "Birth Place"))
                if (valStr.isNotBlank()) result["pob"] = valStr
            }

            // 5. Permanent Address
            else if (norm.contains("স্থায়ী ঠিকানা") || norm.contains("স্থায়ী ঠিকানা") || norm.lowercase().contains("permanent address")) {
                val valStr = extractValueAfterLabel(line, listOf("স্থায়ী ঠিকানা", "স্থায়ী ঠিকানা", "Permanent Address", "Permanent"))
                if (valStr.isNotBlank()) result["perm_address"] = valStr
            }

            // 6. Present Address
            else if (norm.contains("বর্তমান ঠিকানা") || norm.lowercase().contains("present address")) {
                val valStr = extractValueAfterLabel(line, listOf("বর্তমান ঠিকানা", "Present Address", "Present"))
                if (valStr.isNotBlank()) result["pres_address"] = valStr
            }

            // 7. Class
            else if (norm.contains("শ্রেণি") || norm.lowercase().contains("class")) {
                result["student_class"] = extractClassFromLine(line)
            }

            // 8. Roll No
            else if (norm.contains("রোল") || norm.lowercase().contains("roll")) {
                val r = extractRollNumber(line)
                if (r.isNotBlank()) result["roll_no"] = r
            }

            // 9. Mobile
            else if (norm.contains("মোবাইল") || norm.lowercase().contains("mobile") || norm.contains("ফোন") || norm.lowercase().contains("phone")) {
                val p = extractPhoneNumber(line)
                if (p.isNotBlank()) result["mobile"] = p
            }
        }

        return result
    }

    /**
     * Pattern-Free Sequential Name Triplet Detection:
     * Scans all lines in the document and isolates lines that look like Person Names (Bengali or English).
     * In standard Bangladeshi certificates (Birth, NID, Admission), name entities appear in the fixed order:
     * 1. Student / Person Name
     * 2. Father's Name
     * 3. Mother's Name
     */
    private fun detectSequentialNameTriplets(lines: List<String>): List<String> {
        val detectedNames = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length !in 3..45) continue
            if (isAnyLabel(trimmed)) continue
            if (trimmed.contains("Office", ignoreCase = true) || trimmed.contains("School", ignoreCase = true) ||
                trimmed.contains("Parishad", ignoreCase = true) || trimmed.contains("পরিষদ") ||
                trimmed.contains("কার্যালয়") || trimmed.contains("Government", ignoreCase = true) ||
                trimmed.contains("Bangladesh", ignoreCase = true) || trimmed.contains("জাতীয়তা") ||
                trimmed.contains("Bangladeshi", ignoreCase = true) || trimmed.contains("বাংলাদেশী") ||
                trimmed.contains("রেজিস্ট্রার") || trimmed.contains("Authority", ignoreCase = true) ||
                trimmed.contains("Seal", ignoreCase = true) || trimmed.contains("Signature", ignoreCase = true)
            ) continue

            // Check if line matches Bengali Person Name grammar or English Person Name grammar
            if (isBengaliPersonName(trimmed) || isEnglishPersonName(trimmed)) {
                val clean = cleanNameString(trimmed)
                if (clean.isNotBlank() && clean !in detectedNames) {
                    detectedNames.add(clean)
                }
            }
        }

        return detectedNames
    }

    /**
     * Two-Column Table De-Interleaver:
     * When OCR reads all labels first, then all values:
     * Discovers label block and value block, then maps them 1:1.
     */
    private fun deInterleaveTwoColumnTable(lines: List<String>): Map<String, String> {
        val labelIndices = mutableListOf<Pair<String, Int>>()
        val valueCandidates = mutableListOf<Pair<String, Int>>()

        for (i in lines.indices) {
            val line = lines[i]
            val norm = normalizeNoise(line)

            when {
                isPersonNameConcept(norm) -> labelIndices.add("name" to i)
                isFatherConcept(norm) -> labelIndices.add("father" to i)
                isMotherConcept(norm) -> labelIndices.add("mother" to i)
                isBengaliPersonName(line) || isEnglishPersonName(line) -> {
                    if (!isAnyLabel(line)) {
                        valueCandidates.add(cleanNameString(line) to i)
                    }
                }
            }
        }

        val result = mutableMapOf<String, String>()
        if (labelIndices.isNotEmpty() && valueCandidates.isNotEmpty()) {
            // Find for each label the closest subsequent value candidate
            for ((labelKey, labelLineIdx) in labelIndices) {
                val bestVal = valueCandidates.filter { it.second > labelLineIdx }.minByOrNull { it.second - labelLineIdx }
                if (bestVal != null) {
                    result[labelKey] = bestVal.first
                }
            }
        }

        return result
    }

    // Helper Concept Matchers
    private fun isFatherConcept(text: String): Boolean {
        val lower = text.lowercase()
        return text.contains("পিতা") || text.contains("পিতার") || text.contains("বাপের") ||
                lower.contains("father") || lower.contains("father's") || lower.contains("fathers") ||
                text.contains("পতোর") || text.contains("অভিভাবক")
    }

    private fun isMotherConcept(text: String): Boolean {
        val lower = text.lowercase()
        return text.contains("মাতা") || text.contains("মাতার") || text.contains("মায়ের") ||
                lower.contains("mother") || lower.contains("mother's") || lower.contains("mothers") ||
                text.contains("মাতো")
    }

    private fun isPersonNameConcept(text: String): Boolean {
        val lower = text.lowercase()
        if (isFatherConcept(text) || isMotherConcept(text)) return false
        return text.contains("নাম") || lower.contains("name") || text.contains("শিক্ষার্থীর নাম") ||
                lower.contains("student name") || lower.contains("child name") || lower.contains("pupil")
    }

    private fun isAnyLabel(text: String): Boolean {
        val lower = text.lowercase()
        val norm = normalizeNoise(text)
        return norm.contains("নাম") || lower.contains("name") || norm.contains("পিতা") || lower.contains("father") ||
                norm.contains("মাতা") || lower.contains("mother") || norm.contains("জন্ম") || lower.contains("birth") ||
                norm.contains("তারিখ") || lower.contains("date") || norm.contains("লিঙ্গ") || lower.contains("sex") ||
                lower.contains("gender") || norm.contains("ঠিকানা") || lower.contains("address") || norm.contains("শ্রেণি") ||
                lower.contains("class") || norm.contains("রোল") || lower.contains("roll") || norm.contains("নম্বর") ||
                lower.contains("number") || norm.contains("জাতীয়তা") || lower.contains("nationality")
    }

    private fun isBengaliPersonName(text: String): Boolean {
        if (!containsBangla(text)) return false
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size !in 2..6) return false

        val commonNameSyllables = listOf("মোঃ", "মোহাম্মদ", "মোসাঃ", "মোসাম্মৎ", "ইসলাম", "রহমান", "উদ্দিন", "আহমেদ", "হোসেন", "আলী", "খান", "চৌধুরী", "সরকার", "বেগম", "খাতুন", "আক্তার", "মোল্লা", "মিয়া", "হক", "পলাশ", "মণ্ডল", "শিকদার", "দত্ত", "দাস", "ঘোষ", "রায়", "শেখ", "সৈয়দ", "কাজী")
        val hasCommonSyllable = commonNameSyllables.any { text.contains(it) }
        val isAllBanglaLetters = text.all { it in '\u0980'..'\u09FF' || it == ' ' || it == '.' || it == '-' || it == 'ঃ' }

        return isAllBanglaLetters && (hasCommonSyllable || words.size in 2..4)
    }

    private fun isEnglishPersonName(text: String): Boolean {
        if (containsBangla(text)) return false
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size !in 2..6) return false

        val commonEnglishNameTokens = listOf("MD", "MD.", "MOHAMMAD", "MOHAMED", "MST", "MST.", "ISLAM", "RAHMAN", "UDDIN", "AHMED", "HOSSAIN", "ALI", "KHAN", "CHOWDHURY", "SARKER", "BEGUM", "KHATUN", "AKTER", "MOLLA", "MIAH", "HAQUE", "ROY", "DAS", "GHOSH", "DUTTA", "SHEIKH", "SYED", "KAZI")
        val upperText = text.uppercase()
        val hasCommonToken = commonEnglishNameTokens.any { upperText.contains(it) }
        val isAlphaOnly = text.all { it in 'A'..'Z' || it in 'a'..'z' || it == ' ' || it == '.' || it == '-' }

        return isAlphaOnly && (hasCommonToken || (text == text.uppercase() && words.size in 2..4))
    }

    private fun splitBilingualText(text: String): Pair<String, String> {
        val bnChars = StringBuilder()
        val enChars = StringBuilder()
        for (token in text.split(Regex("[/|,–—\\n]"))) {
            val t = token.trim()
            if (containsBangla(t)) bnChars.append(" ").append(t)
            else if (t.any { it in 'A'..'Z' || it in 'a'..'z' }) enChars.append(" ").append(t)
        }
        return bnChars.toString().trim() to enChars.toString().trim()
    }

    private fun extractValueAfterLabel(line: String, labels: List<String>): String {
        var clean = line
        for (lbl in labels) {
            clean = clean.replace(lbl, "", ignoreCase = true)
        }
        return clean.trim(' ', ':', 'ঃ', '-', '=', '–', '—', '/', '|', '\t', ',', '.')
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
        if (m != null) return BanglaUtils.toEnglishDigits(m.groupValues[1].replace(" ", ""))

        val ten = Regex("\\b[0-9\u09E6-\u09EF]{10}\\b").find(text)
        if (ten != null) return BanglaUtils.toEnglishDigits(ten.value)

        val seventeen = Regex("\\b[0-9\u09E6-\u09EF]{17}\\b").find(text)
        if (seventeen != null) return BanglaUtils.toEnglishDigits(seventeen.value)

        return ""
    }

    private fun extractBloodGroup(text: String): String {
        val m = Regex("(?:Blood Group|রক্তের গ্রুপ|Blood)[:\\s]*([ABOab0][+-]?)", RegexOption.IGNORE_CASE).find(text)
        return m?.groupValues?.get(1)?.uppercase() ?: ""
    }

    private fun extractPhoneNumber(text: String): String {
        val m = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}").find(text)
        return if (m != null) BanglaUtils.toEnglishDigits(m.value) else ""
    }

    private fun extractRollNumber(text: String): String {
        val m = Regex("(?:Roll|রোল|ক্রমিক)[:\\s]*([0-9\u09E6-\u09EF]{1,4})", RegexOption.IGNORE_CASE).find(text)
        return if (m != null) BanglaUtils.toEnglishDigits(m.groupValues[1]) else "1"
    }

    private fun extractClassFromText(text: String): String {
        val lower = text.lowercase()
        return when {
            text.contains("১ম") || text.contains("প্রথম") || lower.contains("class 1") || lower.contains("class-1") || lower.contains("class i") -> "১ম শ্রেণি"
            text.contains("২য়") || text.contains("২য়") || text.contains("দ্বিতীয়") || lower.contains("class 2") || lower.contains("class-2") || lower.contains("class ii") -> "২য় শ্রেণি"
            text.contains("৩য়") || text.contains("৩য়") || text.contains("তৃতীয়") || lower.contains("class 3") || lower.contains("class-3") || lower.contains("class iii") -> "৩য় শ্রেণি"
            text.contains("৪র্থ") || text.contains("চতুর্থ") || lower.contains("class 4") || lower.contains("class-4") || lower.contains("class iv") -> "৪র্থ শ্রেণি"
            text.contains("৫ম") || text.contains("পঞ্চম") || lower.contains("class 5") || lower.contains("class-5") || lower.contains("class v") -> "৫ম শ্রেণি"
            text.contains("প্রাক") || lower.contains("pre-primary") || lower.contains("nursery") || lower.contains("kg") -> "প্রাক-প্রাথমিক ৪+"
            else -> "১ম শ্রেণি"
        }
    }

    private fun extractClassFromLine(line: String): String = extractClassFromText(line)

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
