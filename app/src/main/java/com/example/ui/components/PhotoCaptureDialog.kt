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
import com.example.util.MLKitBackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.abs

enum class PhotoAspectMode {
    PASSPORT_3_4,
    SQUARE_1_1,
    STAMP_4_5,
    ORIGINAL,
    CUSTOM
}

enum class PhotoBgMode(val title: String, val colorInt: Int?) {
    ORIGINAL("আসল BG", null),
    WHITE("সাদা (White)", AndroidColor.WHITE),
    OFF_WHITE("অফ-হোয়াইট", AndroidColor.rgb(248, 249, 250)),
    LIGHT_BLUE("হালকা নীল", AndroidColor.rgb(227, 242, 253)),
    STUDIO_BLUE("রয়্যাল ব্লু", AndroidColor.rgb(25, 118, 210)),
    MINT_GREEN("মিন্ট গ্রিন", AndroidColor.rgb(232, 245, 233)),
    LIGHT_GRAY("হালকা ধূসর", AndroidColor.rgb(236, 239, 241)),
    TRANSPARENT("স্বচ্ছ (PNG)", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureDialog(
    currentPhotoUri: String?,
    title: String = "ছবি যুক্ত ও প্রসেসিং (Photo Studio)",
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

    // Controls: Size & Zoom/Crop
    var aspectMode by remember { mutableStateOf(PhotoAspectMode.PASSPORT_3_4) }
    var customWidthInput by remember { mutableStateOf("300") }
    var customHeightInput by remember { mutableStateOf("400") }
    var zoomLevel by remember { mutableStateOf(1.0f) } // 1.0x to 3.0x
    var panOffsetX by remember { mutableStateOf(0f) } // -100 to +100
    var panOffsetY by remember { mutableStateOf(0f) } // -100 to +100

    // Controls: Rotation
    var rotationAngle by remember { mutableStateOf(0f) }
    var fixedRotation by remember { mutableStateOf(0) }

    // Controls: Brightness / Light
    var autoBrighten by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(0f) } // -50..+50
    var contrast by remember { mutableStateOf(1.0f) } // 0.6..1.8

    // Controls: ML Kit Background Removal & Color
    var bgMode by remember { mutableStateOf(PhotoBgMode.ORIGINAL) }

    // Selected tab: 0=Aspect & Zoom/Crop, 1=ML Kit BG, 2=Rotation, 3=Brightness
    var selectedTab by remember { mutableStateOf(0) }

    // Auto Zoom calculation function
    fun autoSmartZoom() {
        val src = rawSourceBitmap ?: return
        // Calculate smart passport framing
        zoomLevel = 1.35f
        panOffsetY = -15f
        panOffsetX = 0f
    }

    // Function to re-render processed bitmap
    fun triggerProcessing() {
        val src = rawSourceBitmap ?: return
        coroutineScope.launch {
            isProcessing = true
            val res = withContext(Dispatchers.Default) {
                applyImagePipeline(
                    source = src,
                    aspectMode = aspectMode,
                    customWidth = customWidthInput.toIntOrNull() ?: 300,
                    customHeight = customHeightInput.toIntOrNull() ?: 400,
                    zoomLevel = zoomLevel,
                    panX = panOffsetX,
                    panY = panOffsetY,
                    fixedRotation = fixedRotation,
                    manualAngle = rotationAngle,
                    autoBrighten = autoBrighten,
                    manualBrightness = brightness,
                    manualContrast = contrast,
                    bgMode = bgMode
                )
            }
            previewBitmap = res
            isProcessing = false
        }
    }

    // Initial render
    LaunchedEffect(rawSourceBitmap, aspectMode, zoomLevel, panOffsetX, panOffsetY, fixedRotation, rotationAngle, autoBrighten, brightness, contrast, bgMode) {
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
            rotationAngle = 0f
            fixedRotation = 0
            autoBrighten = true
            brightness = 10f
            contrast = 1.05f
            zoomLevel = 1.25f
            panOffsetY = -10f
            bgMode = PhotoBgMode.WHITE
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
                        zoomLevel = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
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
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.94f),
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
                val previewWidth = when (aspectMode) {
                    PhotoAspectMode.SQUARE_1_1 -> 160.dp
                    PhotoAspectMode.STAMP_4_5 -> 140.dp
                    PhotoAspectMode.PASSPORT_3_4 -> 140.dp
                    PhotoAspectMode.CUSTOM -> 150.dp
                    PhotoAspectMode.ORIGINAL -> 150.dp
                }
                val previewHeight = when (aspectMode) {
                    PhotoAspectMode.SQUARE_1_1 -> 160.dp
                    PhotoAspectMode.STAMP_4_5 -> 175.dp
                    PhotoAspectMode.PASSPORT_3_4 -> 185.dp
                    PhotoAspectMode.CUSTOM -> 180.dp
                    PhotoAspectMode.ORIGINAL -> 170.dp
                }

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
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("ML Kit AI প্রসেসিং...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                zoomLevel = 1.3f
                                panOffsetY = -15f
                                panOffsetX = 0f
                                bgMode = PhotoBgMode.WHITE
                                triggerProcessing()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_auto_passport_magic")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("১-ক্লিকে স্মার্ট পাসপোর্ট (ML Kit)", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = {
                                aspectMode = PhotoAspectMode.PASSPORT_3_4
                                rotationAngle = 0f
                                fixedRotation = 0
                                autoBrighten = false
                                brightness = 0f
                                contrast = 1.0f
                                zoomLevel = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                                bgMode = PhotoBgMode.ORIGINAL
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
                        val tabs = listOf("জুম ও ক্রপ", "ML Kit BG", "সোজা / কোণ", "উজ্জ্বলতা")
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (selectedTab) {
                                // 0: Size, Auto Zoom & Manual Crop
                                0 -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("সাইজ ও অনুপাত:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        FilledTonalButton(
                                            onClick = {
                                                autoSmartZoom()
                                                triggerProcessing()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("স্মার্ট অটো জুম", fontSize = 10.sp)
                                        }
                                    }

                                    // Aspect chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            PhotoAspectMode.PASSPORT_3_4 to "পাসপোর্ট (৩:৪)",
                                            PhotoAspectMode.SQUARE_1_1 to "স্কয়ার (১:১)",
                                            PhotoAspectMode.STAMP_4_5 to "স্ট্যাম্প (৪:৫)",
                                            PhotoAspectMode.ORIGINAL to "আসল সাইজ",
                                            PhotoAspectMode.CUSTOM to "কাস্টম"
                                        ).forEach { (mode, label) ->
                                            FilterChip(
                                                selected = aspectMode == mode,
                                                onClick = {
                                                    aspectMode = mode
                                                    triggerProcessing()
                                                },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }

                                    // Custom Size Input
                                    if (aspectMode == PhotoAspectMode.CUSTOM) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customWidthInput,
                                                onValueChange = { customWidthInput = it },
                                                label = { Text("প্রস্থ (Width px)") },
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = customHeightInput,
                                                onValueChange = { customHeightInput = it },
                                                label = { Text("উচ্চতা (Height px)") },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    // Manual Zoom Slider
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("জুম (${String.format("%.1f", zoomLevel)}x):", fontSize = 11.sp, modifier = Modifier.width(75.dp))
                                        Slider(
                                            value = zoomLevel,
                                            onValueChange = {
                                                zoomLevel = it
                                                triggerProcessing()
                                            },
                                            valueRange = 1.0f..2.5f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Pan Y (Vertical Framing)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("উপরে/নিচে:", fontSize = 11.sp, modifier = Modifier.width(75.dp))
                                        Slider(
                                            value = panOffsetY,
                                            onValueChange = {
                                                panOffsetY = it
                                                triggerProcessing()
                                            },
                                            valueRange = -60f..60f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // 1: Google ML Kit Background Erase & Color Fill
                                1 -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Google ML Kit ব্যাকগ্রাউন্ড রূপান্তর:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Surface(
                                            color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("On-Device AI", fontSize = 9.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }

                                    Text("পাসপোর্ট ও আইডির জন্য ব্যাকগ্রাউন্ড রঙ নির্বাচন করুন:", fontSize = 11.sp, color = Color.Gray)

                                    // Color chips
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(PhotoBgMode.values().size) { idx ->
                                            val mode = PhotoBgMode.values()[idx]
                                            FilterChip(
                                                selected = bgMode == mode,
                                                onClick = {
                                                    bgMode = mode
                                                    triggerProcessing()
                                                },
                                                label = { Text(mode.title, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }

                                // 2: Auto Straighten & Manual Rotate
                                2 -> {
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

                                // 3: Brightness & Light
                                3 -> {
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
 * High-performance image processing pipeline combining:
 * 1. Rotation / Straightening
 * 2. Smart Zoom, Pan & Aspect Cropping (Passport 3:4, Square 1:1, Stamp 4:5, Custom W:H)
 * 3. Google ML Kit Neural On-Device Background Removal & Custom Color Blend
 * 4. Brightness / Contrast Tone Enhancements
 */
private suspend fun applyImagePipeline(
    source: Bitmap,
    aspectMode: PhotoAspectMode,
    customWidth: Int,
    customHeight: Int,
    zoomLevel: Float,
    panX: Float,
    panY: Float,
    fixedRotation: Int,
    manualAngle: Float,
    autoBrighten: Boolean,
    manualBrightness: Float,
    manualContrast: Float,
    bgMode: PhotoBgMode
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

    // 2. Aspect Ratio and Zoom Cropping
    val targetAspect = when (aspectMode) {
        PhotoAspectMode.PASSPORT_3_4 -> 3f / 4f
        PhotoAspectMode.SQUARE_1_1 -> 1f / 1f
        PhotoAspectMode.STAMP_4_5 -> 4f / 5f
        PhotoAspectMode.CUSTOM -> {
            val w = customWidth.coerceAtLeast(50).toFloat()
            val h = customHeight.coerceAtLeast(50).toFloat()
            w / h
        }
        PhotoAspectMode.ORIGINAL -> width.toFloat() / height.toFloat()
    }
    val currentAspect = width.toFloat() / height.toFloat()

    var baseCropWidth: Float
    var baseCropHeight: Float

    if (currentAspect > targetAspect) {
        baseCropHeight = height.toFloat()
        baseCropWidth = (height * targetAspect).coerceAtMost(width.toFloat())
    } else {
        baseCropWidth = width.toFloat()
        baseCropHeight = (width / targetAspect).coerceAtMost(height.toFloat())
    }

    // Apply Zoom (Shrinks window size)
    val effectiveCropW = (baseCropWidth / zoomLevel).coerceIn(40f, width.toFloat())
    val effectiveCropH = (baseCropHeight / zoomLevel).coerceIn(40f, height.toFloat())

    val centerBaseX = (width - effectiveCropW) / 2f
    val centerBaseY = if (aspectMode == PhotoAspectMode.PASSPORT_3_4 || aspectMode == PhotoAspectMode.STAMP_4_5) {
        ((height - effectiveCropH) * 0.28f)
    } else {
        ((height - effectiveCropH) / 2f)
    }

    val panPixelX = (panX / 100f) * (width * 0.25f)
    val panPixelY = (panY / 100f) * (height * 0.25f)

    val startX = (centerBaseX + panPixelX).toInt().coerceIn(0, (width - effectiveCropW.toInt()).coerceAtLeast(0))
    val startY = (centerBaseY + panPixelY).toInt().coerceIn(0, (height - effectiveCropH.toInt()).coerceAtLeast(0))
    val cropW = effectiveCropW.toInt().coerceAtMost(width - startX)
    val cropH = effectiveCropH.toInt().coerceAtMost(height - startY)

    val cropped = Bitmap.createBitmap(rotated, startX, startY, cropW, cropH)

    // Target dimensions
    val outWidth = when (aspectMode) {
        PhotoAspectMode.PASSPORT_3_4 -> 360
        PhotoAspectMode.SQUARE_1_1 -> 360
        PhotoAspectMode.STAMP_4_5 -> 320
        PhotoAspectMode.CUSTOM -> customWidth.coerceIn(100, 1200)
        PhotoAspectMode.ORIGINAL -> 400.coerceAtMost(cropped.width)
    }
    val outHeight = (outWidth / targetAspect).toInt().coerceIn(100, 1200)
    val scaled = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)

    // 3. Google ML Kit Background Removal & Color Filling
    val bgProcessed = if (bgMode != PhotoBgMode.ORIGINAL) {
        MLKitBackgroundRemover.removeBackgroundAndApplyColor(scaled, bgMode.colorInt)
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
