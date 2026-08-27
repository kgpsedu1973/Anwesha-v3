package com.example.data.model

import java.util.UUID

/**
 * Field visibility and style configuration for Seat Plan Cards
 */
data class SeatPlanFieldConfig(
    val showSchoolName: Boolean = true,
    val showSchoolAddress: Boolean = true,
    val showExamName: Boolean = true,
    val showSeatPlanTitle: Boolean = true,
    val seatPlanTitleText: String = "আসন বিন্যাস",
    val showStudentName: Boolean = true,
    val showStudentClass: Boolean = true,
    val showRollNumber: Boolean = true,
    val showSection: Boolean = false,
    val showRoomNumber: Boolean = false,
    val roomNumberText: String = "",
    val showBenchNumber: Boolean = false,
    val benchPrefix: String = "বেঞ্চ: ",
    val convertBanglaDigits: Boolean = true,
    val classFormat: String = "SHORT", // "SHORT" (১ম), "FULL" (প্রথম), "RAW" (as in DB)
    val headerFontSizeScale: Float = 1.0f,
    val contentFontSizeScale: Float = 1.0f,
    val isSchoolNameBold: Boolean = true,
    val isTitleBold: Boolean = true,
    val cardCornerRadiusDp: Float = 12f, // Rounded rectangle corner radius
    val cardBorderWidthDp: Float = 1.5f,
    val cardBorderStyle: String = "solid" // "solid", "double", "dashed", "dotted", "none"
)

/**
 * Page & Grid setup configuration for Seat Plan Sheet
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
    val horizontalGapMm: Float = 2.0f,  // Gap between columns in mm
    val verticalGapMm: Float = 2.0f,    // Gap between rows in mm
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
    val fontId: String = "noto_serif_bengali" // AppBengaliFont ID
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
