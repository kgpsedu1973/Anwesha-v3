package com.example.ui.screens.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.entity.StudentDocumentEntity
import com.example.data.local.entity.StudentEntity
import com.example.domain.model.OcrLanguage
import com.example.domain.usecase.DocumentEdgeDetectionUseCase
import com.example.domain.usecase.EnhancementMode
import com.example.domain.usecase.ImageEnhancementUseCase
import com.example.util.BanglaUtils
import com.example.util.DocScannerOcrHelper
import com.example.util.ExtractedStudentData
import com.example.util.GeminiDocOcrService
import com.example.viewmodel.MainViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allStudents by viewModel.allStudents.collectAsState()
    val allStudentDocuments by viewModel.allStudentDocuments.collectAsState()

    val imageEnhancementUseCase = remember { ImageEnhancementUseCase() }
    val documentEdgeDetectionUseCase = remember { DocumentEdgeDetectionUseCase() }

    // State for Scanned Pages & Output
    var scannedPageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var scannedPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Loaded Bitmap & Filter states for current page
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentFilter by remember { mutableStateOf(EnhancementMode.MAGIC_COLOR) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var isEnhancing by remember { mutableStateOf(false) }

    // OCR & Extracted Data state
    var selectedOcrLanguage by remember { mutableStateOf(OcrLanguage.BENGALI_AND_ENGLISH) }
    var isOcrProcessing by remember { mutableStateOf(false) }
    var extractedData by remember { mutableStateOf<ExtractedStudentData?>(null) }
    var rawOcrText by remember { mutableStateOf("") }
    var ocrConfidence by remember { mutableStateOf(0f) }
    var activeTab by remember { mutableStateOf("extracted") } // "extracted", "editor", "raw_ocr", "archive"

    // Temporary camera capture Uri state
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Dialog States
    var showStudentImportDialog by remember { mutableStateOf(false) }
    var showLinkExistingStudentDialog by remember { mutableStateOf(false) }
    var viewingDocumentDetail by remember { mutableStateOf<StudentDocumentEntity?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Archive search query
    var archiveSearchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        if (showStudentImportDialog || showLinkExistingStudentDialog || viewingDocumentDetail != null) {
            showStudentImportDialog = false
            showLinkExistingStudentDialog = false
            viewingDocumentDetail = null
        } else {
            onNavigateBack()
        }
    }

    // Function to run Multimodal Gemini Flash AI extraction on current document bitmap
    fun runGeminiAiExtraction(bitmap: Bitmap) {
        coroutineScope.launch {
            isOcrProcessing = true
            statusMessage = "Gemini AI ভিশন দিয়ে বাংলা ও ইংরেজি তথ্য নির্ভুলভাবে সনাক্ত করা হচ্ছে..."
            try {
                val aiResult = GeminiDocOcrService.extractDocumentWithAi(bitmap)
                aiResult.onSuccess { data ->
                    extractedData = data
                    rawOcrText = data.rawText
                    statusMessage = "Gemini AI ভিশন দিয়ে তথ্য সফলভাবে এক্সট্রাক্ট করা হয়েছে!"
                    Toast.makeText(context, "AI তথ্য সনাক্তকরণ সফল!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    // Fallback to offline OCR
                    val offlineData = DocScannerOcrHelper.extractStudentInformation(rawOcrText)
                    extractedData = offlineData
                    statusMessage = "অফলাইন OCR ব্যবহার করা হয়েছে (${err.localizedMessage})"
                    Toast.makeText(context, "অফলাইন OCR ডেটা লোড হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val offlineData = DocScannerOcrHelper.extractStudentInformation(rawOcrText)
                extractedData = offlineData
                statusMessage = "অফলাইন OCR ব্যবহার করা হয়েছে"
            } finally {
                isOcrProcessing = false
            }
        }
    }

    // Function to reload bitmap, apply edge detection/deskew (if needed), and run unified enhancement + Tesseract OCR
    fun processCurrentPage(uri: Uri, shouldStraighten: Boolean = false, language: OcrLanguage = selectedOcrLanguage, runAiIfAvailable: Boolean = true) {
        coroutineScope.launch {
            isOcrProcessing = true
            try {
                val rawBmp = DocScannerOcrHelper.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
                val bmp = if (shouldStraighten) {
                    documentEdgeDetectionUseCase.straightenOrDeskew(rawBmp)
                } else {
                    rawBmp
                }

                currentBitmap = bmp
                rotationAngle = 0f
                currentFilter = EnhancementMode.MAGIC_COLOR

                // Run unified enhancement pipeline off main thread
                val enhanced = imageEnhancementUseCase.execute(bmp, EnhancementMode.MAGIC_COLOR, 0f)
                processedBitmap = enhanced

                // Perform Bengali/English Tesseract OCR on enhanced bitmap
                val ocrResult = viewModel.ocrUseCase.recognizeText(enhanced, language)
                rawOcrText = ocrResult.recognizedText
                ocrConfidence = ocrResult.meanConfidence

                val parsed = DocScannerOcrHelper.extractStudentInformation(ocrResult.recognizedText)
                extractedData = parsed

                // If Gemini API is available and enabled, auto-enhance with AI
                if (runAiIfAvailable && GeminiDocOcrService.isAiAvailable()) {
                    val aiResult = GeminiDocOcrService.extractDocumentWithAi(enhanced)
                    aiResult.onSuccess { aiData ->
                        extractedData = aiData
                        rawOcrText = aiData.rawText
                    }
                }

                statusMessage = if (shouldStraighten) {
                    "ডকুমেন্ট সোজা করা ও OCR বিশ্লেষণ সম্পন্ন (${language.displayNameBn})"
                } else {
                    "ডকুমেন্ট পরিবর্ধিত ও OCR বিশ্লেষণ সম্পন্ন (${language.displayNameBn})"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                statusMessage = "প্রসেসিং ত্রুটি: ${e.localizedMessage}"
                Toast.makeText(context, "OCR প্রক্রিয়াকরণে ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isOcrProcessing = false
            }
        }
    }

    // Google ML Kit Document Scanner Activity Result Launcher (Mode: BASE for capture + corner detection only)
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                val pages = scanResult.pages?.mapNotNull { it.imageUri } ?: emptyList()
                val pdf = scanResult.pdf?.uri

                if (pages.isNotEmpty()) {
                    scannedPageUris = pages
                    selectedPageIndex = 0
                    scannedPdfUri = pdf
                    processCurrentPage(pages[0], shouldStraighten = false)
                    Toast.makeText(context, "${pages.size}টি পৃষ্ঠা স্ক্যান সম্পন্ন!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Direct Camera Photo Capture Launcher (applies OpenCV auto-edge detection & perspective correction)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            val capturedUri = tempCameraImageUri!!
            scannedPageUris = listOf(capturedUri)
            selectedPageIndex = 0
            scannedPdfUri = null
            processCurrentPage(capturedUri, shouldStraighten = true)
            Toast.makeText(context, "ছবি ধারণ ও টেসার্যাক্ট OCR সম্পন্ন!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Multiple/Single Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            scannedPageUris = uris
            selectedPageIndex = 0
            scannedPdfUri = null
            processCurrentPage(uris[0], shouldStraighten = true)
            Toast.makeText(context, "${uris.size}টি ডকুমেন্ট গ্যালারি থেকে লোড ও বিশ্লেষণ সম্পন্ন!", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchDirectCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(context.cacheDir, "camera_photos")
            if (!storageDir.exists()) storageDir.mkdirs()
            val imageFile = File(storageDir, "DOC_${timeStamp}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            tempCameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "ক্যামেরা চালু করা যায়নি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchMlKitDocumentScanner() {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(15)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .build()

            val scanner = GmsDocumentScanning.getClient(options)
            val activity = context as? Activity
            if (activity != null) {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        scannerLauncher.launch(request)
                    }
                    .addOnFailureListener {
                        launchDirectCamera()
                    }
            } else {
                launchDirectCamera()
            }
        } catch (e: Exception) {
            launchDirectCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্মার্ট ডকুমেন্ট স্ক্যানার ও OCR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "অন-ডিভাইস Tesseract OCR (বাংলা + ইংরেজি) ও শিক্ষার্থী ডাটাবেস",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পিছনে যান")
                    }
                },
                actions = {
                    if (scannedPdfUri != null) {
                        IconButton(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, scannedPdfUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "পিডিএফ ফাইল শেয়ার করুন"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "শেয়ার ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF Share", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Scanner Action Hero Header
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.DocumentScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "অন-ডিভাইস OCR স্ক্যানার",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "100% অফলাইন",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "বাংলা ও ইংরেজি হস্তলিপি/ছাপা ডকুমেন্ট থেকে স্বয়ংক্রিয় ডাটা এক্সট্রাকশন",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { launchMlKitDocumentScanner() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(42.dp)
                                    .testTag("btn_launch_scanner")
                            ) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("স্ক্যানার", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { launchDirectCamera() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("btn_launch_camera")
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ক্যামেরা", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("btn_launch_gallery")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("গ্যালারি", fontSize = 12.sp)
                            }
                        }

                        // OCR Mode & Language Selection Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ইঞ্জিন:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // AI Vision Action Button
                            FilterChip(
                                selected = extractedData?.extractionSource?.contains("Gemini") == true,
                                onClick = {
                                    if (processedBitmap != null) {
                                        runGeminiAiExtraction(processedBitmap!!)
                                    } else if (scannedPageUris.isNotEmpty()) {
                                        processCurrentPage(scannedPageUris[selectedPageIndex], runAiIfAvailable = true)
                                    } else {
                                        Toast.makeText(context, "প্রথমে একটি ডকুমেন্ট স্ক্যান বা গ্যালারি থেকে সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Gemini AI ভিশন", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                },
                                modifier = Modifier.height(28.dp)
                            )

                            OcrLanguage.values().forEach { lang ->
                                val isSelected = selectedOcrLanguage == lang && extractedData?.extractionSource?.contains("Gemini") != true
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedOcrLanguage = lang
                                        if (processedBitmap != null) {
                                            coroutineScope.launch {
                                                isOcrProcessing = true
                                                try {
                                                    val res = viewModel.ocrUseCase.recognizeText(processedBitmap!!, lang)
                                                    rawOcrText = res.recognizedText
                                                    ocrConfidence = res.meanConfidence
                                                    extractedData = DocScannerOcrHelper.extractStudentInformation(res.recognizedText)
                                                    Toast.makeText(context, "অফলাইন OCR সম্পন্ন (${lang.displayNameBn})", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isOcrProcessing = false
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(lang.displayNameBn, fontSize = 10.5.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Scanned Pages Preview & Navigation Strip
            if (scannedPageUris.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "স্ক্যানকৃত পৃষ্ঠা (${BanglaUtils.toBanglaDigits((selectedPageIndex + 1).toString())}/${BanglaUtils.toBanglaDigits(scannedPageUris.size.toString())})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (scannedPdfUri != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "PDF তৈরি সম্পন্ন",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Thumbnail horizontal list
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(scannedPageUris) { idx, uri ->
                                val isSelected = idx == selectedPageIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 75.dp)
                                        .clickable {
                                            if (selectedPageIndex != idx) {
                                                selectedPageIndex = idx
                                                processCurrentPage(uri)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Tabs (Extracted Data, Filter Studio, Raw OCR Text, Archive)
                item {
                    TabRow(
                        selectedTabIndex = when (activeTab) {
                            "extracted" -> 0
                            "editor" -> 1
                            "raw_ocr" -> 2
                            else -> 3
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == "extracted",
                            onClick = { activeTab = "extracted" },
                            text = { Text("এক্সট্রাক্ট তথ্য", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                        Tab(
                            selected = activeTab == "editor",
                            onClick = { activeTab = "editor" },
                            text = { Text("ফিল্টার/ক্রপ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                        Tab(
                            selected = activeTab == "raw_ocr",
                            onClick = { activeTab = "raw_ocr" },
                            text = { Text("OCR টেক্সট", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                        Tab(
                            selected = activeTab == "archive",
                            onClick = { activeTab = "archive" },
                            text = { Text("সংযুক্ত নথি (${BanglaUtils.toBanglaDigits(allStudentDocuments.size)})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                    }
                }

                // TAB 1: EXTRACTED STRUCTURED DATA
                if (activeTab == "extracted") {
                    item {
                        if (isOcrProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Text("স্মার্ট OCR ও AI ভিশন দিয়ে বাংলা ও ইংরেজি তথ্য পড়া হচ্ছে...", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else if (extractedData != null) {
                            val data = extractedData!!
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Detected Doc Type & Source Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                    Text(
                                                        text = data.documentTypeDetected,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = if (data.extractionSource.contains("Gemini")) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "উৎস: ${data.extractionSource}",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (data.extractionSource.contains("Gemini")) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }

                                        // Quick Copy, AI Re-run & Export Actions
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (processedBitmap != null) {
                                                IconButton(
                                                    onClick = { runGeminiAiExtraction(processedBitmap!!) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI ভিশন পুনঃবিশ্লেষণ", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    val clip = ClipData.newPlainText("Extracted Data", data.rawText)
                                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                                    Toast.makeText(context, "টেক্সট কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = "কপি", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }

                                            if (processedBitmap != null) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.exportBitmapsToPdfInDownloads(
                                                            bitmaps = listOf(processedBitmap!!),
                                                            fileName = "Document_${data.nameBn.ifBlank { data.nameEn }.ifBlank { "Scan" }}_${System.currentTimeMillis()}"
                                                        ) { success, path ->
                                                            if (success) {
                                                                Toast.makeText(context, "PDF সংরক্ষিত: $path", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "PDF সংরক্ষণ ব্যর্থ: $path", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Filled.Download, contentDescription = "ডাউনলোড PDF", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Extracted Items Grid
                                    ExtractedDataFieldRow("নাম (বাংলা)", data.nameBn.ifBlank { "সনাক্ত হয়নি" })
                                    if (data.nameEn.isNotBlank()) {
                                        ExtractedDataFieldRow("নাম (ইংরেজি)", data.nameEn)
                                    }
                                    ExtractedDataFieldRow("পিতার নাম", data.fatherName.ifBlank { "সনাক্ত হয়নি" })
                                    if (data.motherName.isNotBlank()) {
                                        ExtractedDataFieldRow("মাতার নাম", data.motherName)
                                    }
                                    if (data.spouseName.isNotBlank()) {
                                        ExtractedDataFieldRow("স্বামী / স্ত্রী", data.spouseName)
                                    }
                                    if (data.birthRegNumber.isNotBlank()) {
                                        ExtractedDataFieldRow("জন্ম নিবন্ধন নম্বর (১৭ ডিজিট)", data.birthRegNumber)
                                    }
                                    if (data.nidNumber.isNotBlank()) {
                                        ExtractedDataFieldRow("জাতীয় পরিচয়পত্র নম্বর (NID)", data.nidNumber)
                                    }
                                    ExtractedDataFieldRow("জন্ম তারিখ", data.birthDate.ifBlank { "সনাক্ত হয়নি" })
                                    if (data.bloodGroup.isNotBlank()) {
                                        ExtractedDataFieldRow("রক্তের গ্রুপ", data.bloodGroup)
                                    }
                                    if (data.placeOfBirth.isNotBlank()) {
                                        ExtractedDataFieldRow("জন্মস্থান", data.placeOfBirth)
                                    }
                                    ExtractedDataFieldRow("শ্রেণি ও রোল", "${data.studentClass}, রোল: ${BanglaUtils.toBanglaDigits(data.rollNumber?.toString() ?: "১")}")
                                    ExtractedDataFieldRow("লিঙ্গ", data.gender)
                                    if (data.mobileNumber.isNotBlank()) {
                                        ExtractedDataFieldRow("মোবাইল নম্বর", data.mobileNumber)
                                    }
                                    if (data.village.isNotBlank() || data.address.isNotBlank()) {
                                        ExtractedDataFieldRow("ঠিকানা / গ্রাম", data.address.ifBlank { data.village })
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Action Buttons: Save New OR Link Existing Student
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showStudentImportDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .height(44.dp)
                                                .testTag("btn_import_extracted_student")
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("নতুন শিক্ষার্থী সংরক্ষণ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        FilledTonalButton(
                                            onClick = { showLinkExistingStudentDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .height(44.dp)
                                                .testTag("btn_link_existing_student")
                                        ) {
                                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("বিদ্যমানে লিঙ্ক করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: FILTER & CROP STUDIO
                if (activeTab == "editor") {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ক্যামস্ক্যানার পোস্ট-প্রসেসিং ইঞ্জিন",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isEnhancing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }

                                // Filter Selection Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    EnhancementMode.values().forEach { mode ->
                                        val isSelected = currentFilter == mode
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                currentFilter = mode
                                                if (currentBitmap != null) {
                                                    coroutineScope.launch {
                                                        isEnhancing = true
                                                        try {
                                                            processedBitmap = imageEnhancementUseCase.execute(
                                                                currentBitmap!!,
                                                                mode,
                                                                rotationAngle
                                                            )
                                                        } finally {
                                                            isEnhancing = false
                                                        }
                                                    }
                                                }
                                            },
                                            label = { Text(mode.titleBn, fontSize = 11.sp) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null
                                        )
                                    }
                                }

                                // Rotation, Straighten/Deskew, and Action Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            rotationAngle = (rotationAngle + 90f) % 360f
                                            if (currentBitmap != null) {
                                                coroutineScope.launch {
                                                    isEnhancing = true
                                                    try {
                                                        processedBitmap = imageEnhancementUseCase.execute(
                                                            currentBitmap!!,
                                                            currentFilter,
                                                            rotationAngle
                                                        )
                                                    } finally {
                                                        isEnhancing = false
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("ঘোরান 90°", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (currentBitmap != null) {
                                                coroutineScope.launch {
                                                    isEnhancing = true
                                                    try {
                                                        val straightened = documentEdgeDetectionUseCase.straightenOrDeskew(currentBitmap!!)
                                                        currentBitmap = straightened
                                                        processedBitmap = imageEnhancementUseCase.execute(
                                                            straightened,
                                                            currentFilter,
                                                            rotationAngle
                                                        )
                                                        Toast.makeText(context, "ডকুমেন্ট সোজা করা সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "সোজা করতে সমস্যা: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isEnhancing = false
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("সোজা করুন", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (processedBitmap != null) {
                                                coroutineScope.launch {
                                                    val res = viewModel.ocrUseCase.recognizeText(processedBitmap!!, selectedOcrLanguage)
                                                    rawOcrText = res.recognizedText
                                                    ocrConfidence = res.meanConfidence
                                                    extractedData = DocScannerOcrHelper.extractStudentInformation(res.recognizedText)
                                                    Toast.makeText(context, "ডকুমেন্ট ফিল্টার সংরক্ষিত ও OCR আপডেট সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                                    activeTab = "extracted"
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("প্রয়োগ ও OCR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Filtered Image Canvas Preview
                                if (processedBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(280.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.05f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = processedBitmap!!.asImageBitmap(),
                                            contentDescription = "Filtered Document",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: RAW OCR TEXT VIEW
                if (activeTab == "raw_ocr") {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Tesseract OCR সনাক্তকৃত টেক্সট",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${rawOcrText.length} অক্ষর সনাক্ত | ভাষা: ${selectedOcrLanguage.displayNameBn}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                val clip = ClipData.newPlainText("Raw OCR Text", rawOcrText)
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                                Toast.makeText(context, "টেক্সট কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "কপি", modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, rawOcrText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "টেক্সট শেয়ার করুন"))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = "শেয়ার", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 300.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = rawOcrText.ifBlank { "কোনো টেক্সট পাওয়া যায়নি।" },
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty Placeholder State
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.DocumentScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            Text(
                                text = "কোনো ডকুমেন্ট স্ক্যান করা হয়নি",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "জন্ম নিবন্ধন সনদ, ভর্তি ফরম বা যেকোনো নথি ক্যামস্ক্যানারের মতো নিখুঁতভাবে স্ক্যান ও বাংলা Tesseract OCR দিয়ে স্বয়ংক্রিয় ডাটাবেসে যুক্ত করতে উপরের বাটনগুলোতে চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // TAB 4: ARCHIVE & ATTACHED DOCUMENTS LIST (Always Accessible)
            if (activeTab == "archive" || (scannedPageUris.isEmpty() && allStudentDocuments.isNotEmpty())) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "সংযুক্ত সকল নথিপত্র (${BanglaUtils.toBanglaDigits(allStudentDocuments.size)}টি)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = archiveSearchQuery,
                            onValueChange = { archiveSearchQuery = it },
                            placeholder = { Text("নথির নাম বা শিক্ষার্থীর নাম খুঁজুন...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = if (archiveSearchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { archiveSearchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "মুছুন", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        val filteredDocs = remember(allStudentDocuments, archiveSearchQuery, allStudents) {
                            allStudentDocuments.filter { doc ->
                                if (archiveSearchQuery.isBlank()) true
                                else {
                                    val matchedStudent = allStudents.find { it.id == doc.studentId }
                                    doc.title.contains(archiveSearchQuery, ignoreCase = true) ||
                                            doc.documentType.contains(archiveSearchQuery, ignoreCase = true) ||
                                            doc.extractedText.contains(archiveSearchQuery, ignoreCase = true) ||
                                            (matchedStudent?.name?.contains(archiveSearchQuery, ignoreCase = true) == true)
                                }
                            }
                        }

                        if (filteredDocs.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (archiveSearchQuery.isBlank()) "কোনো নথি সংরক্ষিত নেই" else "খুঁজে পাওয়া যায়নি",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredDocs.forEach { doc ->
                                    val student = allStudents.find { it.id == doc.studentId }
                                    DocumentArchiveCard(
                                        doc = doc,
                                        student = student,
                                        onView = { viewingDocumentDetail = doc },
                                        onShare = {
                                            val uri = Uri.parse(doc.fileUri)
                                            viewModel.shareDocument(uri, doc.title, doc.fileType)
                                        },
                                        onDownload = {
                                            val uri = Uri.parse(doc.fileUri)
                                            val fileName = "${doc.title}_${doc.id.take(6)}"
                                            viewModel.exportDocumentToDownloads(uri, fileName, doc.fileType) { success, path ->
                                                if (success) {
                                                    Toast.makeText(context, "সংরক্ষিত: $path", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "ডাউনলোড ব্যর্থ: $path", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onDelete = {
                                            viewModel.deleteStudentDocument(doc)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Direct Student Import Dialog (Create New Student & Attach Scanned Document)
    if (showStudentImportDialog && extractedData != null) {
        val data = extractedData!!
        var tempName by remember { mutableStateOf(data.nameBn.ifBlank { data.nameEn }) }
        var tempFather by remember { mutableStateOf(data.fatherName.ifBlank { data.spouseName }) }
        var tempMother by remember { mutableStateOf(data.motherName) }
        var tempBirthReg by remember { mutableStateOf(data.birthRegNumber.ifBlank { data.nidNumber }) }
        var tempBirthDate by remember { mutableStateOf(data.birthDate) }
        var tempClass by remember { mutableStateOf(data.studentClass.ifBlank { "১ম শ্রেণি" }) }
        var tempRoll by remember { mutableStateOf(data.rollNumber?.toString() ?: "1") }
        var tempMobile by remember { mutableStateOf(data.mobileNumber) }
        var tempGender by remember { mutableStateOf(data.gender) }
        var tempAddress by remember { mutableStateOf(data.address.ifBlank { data.village }) }
        var docTitle by remember { mutableStateOf(data.documentTypeDetected) }

        AlertDialog(
            onDismissRequest = { showStudentImportDialog = false },
            icon = {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "নতুন শিক্ষার্থী সংরক্ষণ ও ডকুমেন্ট সংযুক্তি",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OCR সনাক্তকৃত তথ্য পর্যালোচনা করুন। সংরক্ষণ করলে স্বয়ংক্রিয়ভাবে শিক্ষার্থীর প্রোফাইল এবং স্ক্যানকৃত নথি উভয়ই ডাটাবেসে যুক্ত হবে:",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("শিক্ষার্থীর নাম *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = tempClass,
                            onValueChange = { tempClass = it },
                            label = { Text("শ্রেণি") },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tempRoll,
                            onValueChange = { tempRoll = it },
                            label = { Text("রোল") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = tempFather,
                        onValueChange = { tempFather = it },
                        label = { Text("পিতার নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tempMother,
                        onValueChange = { tempMother = it },
                        label = { Text("মাতার নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tempBirthReg,
                        onValueChange = { tempBirthReg = it },
                        label = { Text("জন্ম নিবন্ধন নম্বর (১৭ ডিজিট)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tempBirthDate,
                        onValueChange = { tempBirthDate = it },
                        label = { Text("জন্ম তারিখ (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tempMobile,
                        onValueChange = { tempMobile = it },
                        label = { Text("মোবাইল নম্বর") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tempAddress,
                        onValueChange = { tempAddress = it },
                        label = { Text("ঠিকানা / গ্রাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("সংযুক্ত নথির শিরোনাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isBlank()) {
                            Toast.makeText(context, "অনুগ্রহ করে শিক্ষার্থীর নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            val studentId = "STU-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}"
                            val rollInt = tempRoll.toIntOrNull() ?: 1
                            val newStudent = StudentEntity(
                                id = studentId,
                                name = tempName.trim(),
                                studentClass = tempClass.trim(),
                                rollNumber = rollInt,
                                fatherName = tempFather.trim(),
                                motherName = tempMother.trim(),
                                birthRegNumber = tempBirthReg.trim(),
                                birthDate = tempBirthDate.trim(),
                                mobile = tempMobile.trim(),
                                parentContact = tempMobile.trim(),
                                village = tempAddress.trim(),
                                address = tempAddress.trim(),
                                gender = tempGender,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            viewModel.insertStudent(newStudent)

                            // Attach scanned document to new student
                            if (processedBitmap != null) {
                                viewModel.saveStudentDocument(
                                    studentId = studentId,
                                    title = docTitle.ifBlank { "জন্ম নিবন্ধন / ভর্তি ফরম" },
                                    documentType = data.documentTypeDetected,
                                    bitmap = processedBitmap!!,
                                    extractedText = rawOcrText,
                                    notes = "স্বয়ংক্রিয় OCR স্ক্যান থেকে সংযোজিত"
                                )
                            }

                            showStudentImportDialog = false
                            Toast.makeText(context, "${tempName} এর তথ্য ও ডকুমেন্ট সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStudentImportDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Link Scanned Document to Existing Student Dialog
    if (showLinkExistingStudentDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
        var docTitle by remember { mutableStateOf(extractedData?.documentTypeDetected ?: "সংযুক্ত নথি") }
        var docType by remember { mutableStateOf("জন্ম নিবন্ধন সনদ") }
        var notes by remember { mutableStateOf("") }

        val docTypes = listOf("জন্ম নিবন্ধন সনদ", "ভর্তি ফরম", "প্রত্যয়ন পত্র", "মার্কশিট", "পাসপোর্ট ছবি", "অন্যান্য")

        val filteredExistingStudents = remember(allStudents, searchQuery) {
            if (searchQuery.isBlank()) allStudents.take(20)
            else allStudents.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.studentClass.contains(searchQuery, ignoreCase = true) ||
                        it.rollNumber.toString().contains(searchQuery) ||
                        it.id.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showLinkExistingStudentDialog = false },
            icon = {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "বিদ্যমান শিক্ষার্থীর সাথে নথি লিঙ্ক করুন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "যে শিক্ষার্থীর প্রোফাইলে এই স্ক্যানকৃত নথি সংযুক্ত করতে চান তাকে নির্বাচন করুন:",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("শিক্ষার্থীর নাম/রোল/শ্রেণি খুঁজুন...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Student selection list
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(filteredExistingStudents) { st ->
                                val isSelected = selectedStudent?.id == st.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { selectedStudent = st }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(st.name, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text("${st.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(st.rollNumber)} | পিতা: ${st.fatherName}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("নথির শিরোনাম *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Document Type Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        docTypes.forEach { type ->
                            FilterChip(
                                selected = docType == type,
                                onClick = { docType = type; if (docTitle.isBlank() || docTypes.contains(docTitle)) docTitle = type },
                                label = { Text(type, fontSize = 10.5.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedStudent == null) {
                            Toast.makeText(context, "অনুগ্রহ করে শিক্ষার্থী নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (processedBitmap == null && currentBitmap == null) {
                            Toast.makeText(context, "কোনো স্ক্যানকৃত ছবি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val bmpToSave = processedBitmap ?: currentBitmap!!
                        viewModel.saveStudentDocument(
                            studentId = selectedStudent!!.id,
                            title = docTitle.ifBlank { docType },
                            documentType = docType,
                            bitmap = bmpToSave,
                            extractedText = rawOcrText,
                            notes = notes
                        ) {
                            showLinkExistingStudentDialog = false
                            Toast.makeText(context, "${selectedStudent!!.name} এর প্রোফাইলে নথি সফলভাবে সংযুক্ত হয়েছে!", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = selectedStudent != null
                ) {
                    Text("সংযুক্ত করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkExistingStudentDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Viewing Document Detail Modal
    if (viewingDocumentDetail != null) {
        val doc = viewingDocumentDetail!!
        val student = allStudents.find { it.id == doc.studentId }
        AlertDialog(
            onDismissRequest = { viewingDocumentDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (student != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("শিক্ষার্থী: ${student.name}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("শ্রেণি: ${student.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 11.sp)
                            }
                        }
                    }

                    if (doc.fileUri.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            AsyncImage(
                                model = Uri.parse(doc.fileUri),
                                contentDescription = doc.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (doc.extractedText.isNotBlank()) {
                        Text("OCR এক্সট্রাক্ট টেক্সট:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = doc.extractedText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        val uri = Uri.parse(doc.fileUri)
                        val fileName = "${doc.title}_${doc.id.take(6)}"
                        viewModel.exportDocumentToDownloads(uri, fileName, doc.fileType) { success, path ->
                            if (success) Toast.makeText(context, "সংরক্ষিত: $path", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = {
                        val uri = Uri.parse(doc.fileUri)
                        viewModel.shareDocument(uri, doc.title, doc.fileType)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary)
                    }

                    TextButton(onClick = { viewingDocumentDetail = null }) {
                        Text("বন্ধ করুন")
                    }
                }
            }
        )
    }
}

@Composable
private fun DocumentArchiveCard(
    doc: StudentDocumentEntity,
    student: StudentEntity?,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Thumbnail preview
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(52.dp)
            ) {
                if (doc.fileUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(doc.fileUri),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (student != null) {
                    Text(
                        text = "${student.name} (${student.studentClass}, রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)})",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "সংযুক্ত: ${doc.documentType}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "স্ক্যান তারিখ: ${doc.scanDate}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun ExtractedDataFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.End
        )
    }
}
