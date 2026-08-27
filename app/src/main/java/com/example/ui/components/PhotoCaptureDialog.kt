package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.sqrt

enum class PhotoAspectMode {
    PASSPORT_3_4,
    SQUARE_1_1,
    ORIGINAL
}

enum class PhotoBgMode {
    WHITE,
    OFF_WHITE,
    LIGHT_BLUE,
    ORIGINAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureDialog(
    currentPhotoUri: String?,
    title: String = "ছবি যুক্ত ও প্রসেসিং (Photo Editor)",
    onDismiss: () -> Unit,
    onPhotoSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rawSourceBitmap by remember {
        mutableStateOf<Bitmap?>(loadInitialBitmap(context, currentPhotoUri))
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Control parameters
    var aspectMode by remember { mutableStateOf(PhotoAspectMode.PASSPORT_3_4) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var fixedRotation by remember { mutableStateOf(0) }
    var autoBrighten by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(0f) } // -50..+50
    var contrast by remember { mutableStateOf(1.0f) } // 0.6..1.8
    var bgMode by remember { mutableStateOf(PhotoBgMode.ORIGINAL) }
    var bgTolerance by remember { mutableStateOf(35f) } // 10..80

    // Selected control tab: 0=Aspect/Crop, 1=Straighten/Rotate, 2=Brighten/Light, 3=Background
    var selectedTab by remember { mutableStateOf(0) }

    // Function to re-render processed bitmap
    fun triggerProcessing() {
        val src = rawSourceBitmap ?: return
        coroutineScope.launch {
            isProcessing = true
            val res = withContext(Dispatchers.Default) {
                applyImagePipeline(
                    source = src,
                    aspectMode = aspectMode,
                    fixedRotation = fixedRotation,
                    manualAngle = rotationAngle,
                    autoBrighten = autoBrighten,
                    manualBrightness = brightness,
                    manualContrast = contrast,
                    bgMode = bgMode,
                    bgTolerance = bgTolerance.toInt()
                )
            }
            previewBitmap = res
            isProcessing = false
        }
    }

    // Initial render
    LaunchedEffect(rawSourceBitmap) {
        if (rawSourceBitmap != null) {
            triggerProcessing()
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { rawBitmap ->
        if (rawBitmap != null) {
            rawSourceBitmap = rawBitmap
            // reset transforms for fresh photo
            rotationAngle = 0f
            fixedRotation = 0
            autoBrighten = true
            brightness = 10f
            contrast = 1.05f
            bgMode = PhotoBgMode.ORIGINAL
            triggerProcessing()
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    isProcessing = true
                    val stream: InputStream? = context.contentResolver.openInputStream(uri)
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (decoded != null) {
                        rawSourceBitmap = decoded
                        rotationAngle = 0f
                        fixedRotation = 0
                        autoBrighten = false
                        brightness = 0f
                        contrast = 1.0f
                        bgMode = PhotoBgMode.ORIGINAL
                        triggerProcessing()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source Buttons: Camera & Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher.launch() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_capture_camera"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ক্যামেরা তুলুন", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_pick_gallery"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("গ্যালারি হতে", fontSize = 12.sp)
                    }
                }

                // Photo Preview Box with Dimensions Indicator
                val previewWidth = if (aspectMode == PhotoAspectMode.SQUARE_1_1) 160.dp else 140.dp
                val previewHeight = if (aspectMode == PhotoAspectMode.SQUARE_1_1) 160.dp else 185.dp

                Box(
                    modifier = Modifier
                        .size(previewWidth, previewHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8ECEF))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.AccountBox,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ছবি নির্বাচন করুন", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    if (isProcessing) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                // Quick Action Bar: One-Tap Auto Passport & Reset
                if (rawSourceBitmap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                aspectMode = PhotoAspectMode.PASSPORT_3_4
                                rotationAngle = 0f
                                fixedRotation = 0
                                autoBrighten = true
                                brightness = 15f
                                contrast = 1.1f
                                bgMode = PhotoBgMode.WHITE
                                bgTolerance = 35f
                                triggerProcessing()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_auto_passport_magic")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("১-ক্লিকে পারফেক্ট পাসপোর্ট (Auto)", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = {
                                aspectMode = PhotoAspectMode.PASSPORT_3_4
                                rotationAngle = 0f
                                fixedRotation = 0
                                autoBrighten = false
                                brightness = 0f
                                contrast = 1.0f
                                bgMode = PhotoBgMode.ORIGINAL
                                bgTolerance = 35f
                                triggerProcessing()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("রিসেট", fontSize = 11.sp)
                        }
                    }

                    // Feature Tab Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf("সাইজ (3:4)", "সোজা / কোণ", "উজ্জ্বলতা", "ব্যাকগ্রাউন্ড")
                        tabs.forEachIndexed { index, titleText ->
                            FilterChip(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(titleText, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Tab Content Controls
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (selectedTab) {
                                // 0: Size & Aspect Ratio
                                0 -> {
                                    Text("ছবির অনুপাত নির্বাচন করুন:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            PhotoAspectMode.PASSPORT_3_4 to "পাসপোর্ট (৩:৪)",
                                            PhotoAspectMode.SQUARE_1_1 to "স্কয়ার (১:১)",
                                            PhotoAspectMode.ORIGINAL to "আসল সাইজ"
                                        ).forEach { (mode, label) ->
                                            FilterChip(
                                                selected = aspectMode == mode,
                                                onClick = {
                                                    aspectMode = mode
                                                    triggerProcessing()
                                                },
                                                label = { Text(label, fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // 1: Auto Straighten & Manual Rotate
                                1 -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("কোণ সোজা / রোটেট:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        OutlinedButton(
                                            onClick = {
                                                rotationAngle = 0f
                                                triggerProcessing()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("সোজা করুন (0°)", fontSize = 10.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                fixedRotation = (fixedRotation - 90 + 360) % 360
                                                triggerProcessing()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.RotateLeft, contentDescription = "Rotate 90 Left")
                                        }

                                        Slider(
                                            value = rotationAngle,
                                            onValueChange = {
                                                rotationAngle = it
                                                triggerProcessing()
                                            },
                                            valueRange = -30f..30f,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                fixedRotation = (fixedRotation + 90) % 360
                                                triggerProcessing()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90 Right")
                                        }
                                    }
                                    Text("ম্যানুয়াল কোণ: ${String.format("%.1f", rotationAngle)}°", fontSize = 10.sp, color = Color.Gray)
                                }

                                // 2: Auto Brighten & Custom Light
                                2 -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("আলো ও উজ্জ্বলতা:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        FilledTonalButton(
                                            onClick = {
                                                autoBrighten = !autoBrighten
                                                if (autoBrighten) {
                                                    brightness = 20f
                                                    contrast = 1.15f
                                                } else {
                                                    brightness = 0f
                                                    contrast = 1.0f
                                                }
                                                triggerProcessing()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (autoBrighten) "অটো ব্রাইট: চালু" else "অটো ব্রাইট", fontSize = 10.sp)
                                        }
                                    }

                                    // Brightness Slider
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("উজ্জ্বলতা:", fontSize = 11.sp, modifier = Modifier.width(60.dp))
                                        Slider(
                                            value = brightness,
                                            onValueChange = {
                                                brightness = it
                                                triggerProcessing()
                                            },
                                            valueRange = -40f..40f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Contrast Slider
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("কনট্রাস্ট:", fontSize = 11.sp, modifier = Modifier.width(60.dp))
                                        Slider(
                                            value = contrast,
                                            onValueChange = {
                                                contrast = it
                                                triggerProcessing()
                                            },
                                            valueRange = 0.7f..1.5f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // 3: Background Removal & White Color
                                3 -> {
                                    Text("ব্যাকগ্রাউন্ড রূপান্তর ও হোয়াইট ফিল:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            PhotoBgMode.WHITE to "সাদা (White)",
                                            PhotoBgMode.OFF_WHITE to "অফ-হোয়াইট",
                                            PhotoBgMode.LIGHT_BLUE to "হালকা নীল",
                                            PhotoBgMode.ORIGINAL to "আসল BG"
                                        ).forEach { (mode, label) ->
                                            FilterChip(
                                                selected = bgMode == mode,
                                                onClick = {
                                                    bgMode = mode
                                                    triggerProcessing()
                                                },
                                                label = { Text(label, fontSize = 10.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    if (bgMode != PhotoBgMode.ORIGINAL) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("সেনসিটিভিটি:", fontSize = 11.sp, modifier = Modifier.width(75.dp))
                                            Slider(
                                                value = bgTolerance,
                                                onValueChange = {
                                                    bgTolerance = it
                                                    triggerProcessing()
                                                },
                                                valueRange = 15f..65f,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalBmp = previewBitmap ?: rawSourceBitmap
                    if (finalBmp != null) {
                        val base64 = bitmapToBase64(finalBmp)
                        onPhotoSelected(base64)
                    }
                    onDismiss()
                },
                enabled = previewBitmap != null || rawSourceBitmap != null,
                modifier = Modifier.testTag("btn_save_photo")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

/**
 * High-performance, memory-safe image processing pipeline combining:
 * 1. Rotation / Straightening
 * 2. 3:4 Passport / 1:1 Square Cropping
 * 3. Fast Edge-Aware Background Color Replacement
 * 4. Brightness / Contrast Tone Enhancements
 */
private fun applyImagePipeline(
    source: Bitmap,
    aspectMode: PhotoAspectMode,
    fixedRotation: Int,
    manualAngle: Float,
    autoBrighten: Boolean,
    manualBrightness: Float,
    manualContrast: Float,
    bgMode: PhotoBgMode,
    bgTolerance: Int
): Bitmap {
    val totalAngle = fixedRotation + manualAngle

    // 1. Rotate
    val matrix = Matrix()
    if (abs(totalAngle) > 0.01f) {
        matrix.postRotate(totalAngle)
    }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

    val width = rotated.width
    val height = rotated.height

    // 2. Crop according to aspect ratio
    val targetAspect = when (aspectMode) {
        PhotoAspectMode.PASSPORT_3_4 -> 3f / 4f
        PhotoAspectMode.SQUARE_1_1 -> 1f / 1f
        PhotoAspectMode.ORIGINAL -> width.toFloat() / height.toFloat()
    }
    val currentAspect = width.toFloat() / height.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    val startX: Int
    val startY: Int

    if (currentAspect > targetAspect) {
        cropHeight = height
        cropWidth = (height * targetAspect).toInt().coerceAtMost(width)
        startX = (width - cropWidth) / 2
        startY = 0
    } else {
        cropWidth = width
        cropHeight = (width / targetAspect).toInt().coerceAtMost(height)
        startX = 0
        // Top bias for passport faces (30% from top)
        startY = if (aspectMode == PhotoAspectMode.PASSPORT_3_4) {
            ((height - cropHeight) * 0.30f).toInt().coerceIn(0, height - cropHeight)
        } else {
            ((height - cropHeight) / 2).coerceIn(0, height - cropHeight)
        }
    }

    val cropped = Bitmap.createBitmap(
        rotated,
        startX.coerceIn(0, width - 1),
        startY.coerceIn(0, height - 1),
        cropWidth.coerceAtMost(width - startX),
        cropHeight.coerceAtMost(height - startY)
    )

    // Target dimensions
    val outWidth = when (aspectMode) {
        PhotoAspectMode.PASSPORT_3_4 -> 360
        PhotoAspectMode.SQUARE_1_1 -> 360
        PhotoAspectMode.ORIGINAL -> 400.coerceAtMost(cropped.width)
    }
    val outHeight = (outWidth / targetAspect).toInt()
    val scaled = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)

    // 3. Background Replacement if requested
    val bgProcessed = if (bgMode != PhotoBgMode.ORIGINAL) {
        applyBackgroundReplacement(scaled, bgMode, bgTolerance)
    } else {
        scaled
    }

    // 4. Brightness & Contrast
    var effectiveBrightness = manualBrightness
    var effectiveContrast = manualContrast

    if (autoBrighten) {
        effectiveBrightness += 15f
        effectiveContrast *= 1.10f
    }

    val finalOutput = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(finalOutput)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cm = ColorMatrix().apply {
        val scale = effectiveContrast
        val translate = effectiveBrightness
        set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(bgProcessed, 0f, 0f, paint)

    return finalOutput
}

/**
 * Fast, pure-Kotlin background removal and solid color fill:
 * Samples the 4 corners and upper borders to extract dominant background RGB,
 * then checks color distance against pixels outside the center subject zone.
 */
private fun applyBackgroundReplacement(
    source: Bitmap,
    bgMode: PhotoBgMode,
    tolerance: Int
): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val targetColor = when (bgMode) {
        PhotoBgMode.WHITE -> AndroidColor.WHITE
        PhotoBgMode.OFF_WHITE -> AndroidColor.rgb(248, 249, 250)
        PhotoBgMode.LIGHT_BLUE -> AndroidColor.rgb(227, 242, 253)
        PhotoBgMode.ORIGINAL -> AndroidColor.WHITE
    }

    // Sample background colors from top corners & top edge
    val samplePoints = listOf(
        pixels[0],                                // Top-Left
        pixels[w - 1],                            // Top-Right
        pixels[w / 4],                            // Top 25%
        pixels[(w * 3) / 4],                      // Top 75%
        pixels[((h / 4) * w)],                    // Mid-Left
        pixels[((h / 4) * w) + (w - 1)]           // Mid-Right
    )

    var avgR = 0
    var avgG = 0
    var avgB = 0
    samplePoints.forEach { color ->
        avgR += AndroidColor.red(color)
        avgG += AndroidColor.green(color)
        avgB += AndroidColor.blue(color)
    }
    val count = samplePoints.size
    avgR /= count
    avgG /= count
    avgB /= count

    val tolSq = (tolerance * 2.5).let { it * it }
    val centerX = w / 2
    val centerY = (h * 0.45).toInt()
    val subjectRadiusX = w * 0.38
    val subjectRadiusY = h * 0.45

    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            val p = pixels[idx]
            val r = AndroidColor.red(p)
            val g = AndroidColor.green(p)
            val b = AndroidColor.blue(p)

            val dr = r - avgR
            val dg = g - avgG
            val db = b - avgB
            val distSq = dr * dr + dg * dg + db * db

            // Normalize distance from subject center (ellipse check)
            val dxNorm = (x - centerX) / subjectRadiusX
            val dyNorm = (y - centerY) / subjectRadiusY
            val inSubjectCore = (dxNorm * dxNorm + dyNorm * dyNorm) < 0.65

            if (!inSubjectCore && distSq < tolSq) {
                pixels[idx] = targetColor
            }
        }
    }

    val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    resultBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    return resultBitmap
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val byteArray = outputStream.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

private fun loadInitialBitmap(context: Context, uriOrBase64: String?): Bitmap? {
    if (uriOrBase64.isNullOrBlank()) return null
    return try {
        if (uriOrBase64.startsWith("data:image")) {
            val base64Data = uriOrBase64.substringAfter("base64,")
            val decodedString = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
        } else {
            val uri = Uri.parse(uriOrBase64)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bmp
        }
    } catch (e: Exception) {
        null
    }
}
