package com.example.util

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

enum class AppBengaliFont(
    val id: String,
    val displayNameBn: String,
    val displayNameEn: String,
    val fontFamily: FontFamily,
    val pdfFontFamily: String
) {
    NOTO_SERIF_BENGALI(
        id = "noto_serif_bengali",
        displayNameBn = "নোটো সেরিফ বাংলা (Noto Serif Bengali - ডিফল্ট)",
        displayNameEn = "Noto Serif Bengali (Default)",
        fontFamily = FontFamily.Serif,
        pdfFontFamily = "serif"
    ),
    KALPURUSH(
        id = "kalpurush",
        displayNameBn = "কালপুরুষ (Kalpurush - অফিসিয়াল)",
        displayNameEn = "Kalpurush",
        fontFamily = FontFamily.SansSerif,
        pdfFontFamily = "sans-serif"
    ),
    SOLAIMAN_LIPI(
        id = "solaiman_lipi",
        displayNameBn = "সোলায়মান লিপি (SolaimanLipi)",
        displayNameEn = "SolaimanLipi",
        fontFamily = FontFamily.SansSerif,
        pdfFontFamily = "sans-serif"
    ),
    SIYAM_RUPALI(
        id = "siyam_rupali",
        displayNameBn = "সিয়াম রূপালী (Siyam Rupali)",
        displayNameEn = "Siyam Rupali",
        fontFamily = FontFamily.SansSerif,
        pdfFontFamily = "sans-serif"
    ),
    NOTO_SANS_BENGALI(
        id = "noto_sans_bengali",
        displayNameBn = "নোটো সান্স বাংলা (Noto Sans Bengali)",
        displayNameEn = "Noto Sans Bengali",
        fontFamily = FontFamily.SansSerif,
        pdfFontFamily = "sans-serif"
    ),
    SYSTEM_DEFAULT(
        id = "system_default",
        displayNameBn = "সিস্টেম ডিফল্ট (System Default)",
        displayNameEn = "System Default",
        fontFamily = FontFamily.Default,
        pdfFontFamily = "sans-serif"
    )
}

object FontPreferences {
    private const val PREFS_NAME = "anwesha_font_prefs"
    private const val KEY_SELECTED_FONT = "selected_bengali_font"

    fun getSavedFont(context: Context): AppBengaliFont {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SELECTED_FONT, AppBengaliFont.NOTO_SERIF_BENGALI.id)
        return AppBengaliFont.values().find { it.id == savedId } ?: AppBengaliFont.NOTO_SERIF_BENGALI
    }

    fun saveFont(context: Context, font: AppBengaliFont) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_FONT, font.id).apply()
    }
}
