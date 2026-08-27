package com.example.data.model

import java.util.UUID

/**
 * Data structures for Admit Card Maker & Routine Generator
 */
data class RoutineDay(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "", // YYYY-MM-DD
    val day: String = "",  // e.g. "রবিবার"
    val subjects: List<String> = listOf("")
)

data class AdmitCardSettings(
    val pageSize: String = "A4", // "A4", "Letter", "Legal"
    val orientation: String = "portrait", // "portrait", "landscape"
    val cardsPerPage: Int = 4,   // 1, 2, 3, 4, 5, 6, 8
    val marginTop: Float = 0.25f,
    val marginBottom: Float = 0.25f,
    val marginLeft: Float = 0.25f,
    val marginRight: Float = 0.25f,
    val vGap: Float = 0.5f,
    val frameStyle: String = "dashed", // "solid", "dashed", "dotted", "double", "none"
    val cardFont: String = "serif",   // "serif", "sans"
    val sigSize: String = "3"         // "1", "2", "3", "4", "5"
)

data class AdmitCardStudent(
    val id: String,
    val name: String,
    val studentClass: String,
    val rollNumber: String,
    val fatherName: String = "",
    val motherName: String = ""
)

data class AdmitCardMakerState(
    val schoolName: String = "",
    val schoolAddress: String = "",
    val examName: String = "দ্বিতীয় প্রান্তিক মূল্যায়ন - ২০২৬",
    val subjects: List<String> = listOf(
        "বাংলা",
        "ইংরেজি",
        "প্রাথমিক বিজ্ঞান",
        "বাংলাদেশ ও বিশ্বপরিচয়",
        "প্রাথমিক গণিত",
        "ধর্ম ও নৈতিক শিক্ষা",
        "শারীরিক ও মানসিক স্বাস্থ্য শিক্ষা",
        "চারু ও কারুকলা"
    ),
    val classes: List<String> = listOf(
        "প্রাক-প্রাথমিক",
        "প্রথম",
        "দ্বিতীয়",
        "তৃতীয়",
        "চতুর্থ",
        "পঞ্চম"
    ),
    val timePresets: List<String> = listOf(
        "১০:০০-১১:০০",
        "১০:০০-১২:৩০",
        "০১:০০-০৩:৩০"
    ),
    val defaultTime: String = "১০:০০-১১:০০",
    val classTimes: Map<String, String> = emptyMap(),
    val classRoutines: Map<String, List<RoutineDay>> = emptyMap(),
    val signature: String = "", // Base64 or URI
    val settings: AdmitCardSettings = AdmitCardSettings(),
    val scope: String = "all",  // "all", "class", "student"
    val selectedClasses: List<String> = emptyList(),
    val selectedStudentIds: List<String> = emptyList(),
    val activeRoutineKey: String = "BASE"
)

/**
 * Tools Hub Tool Item representation for expandable architecture
 */
enum class ToolStatus(val labelBn: String, val labelEn: String) {
    ACTIVE("সক্রিয়", "Active"),
    COMING_SOON("শীঘ্রই আসছে", "Coming Soon"),
    PLANNED("পরিকল্পিত", "Planned")
}

data class ToolItem(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val descriptionBn: String,
    val descriptionEn: String,
    val iconName: String,
    val status: ToolStatus = ToolStatus.COMING_SOON,
    val categoryBn: String = "পরীক্ষা ও মূল্যায়ন",
    val route: String = ""
)
