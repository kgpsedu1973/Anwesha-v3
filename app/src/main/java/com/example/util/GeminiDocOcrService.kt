package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Service to perform multimodal AI Document Extraction using Gemini Flash Vision API
 * Specializing in Bangladeshi National ID (Smart/Laminated NID), Birth Certificates, and School Documents.
 */
object GeminiDocOcrService {

    private const val TAG = "GeminiDocOcrService"
    private const val PREFS_NAME = "gemini_ocr_prefs"
    private const val KEY_GEMINI_API_KEY = "custom_gemini_api_key"
    private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    // Supported Gemini Vision models in order of preference
    private val CANDIDATE_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-pro-preview",
        "gemini-3.5-flash"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Save user-entered Gemini API Key in persistent storage
     */
    fun saveUserApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    /**
     * Retrieve Gemini API Key from SharedPreferences, BuildConfig, or Environment
     */
    fun getApiKey(context: Context? = null): String {
        // 1. Check custom user-saved key from SharedPreferences
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val customKey = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
            if (customKey.isNotBlank()) return customKey
        }

        // 2. Check BuildConfig.GEMINI_API_KEY injected by Secrets Gradle Plugin
        try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            val key = (field.get(null) as? String)?.trim() ?: ""
            if (key.isNotBlank() && !key.startsWith("YOUR_") && !key.contains("DEFAULT_VALUE")) {
                return key
            }
        } catch (e: Exception) {
            // Field not found or reflection failed
        }

        // 3. Check System environment variable
        val envKey = System.getenv("GEMINI_API_KEY")?.trim() ?: ""
        if (envKey.isNotBlank() && !envKey.startsWith("YOUR_")) {
            return envKey
        }

        return ""
    }

    fun isAiAvailable(context: Context? = null): Boolean {
        return getApiKey(context).isNotBlank()
    }

    /**
     * Extract structured Bangladeshi document information using Gemini Multimodal Vision API
     */
    suspend fun extractDocumentWithAi(bitmap: Bitmap, context: Context? = null): Result<ExtractedStudentData> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key পাওয়া যায়নি। অনুগ্রহ করে সেটিংসে আপনার API Key দিন।")
            )
        }

        try {
            // Downscale bitmap if too large to ensure fast network transmission while retaining crystal clarity
            val scaledBitmap = scaleBitmapForAi(bitmap, maxDimension = 1800)
            val base64Image = bitmapToBase64Jpeg(scaledBitmap)

            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            val prompt = """
                You are an expert document OCR engine and parser specialized in Bangladeshi official documents:
                1. National ID Cards (জাতীয় পরিচয়পত্র / Smart NID / Laminated NID / NID Front & Back sides).
                2. Digital Birth Registration Certificates (ডিজিটাল জন্ম ও মৃত্যু নিবন্ধন সনদপত্র / BRIS).
                3. School Admission and Student Registration Forms (ভর্তি ফরম / প্রত্যয়নপত্র).

                Please carefully transcribe all Bengali (বাংলা) and English fields present in the image.
                If both Front and Back of an NID card are visible, extract the name, father, mother, DOB, and NID from the front, and the address, blood group, place of birth, and issue date from the back.

                Return a single JSON object with these EXACT string keys:
                - "document_type": string (e.g. "স্মার্ট জাতীয় পরিচয়পত্র (Smart NID)", "জাতীয় পরিচয়পত্র (NID)", "অনলাইন জন্ম নিবন্ধন সনদ", "ভর্তি ফরম")
                - "name_bn": string (e.g. "রাইয়ান আরা পুর্নি", "মোছাঃ আকলিমা বেগম", "মোঃ আব্দুল্লাহ")
                - "name_en": string (e.g. "RAIYAN ARA PURNY", "Mst. Aklima Begum")
                - "father_name": string (e.g. "মোঃ আবুল বাশার")
                - "mother_name": string (e.g. "সুফিয়া পারভীন")
                - "spouse_name": string (e.g. "মোঃ আমির হোসেন" or empty)
                - "birth_reg_number": string (17-digit birth registration number, digits only)
                - "nid_number": string (10-digit Smart NID, 13-digit or 17-digit NID number, digits only, e.g. "5130557597")
                - "birth_date": string (Standardized as YYYY-MM-DD, e.g. "2001-04-12", "1996-03-28", converting English/Bangla text months)
                - "gender": string ("ছাত্র" or "ছাত্রী" or "পুরুষ" or "নারী")
                - "blood_group": string (e.g. "A+", "B+", "O+", "AB+", "A-", "B-", "O-", "AB-", or empty if not stated)
                - "place_of_birth": string (District or place name, e.g. "ফরিদপুর", "MAGURA", "ঢাকা")
                - "village": string (Village / Road / Gram, e.g. "কটূরাকান্দি", "নিশ্চিন্তপুর")
                - "post_office": string (Post office with postal code, e.g. "শিয়ালদী - ৭৮৬০", "মৌচাক - ১৭৫১")
                - "upazila": string (Upazila / Thana, e.g. "আলফাডাঙ্গা", "কালিয়াকৈর")
                - "district": string (District / Zilla, e.g. "ফরিদপুর", "গাজীপুর")
                - "full_address": string (Complete combined address line, e.g. "গ্রাম/রাস্তা: কটূরাকান্দি, ডাকঘর: শিয়ালদী - ৭৮৬০, আলফাডাঙ্গা, ফরিদপুর")
                - "issue_date": string (Date of issue if present, e.g. "2025-03-24")
                - "raw_extracted_text": string (All detected lines of text in the document)

                RULES:
                - Output ONLY valid JSON. Do not wrap in markdown quotes or add explanations.
                - Preserve all Bengali spelling, matras, and diacritics accurately.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Text Prompt
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Inline Image
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            var lastException: Exception? = null
            val bodyString = requestJson.toString()

            // Try candidate models in sequence
            for (model in CANDIDATE_MODELS) {
                try {
                    val requestUrl = "$API_BASE_URL/$model:generateContent?key=$apiKey"
                    val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(requestUrl)
                        .post(requestBody)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseCode = response.code
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Model $model returned error ($responseCode): $responseBody")
                        lastException = Exception("Gemini API Error ($responseCode): $responseBody")
                        // If model not found or forbidden on this specific model, try next model
                        if (responseCode == 404 || responseCode == 400) {
                            continue
                        } else {
                            break
                        }
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        lastException = Exception("Gemini API থেকে কোনো ফলাফল পাওয়া যায়নি")
                        continue
                    }

                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val textContent = if (parts != null && parts.length() > 0) {
                        parts.getJSONObject(0).optString("text", "")
                    } else ""

                    if (textContent.isBlank()) {
                        lastException = Exception("Gemini ফাঁকা উত্তর দিয়েছে")
                        continue
                    }

                    // Parse text content as JSON
                    val cleanJson = textContent.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val parsedData = JSONObject(cleanJson)

                    val nameBn = parsedData.optString("name_bn", "")
                    val nameEn = parsedData.optString("name_en", "")
                    val fatherName = parsedData.optString("father_name", "")
                    val motherName = parsedData.optString("mother_name", "")
                    val spouseName = parsedData.optString("spouse_name", "")
                    val birthRegNumber = parsedData.optString("birth_reg_number", "").replace(" ", "").replace("-", "")
                    val nidNumber = parsedData.optString("nid_number", "").replace(" ", "").replace("-", "")
                    val birthDate = parsedData.optString("birth_date", "")
                    val rawGender = parsedData.optString("gender", "ছাত্র")
                    val bloodGroup = parsedData.optString("blood_group", "")
                    val placeOfBirth = parsedData.optString("place_of_birth", "")
                    val village = parsedData.optString("village", "")
                    val postOffice = parsedData.optString("post_office", "")
                    val upazila = parsedData.optString("upazila", "")
                    val district = parsedData.optString("district", "")
                    val fullAddress = parsedData.optString("full_address", "")
                    val docType = parsedData.optString("document_type", "স্মার্ট জাতীয় পরিচয়পত্র (Smart NID)")
                    val rawExtracted = parsedData.optString("raw_extracted_text", textContent)

                    val normalizedGender = when {
                        rawGender.contains("নারী") || rawGender.contains("Female", ignoreCase = true) ||
                                rawGender.contains("ছাত্রী") || rawGender.contains("মহিলা") -> "ছাত্রী"
                        else -> "ছাত্র"
                    }

                    val finalAddress = when {
                        fullAddress.isNotBlank() -> fullAddress
                        village.isNotBlank() || upazila.isNotBlank() || district.isNotBlank() -> {
                            listOf(village, postOffice, upazila, district).filter { it.isNotBlank() }.joinToString(", ")
                        }
                        else -> ""
                    }

                    val result = ExtractedStudentData(
                        rawText = if (rawExtracted.isNotBlank()) rawExtracted else textContent,
                        nameBn = nameBn,
                        nameEn = nameEn,
                        fatherName = fatherName.ifBlank { spouseName },
                        motherName = motherName,
                        spouseName = spouseName,
                        nidNumber = nidNumber,
                        birthRegNumber = birthRegNumber,
                        birthDate = birthDate,
                        studentClass = "১ম শ্রেণি",
                        rollNumber = 1,
                        mobileNumber = "",
                        gender = normalizedGender,
                        bloodGroup = bloodGroup,
                        placeOfBirth = placeOfBirth,
                        village = village,
                        postOffice = postOffice,
                        upazila = upazila,
                        district = district,
                        address = finalAddress,
                        documentTypeDetected = docType,
                        extractionSource = "Gemini Vision AI ($model)"
                    )

                    Log.i(TAG, "Successfully extracted Bengali document data via Gemini model $model: ${result.nameBn} / ${result.nameEn}")
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    Log.w(TAG, "Error trying model $model: ${e.message}")
                    lastException = e
                }
            }

            Result.failure(lastException ?: Exception("Gemini Vision AI অনুরোধ সম্পন্ন করা যায়নি"))
        } catch (e: Exception) {
            Log.e(TAG, "Error during Gemini Vision Extraction: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapForAi(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = maxDimension.toFloat() / max(width, height)
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
