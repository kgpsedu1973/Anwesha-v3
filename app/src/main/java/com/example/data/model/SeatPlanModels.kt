package com.example.data.model

/**
 * Field visibility and per-field style & font size configuration for Seat Plan Cards
 */
data class SeatPlanFieldConfig(
    // School Name
    val showSchoolName: Boolean = true,
    val schoolNameFontSizePt: Float = 12.5f,
    val isSchoolNameBold: Boolean = true,

    // School Address
    val showSchoolAddress: Boolean = true,
    val addressFontSizePt: Float = 9.8f,
    val isAddressBold: Boolean = false,

    // Exam / Evaluation Name
    val showExamName: Boolean = true,
    val examNameFontSizePt: Float = 10.5f,
    val isExamNameBold: Boolean = false,

    // Seat Plan Title
    val showSeatPlanTitle: Boolean = true,
    val seatPlanTitleText: String = "আসন বিন্যাস",
    val titleFontSizePt: Float = 11.5f,
    val isTitleBold: Boolean = true,

    // Student Name
    val showStudentName: Boolean = true,
    val studentNameFontSizePt: Float = 11.0f,
    val isStudentNameBold: Boolean = false,

    // Student Class
    val showStudentClass: Boolean = true,
    val classFontSizePt: Float = 11.0f,
    val isClassBold: Boolean = false,
    val classFormat: String = "SHORT", // "SHORT" (১ম), "FULL" (প্রথম), "RAW" (as in DB)

    // Roll Number
    val showRollNumber: Boolean = true,
    val rollFontSizePt: Float = 11.0f,
    val isRollBold: Boolean = false,

    // Optional Room Number
    val showRoomNumber: Boolean = false,
    val roomNumberText: String = "",
    val roomFontSizePt: Float = 10.5f,
    val isRoomBold: Boolean = false,

    // Optional Bench Number
    val showBenchNumber: Boolean = false,
    val benchPrefix: String = "বেঞ্চ: ",
    val benchFontSizePt: Float = 10.5f,
    val isBenchBold: Boolean = false,

    // General Formatting
    val convertBanglaDigits: Boolean = true,
    val cardCornerRadiusDp: Float = 12f, // Rounded rectangle corner radius
    val cardBorderWidthDp: Float = 1.5f,
    val cardBorderStyle: String = "solid" // "solid", "double", "dashed", "dotted", "none"
)

/**
 * Page & Grid setup configuration for Seat Plan Sheet (All measurements in Inches)
 */
data class SeatPlanPageConfig(
    val pageSize: String = "A4", // "A4", "Letter", "Legal"
    val orientation: String = "portrait", // "portrait", "landscape"
    val columns: Int = 2,       // Number of columns per page (default 2)
    val rows: Int = 6,          // Number of rows per page (default 6 => 12 cards)
    val marginTopInch: Float = 0.25f,
    val marginBottomInch: Float = 0.25f,
    val marginLeftInch: Float = 0.25f,
    val marginRightInch: Float = 0.25f,
    val horizontalGapInch: Float = 0.08f,  // Gap between columns in Inches (0.08" ≈ 2mm)
    val verticalGapInch: Float = 0.08f,    // Gap between rows in Inches (0.08" ≈ 2mm)
    val cuttingLineStyle: String = "dotted", // "dotted", "dashed", "solid", "none"
    val cuttingLineColorHex: String = "#94A3B8"
) {
    val totalCardsPerPage: Int
        get() = (columns * rows).coerceAtLeast(1)
}

/**
 * Scope and selection for Seat Plan Generation
 */
data class SeatPlanScopeConfig(
    val scopeType: String = "ALL", // "ALL", "CLASS", "STUDENT"
    val selectedClasses: List<String> = emptyList(),
    val selectedStudentIds: List<String> = emptyList(),
    val sortBy: String = "CLASS_AND_ROLL", // "ROLL", "CLASS_AND_ROLL", "NAME"
    val autoNumberBenches: Boolean = false,
    val startBenchNumber: Int = 1
)

/**
 * Master State for Seat Plan Maker
 */
data class SeatPlanMakerState(
    val schoolName: String = "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
    val schoolAddress: String = "আলফাডাঙ্গা, ফরিদপুর।",
    val examName: String = "২য় প্রান্তিক মূল্যায়ন - ২০২৬",
    val fields: SeatPlanFieldConfig = SeatPlanFieldConfig(),
    val page: SeatPlanPageConfig = SeatPlanPageConfig(),
    val scope: SeatPlanScopeConfig = SeatPlanScopeConfig(),
    val fontId: String = "noto_serif_bengali" // Defaults strictly to Noto Serif Bengali
)

/**
 * Suggested Page Grid Preset
 */
data class SeatPlanGridPreset(
    val titleBn: String,
    val subtitleBn: String,
    val columns: Int,
    val rows: Int,
    val totalCards: Int,
    val isRecommended: Boolean = false
)
