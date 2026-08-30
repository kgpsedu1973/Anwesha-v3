package com.example.util

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
 * Service to perform multimodal AI Document Extraction using Gemini Flash API (gemini-3.5-flash)
 * Specializing in Bangladeshi Birth Certificates, National ID (NID/Smart NID), and Student Records.
 */
object GeminiDocOcrService {

    private const val TAG = "GeminiDocOcrService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrieve Gemini API Key from BuildConfig or Environment variables
     */
    fun getApiKey(): String {
        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            val key = (field.get(null) as? String)?.trim() ?: ""
            if (key.isNotBlank() && !key.startsWith("YOUR_") && !key.contains("DEFAULT_VALUE")) {
                key
            } else {
                System.getenv("GEMINI_API_KEY")?.trim() ?: ""
            }
        } catch (e: Exception) {
            System.getenv("GEMINI_API_KEY")?.trim() ?: ""
        }
    }

    fun isAiAvailable(): Boolean = getApiKey().isNotBlank()

    /**
     * Extract structured Bangladeshi document information using Gemini 3.5 Flash Vision
     */
    suspend fun extractDocumentWithAi(bitmap: Bitmap): Result<ExtractedStudentData> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API Key পাওয়া যায়নি। Secrets প্যানেলে GEMINI_API_KEY যোগ করুন।"))
        }

        try {
            // Downscale bitmap if too large to ensure fast network transmission
            val scaledBitmap = scaleBitmapForAi(bitmap, maxDimension = 1600)
            val base64Image = bitmapToBase64Jpeg(scaledBitmap)

            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            val prompt = """
                You are an expert document OCR and parser for Bangladeshi official documents (Birth Registration Certificates / জন্ম নিবন্ধন সনদ, National ID Smart Cards & Laminated NID / জাতীয় পরিচয়পত্র, and School Admission/Transfer Forms).
                Carefully read all Bengali (বাংলা) and English text in the attached image, including printed text, table boxes, spaced digits, barcodes, MRZ lines, and rubber stamps.

                Return a single JSON object with these EXACT keys:
                - "document_type": string (e.g. "অনলাইন জন্ম নিবন্ধন সনদ", "জাতীয় পরিচয়পত্র (NID)", "স্মার্ট জাতীয় পরিচয়পত্র", "ভর্তি ফরম", etc.)
                - "name_bn": string (Full Bengali name, e.g. "মোছাঃ আকলিমা বেগম", "শারমিন আফরোজা চৌধুরী", "সুফিয়া পারভীন")
                - "name_en": string (Full English name, e.g. "Mst. Aklima Begum", "Sharmine Afroja Chowdhury", "SUFIA PARVIN")
                - "father_name": string (Father's name in Bengali or English)
                - "mother_name": string (Mother's name in Bengali or English)
                - "spouse_name": string (Husband/Wife name if present, e.g. from স্বামী/স্ত্রী field)
                - "birth_reg_number": string (17-digit Birth Registration Number, with all spaces/hyphens removed)
                - "nid_number": string (National ID number: 10, 13, or 17 digits, with spaces removed)
                - "birth_date": string (Standardized as YYYY-MM-DD, e.g. "1996-03-28", "1978-01-01", "1977-04-15", converting any text months like Jan, Feb, Apr, etc.)
                - "gender": string ("ছাত্র" or "ছাত্রী" or "পুরুষ" or "নারী")
                - "blood_group": string (e.g. "A+", "B+", "O+", "AB+", or empty)
                - "place_of_birth": string (District or place name, e.g. "MAGURA", "ফরিদপুর", or empty)
                - "village": string (Village / Area / Gram, e.g. "কটুরাকান্দি", "নিশ্চিন্তপুর", "গ্রীন ভ্যালি")
                - "post_office": string (Post office with code if available, e.g. "শিয়ালদী - ৭৮৭০", "মৌচাক - ১৭৫১")
                - "upazila": string (Upazila/Thana, e.g. "আলফাডাঙ্গা", "কালিয়াকৈর")
                - "district": string (District / Zilla, e.g. "ফরিদপুর", "গাজীপুর", "চট্টগ্রাম")
                - "full_address": string (Complete full address combining all parts)
                - "raw_extracted_text": string (All detected lines of text in the document)

                IMPORTANT:
                - Return ONLY the raw JSON object. Do not include markdown code block formatting (```json ... ```) or conversational commentary.
                - Keep all Bengali spellings exact and intact.
                - Separate Bengali name and English name cleanly even if they are on the same line in the document.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Part 1: Text Prompt
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Part 2: Inline Base64 Image
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

            val requestUrl = "$API_BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(requestUrl)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Gemini API failed (${response.code}): $errBody")
                return@withContext Result.failure(Exception("Gemini API Error (${response.code}): $errBody"))
            }

            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)

            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Gemini API থেকে কোনো ফলাফল পাওয়া যায়নি"))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textContent = if (parts != null && parts.length() > 0) {
                parts.getJSONObject(0).optString("text", "")
            } else ""

            if (textContent.isBlank()) {
                return@withContext Result.failure(Exception("Gemini ফাঁকা উত্তর দিয়েছে"))
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
            val docType = parsedData.optString("document_type", "স্মার্ট নথি (Document)")
            val rawExtracted = parsedData.optString("raw_extracted_text", textContent)

            val normalizedGender = when {
                rawGender.contains("নারী") || rawGender.contains("Female", ignoreCase = true) || rawGender.contains("ছাত্রী") || rawGender.contains("মহিলা") -> "ছাত্রী"
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
                extractionSource = "Gemini AI ভিশন"
            )

            Log.i(TAG, "Successfully extracted Bengali document data via Gemini Vision AI: ${result.nameBn} / ${result.nameEn}")
            Result.success(result)
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
