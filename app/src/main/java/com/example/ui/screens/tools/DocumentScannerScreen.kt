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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.entity.StudentDocumentEntity
import com.example.data.local.entity.StudentEntity
import com.example.domain.usecase.DocumentEdgeDetectionUseCase
import com.example.domain.usecase.EnhancementMode
import com.example.domain.usecase.ImageEnhancementUseCase
import com.example.util.BanglaUtils
import com.example.util.DocScannerOcrHelper
import com.example.util.DocumentOcrFormatter
import com.example.util.SpatialOcrEngine
import com.example.viewmodel.MainViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class OcrEngineOption(
    val id: String,
    val titleBn: String,
    val shortName: String,
    val subtitleBn: String,
    val badgeText: String
) {
    DRIVE_OCR(
        id = "drive",
        titleBn = "Drive OCR (ক্লাউড)",
        shortName = "ড্রাইভ OCR",
        subtitleBn = "গুগল ড্রাইভ হাই-প্রিসিশন বাংলা ও ইংরেজি OCR",
        badgeText = "ক্লাউড"
    ),
    ML_KIT_OCR(
        id = "ml_kit",
        titleBn = "ML Kit OCR (অন-ডিভাইস)",
        shortName = "ML Kit OCR",
        subtitleBn = "গুগল ML Kit v2 বাংলা ও ইংরেজি দেবনাগরী দ্রুত OCR",
        badgeText = "স্মার্ট"
    ),
    OFFLINE_OCR(
        id = "offline",
        titleBn = "Offline OCR (অফলাইন)",
        shortName = "অফলাইন OCR",
        subtitleBn = "১০০% অফলাইন স্পেশিয়াল লেআউট ও ল্যাটিন OCR",
        badgeText = "অফলাইন"
    )
}

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

    // Scanned Pages State
    var scannedPageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var scannedPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Loaded Bitmap & Filter states for current page
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentFilter by remember { mutableStateOf(EnhancementMode.ORIGINAL) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isEnhancing by remember { mutableStateOf(false) }

    // Temporary camera capture Uri state
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Full-screen zoom modal state
    var showFullScreenZoom by remember { mutableStateOf(false) }

    // OCR & Extracted Data States
    val isOcrProcessing by viewModel.isOcrProcessing.collectAsState()
    val ocrProgressMessage by viewModel.ocrProgressMessage.collectAsState()
    var selectedOcrEngine by remember { mutableStateOf(OcrEngineOption.ML_KIT_OCR) }
    var ocrExtractedText by remember { mutableStateOf("") }
    var spatialAnalysisResult by remember { mutableStateOf<SpatialOcrEngine.SpatialAnalysisResult?>(null) }
    var parsedStudentInfo by remember { mutableStateOf<com.example.util.GoogleDriveOcrHelper.ParsedStudentInfo?>(null) }
    var activeOcrTab by remember { mutableIntStateOf(0) } // 0 = সাজানো তথ্য, 1 = বের করা টেক্সট

    // Dialog States
    var showNewStudentFromOcrDialog by remember { mutableStateOf(false) }
    var showLinkExistingStudentDialog by remember { mutableStateOf(false) }
    var viewingDocumentDetail by remember { mutableStateOf<StudentDocumentEntity?>(null) }

    // Archive search query
    var archiveSearchQuery by remember { mutableStateOf("") }
    var activeMainSection by remember { mutableStateOf("scanner") } // "scanner", "archive"

    BackHandler(enabled = true) {
        if (showFullScreenZoom) {
            showFullScreenZoom = false
        } else if (showNewStudentFromOcrDialog) {
            showNewStudentFromOcrDialog = false
        } else if (showLinkExistingStudentDialog || viewingDocumentDetail != null) {
            showLinkExistingStudentDialog = false
            viewingDocumentDetail = null
        } else {
            onNavigateBack()
        }
    }

    // Function to reload bitmap, apply filter/straighten
    fun processCurrentPage(uri: Uri, shouldStraighten: Boolean = false) {
        coroutineScope.launch {
            isEnhancing = true
            try {
                val rawBmp = DocScannerOcrHelper.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
                val bmp = if (shouldStraighten) {
                    documentEdgeDetectionUseCase.straightenOrDeskew(rawBmp)
                } else {
                    rawBmp
                }

                currentBitmap = bmp
                rotationAngle = 0f
                currentFilter = EnhancementMode.ORIGINAL
                processedBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "ডকুমেন্ট লোড ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isEnhancing = false
            }
        }
    }

    // Google ML Kit Document Scanner Activity Result Launcher
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
                    activeMainSection = "scanner"
                    processCurrentPage(pages[0], shouldStraighten = false)
                    Toast.makeText(context, "${pages.size}টি পৃষ্ঠা স্ক্যান সম্পন্ন!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Direct Camera Photo Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            val capturedUri = tempCameraImageUri!!
            scannedPageUris = listOf(capturedUri)
            selectedPageIndex = 0
            scannedPdfUri = null
            activeMainSection = "scanner"
            processCurrentPage(capturedUri, shouldStraighten = false)
            Toast.makeText(context, "ছবি ধারণ সম্পন্ন!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            coroutineScope.launch {
                isEnhancing = true
                try {
                    val safeCachedUris = mutableListOf<Uri>()
                    withContext(Dispatchers.IO) {
                        uris.forEachIndexed { idx, originalUri ->
                            try {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val cacheFile = File(context.cacheDir, "imported_doc_${timeStamp}_$idx.jpg")
                                context.contentResolver.openInputStream(originalUri)?.use { input ->
                                    FileOutputStream(cacheFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                                safeCachedUris.add(fileUri)
                            } catch (_: Exception) {
                                safeCachedUris.add(originalUri)
                            }
                        }
                    }

                    if (safeCachedUris.isNotEmpty()) {
                        scannedPageUris = safeCachedUris
                        selectedPageIndex = 0
                        scannedPdfUri = null
                        activeMainSection = "scanner"
                        processCurrentPage(safeCachedUris[0], shouldStraighten = false)
                        Toast.makeText(context, "${safeCachedUris.size}টি ডকুমেন্ট লোড হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "লোড ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isEnhancing = false
                }
            }
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

    // Helper to perform OCR using one of the 3 supported engines: Drive OCR, ML Kit OCR, Offline OCR
    fun triggerExtractText(engine: OcrEngineOption = selectedOcrEngine) {
        val bmp = processedBitmap ?: currentBitmap
        if (bmp != null) {
            when (engine) {
                OcrEngineOption.DRIVE_OCR -> {
                    viewModel.performGoogleDriveOcr(bmp) { success, text, parsedStudent, spatialResult ->
                        if (success) {
                            ocrExtractedText = text
                            spatialAnalysisResult = spatialResult
                            parsedStudentInfo = parsedStudent ?: DocumentOcrFormatter.formatOcrText(text).studentInfo
                            activeOcrTab = 0 // Show structured data first
                            Toast.makeText(context, "Drive OCR সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                OcrEngineOption.ML_KIT_OCR -> {
                    viewModel.performLocalSpatialOcr(
                        bitmap = bmp,
                        scriptMode = SpatialOcrEngine.OcrScriptMode.DEVANAGARI_BILINGUAL
                    ) { success, result, msg ->
                        if (success && result != null) {
                            spatialAnalysisResult = result
                            ocrExtractedText = result.rawText
                            parsedStudentInfo = result.formattedResult.studentInfo
                            activeOcrTab = 0 // Show structured data first
                            Toast.makeText(context, "ML Kit OCR সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                OcrEngineOption.OFFLINE_OCR -> {
                    viewModel.performLocalSpatialOcr(
                        bitmap = bmp,
                        scriptMode = SpatialOcrEngine.OcrScriptMode.LATIN_STANDARD
                    ) { success, result, msg ->
                        if (success && result != null) {
                            spatialAnalysisResult = result
                            ocrExtractedText = result.rawText
                            parsedStudentInfo = result.formattedResult.studentInfo
                            activeOcrTab = 0 // Show structured data first
                            Toast.makeText(context, "Offline OCR সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, "অনুগ্রহ করে প্রথমে ডকুমেন্ট স্ক্যান বা লোড করুন", Toast.LENGTH_SHORT).show()
        }
    }

    // Formatted document calculation
    val formattedDocResult = remember(ocrExtractedText, spatialAnalysisResult) {
        spatialAnalysisResult?.formattedResult
            ?: DocumentOcrFormatter.formatOcrText(ocrExtractedText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ডকুমেন্ট ও টেক্সট স্ক্যানার",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "স্ক্যান, এডিট, টেক্সট রিকগনিশন ও পিডিএফ",
                            fontSize = 11.sp,
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
                    val activeBmp = processedBitmap ?: currentBitmap
                    if (activeBmp != null) {
                        // Quick Zoom Icon in TopBar
                        IconButton(onClick = { showFullScreenZoom = true }) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "জুম প্রিভিউ", tint = MaterialTheme.colorScheme.primary)
                        }
                        // Quick Share Icon in TopBar
                        IconButton(onClick = {
                            viewModel.shareBitmapImage(activeBmp, "Scanned_Doc_${System.currentTimeMillis()}")
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "শেয়ার করুন", tint = MaterialTheme.colorScheme.onSurface)
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // COMPACT TOP SCANNER ACTION TOOLBAR
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { launchMlKitDocumentScanner() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp)
                                .testTag("btn_launch_scanner"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("স্ক্যানার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { launchDirectCamera() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("btn_launch_camera"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ক্যামেরা", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("btn_launch_gallery"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("গ্যালারি", fontSize = 11.5.sp)
                        }
                    }
                }
            }

            // SCANNED DOCUMENT SECTION
            val activeBmp = processedBitmap ?: currentBitmap
            if (activeBmp != null) {
                // Multi-page thumbnail selector
                if (scannedPageUris.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "পৃষ্ঠা ${BanglaUtils.toBanglaDigits((selectedPageIndex + 1).toString())}/${BanglaUtils.toBanglaDigits(scannedPageUris.size.toString())}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(scannedPageUris) { idx, uri ->
                                    val isSelected = idx == selectedPageIndex
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 54.dp)
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
                }

                // COMPACT SCANNED IMAGE PREVIEW CARD (Interactive Pinch/Tap to Zoom)
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Interactive image container with Tap to Zoom hint
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.04f))
                                    .clickable { showFullScreenZoom = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = activeBmp.asImageBitmap(),
                                    contentDescription = "Scanned Document Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Subtle Zoom hint overlay
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("ফুল-স্ক্রিন জুম", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isEnhancing) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                }
                            }

                            // COMPACT FILTER CHIPS ROW
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                        label = { Text(mode.titleBn, fontSize = 10.5.sp) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                        } else null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                }

                                // Quick Rotate Button
                                IconButton(
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
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90", modifier = Modifier.size(17.dp))
                                }

                                // Straighten Button
                                IconButton(
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
                                                    Toast.makeText(context, "সোজা করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "সোজা ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isEnhancing = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.CropFree, contentDescription = "Straighten", modifier = Modifier.size(17.dp))
                                }
                            }

                            // COMPACT EXPORT & ACTION BUTTONS: Save as Image, Save as PDF, Share, Link Student
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Save as Image
                                OutlinedButton(
                                    onClick = {
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        viewModel.exportBitmapAsImageToDownloads(
                                            bitmap = activeBmp,
                                            fileName = "Document_Scan_$timeStamp"
                                        ) { success, msg ->
                                            Toast.makeText(context, if (success) "ছবি সংরক্ষিত হয়েছে!" else msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("ছবি সংরক্ষণ", fontSize = 10.5.sp, maxLines = 1)
                                }

                                // Save as PDF
                                OutlinedButton(
                                    onClick = {
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        viewModel.exportBitmapsToPdfInDownloads(
                                            bitmaps = listOf(activeBmp),
                                            fileName = "Document_Scan_$timeStamp"
                                        ) { success, msg ->
                                            Toast.makeText(context, if (success) "PDF সংরক্ষিত হয়েছে!" else msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("PDF সংরক্ষণ", fontSize = 10.5.sp, maxLines = 1)
                                }

                                // Share
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareBitmapImage(activeBmp, "Scanned_Document_${System.currentTimeMillis()}")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("শেয়ার", fontSize = 10.5.sp, maxLines = 1)
                                }

                                // Link to Student
                                Button(
                                    onClick = { showLinkExistingStudentDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("নথি যুক্ত", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            // 3 OCR ENGINE OPTIONS SELECTOR (Drive OCR, ML Kit OCR, Offline OCR)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "OCR ইঞ্জিন নির্বাচন করুন:",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = selectedOcrEngine.badgeText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OcrEngineOption.values().forEach { engine ->
                                            val isSelected = selectedOcrEngine == engine
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    if (isSelected) 1.5.dp else 0.5.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(32.dp)
                                                    .clickable {
                                                        selectedOcrEngine = engine
                                                    }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = engine.shortName,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // EXTRACT TEXT (OCR) BUTTON
                            Button(
                                onClick = { triggerExtractText(selectedOcrEngine) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("btn_extract_text_ocr")
                            ) {
                                if (isOcrProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ocrProgressMessage ?: "টেক্সট বের করা হচ্ছে...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${selectedOcrEngine.shortName} দিয়ে টেক্সট বের করুন",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // OCR RESULTS: ONLY Extracted Text ("বের করা টেক্সট") & Formatted Data ("সাজানো তথ্য")
                if (ocrExtractedText.isNotBlank() || formattedDocResult.fields.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 2 Compact Tabs: সাজানো তথ্য and বের করা টেক্সট
                                TabRow(
                                    selectedTabIndex = activeOcrTab,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                ) {
                                    Tab(
                                        selected = activeOcrTab == 0,
                                        onClick = { activeOcrTab = 0 },
                                        text = { Text("📋 সাজানো তথ্য", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                                    )
                                    Tab(
                                        selected = activeOcrTab == 1,
                                        onClick = { activeOcrTab = 1 },
                                        text = { Text("📝 বের করা টেক্সট", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }

                                // TAB 0: সাজানো তথ্য (Formatted / Structured Data)
                                if (activeOcrTab == 0) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Detected Doc Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${formattedDocResult.category.icon} ${formattedDocResult.documentTitleBn}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = "${formattedDocResult.fields.size}টি ফিল্ড",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        // Fields List
                                        if (formattedDocResult.fields.isEmpty()) {
                                            Text(
                                                text = "কোনো নির্দিষ্ট ফিল্ড সাজানো যায়নি। 'বের করা টেক্সট' ট্যাব দেখুন।",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        } else {
                                            formattedDocResult.fields.forEach { field ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = field.labelBn,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                text = field.value,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (field.isImportant) FontWeight.Bold else FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                val clip = ClipData.newPlainText(field.labelBn, field.value)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, "${field.labelBn} কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.ContentCopy,
                                                                contentDescription = "কপি",
                                                                modifier = Modifier.size(13.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Action Row: Copy All Formatted Summary & Add New Student
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Formatted Info", formattedDocResult.formattedSummary)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "সম্পূর্ণ সাজানো তথ্য কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.CopyAll, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("সব কপি", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    parsedStudentInfo = formattedDocResult.studentInfo
                                                    showNewStudentFromOcrDialog = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("নতুন শিক্ষার্থী এন্ট্রি", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // TAB 1: বের করা টেক্সট (Extracted Text)
                                if (activeOcrTab == 1) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = ocrExtractedText,
                                            onValueChange = {
                                                ocrExtractedText = it
                                                parsedStudentInfo = com.example.util.GoogleDriveOcrHelper.parseStudentFromOcrText(it)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 120.dp, max = 220.dp)
                                                .testTag("input_ocr_extracted_text"),
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("Extracted Text", ocrExtractedText)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "টেক্সট কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("কপি", fontSize = 10.5.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val sendIntent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            putExtra(Intent.EXTRA_TEXT, ocrExtractedText)
                                                            type = "text/plain"
                                                        }
                                                        val shareIntent = Intent.createChooser(sendIntent, "টেক্সট শেয়ার করুন")
                                                        context.startActivity(shareIntent)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("শেয়ার", fontSize = 10.5.sp)
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    parsedStudentInfo = com.example.util.GoogleDriveOcrHelper.parseStudentFromOcrText(ocrExtractedText)
                                                    Toast.makeText(context, "পুনরায় সাজানো সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("পুনরায় সাজান", fontSize = 10.5.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // EMPTY PLACEHOLDER
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.DocumentScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Text(
                                text = "কোনো ডকুমেন্ট স্ক্যান করা হয়নি",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "জন্ম নিবন্ধন সনদ, ভর্তি ফরম বা যেকোনো নথি স্ক্যান করতে উপরের স্ক্যানার, ক্যামেরা বা গ্যালারি বাটন চাপুন।",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // ATTACHED / ARCHIVED DOCUMENTS SECTION
            if (allStudentDocuments.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "সংরক্ষিত নথিপত্র (${BanglaUtils.toBanglaDigits(allStudentDocuments.size)}টি)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = archiveSearchQuery,
                                onValueChange = { archiveSearchQuery = it },
                                placeholder = { Text("নথি বা শিক্ষার্থীর নাম খুঁজুন...", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (archiveSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { archiveSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Filled.Clear, contentDescription = "মুছুন", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else null,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                            )

                            val filteredDocs = remember(allStudentDocuments, archiveSearchQuery, allStudents) {
                                allStudentDocuments.filter { doc ->
                                    if (archiveSearchQuery.isBlank()) true
                                    else {
                                        val matchedStudent = allStudents.find { it.id == doc.studentId }
                                        doc.title.contains(archiveSearchQuery, ignoreCase = true) ||
                                                doc.documentType.contains(archiveSearchQuery, ignoreCase = true) ||
                                                (matchedStudent?.name?.contains(archiveSearchQuery, ignoreCase = true) == true)
                                    }
                                }
                            }

                            if (filteredDocs.isEmpty()) {
                                Text(
                                    text = "খুঁজে পাওয়া যায়নি",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredDocs.forEach { doc ->
                                        val student = allStudents.find { it.id == doc.studentId }
                                        CompactDocumentArchiveCard(
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
                                                    Toast.makeText(context, if (success) "সংরক্ষিত: $path" else "ডাউনলোড ব্যর্থ", Toast.LENGTH_SHORT).show()
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
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // FULL-SCREEN PINCH-TO-ZOOM IMAGE VIEWER DIALOG
    val activeBmpForZoom = processedBitmap ?: currentBitmap
    if (showFullScreenZoom && activeBmpForZoom != null) {
        FullScreenImageZoomDialog(
            bitmap = activeBmpForZoom,
            onDismiss = { showFullScreenZoom = false },
            onSaveImage = {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                viewModel.exportBitmapAsImageToDownloads(
                    bitmap = activeBmpForZoom,
                    fileName = "Document_Scan_$timeStamp"
                ) { success, msg ->
                    Toast.makeText(context, if (success) "ছবি সংরক্ষিত হয়েছে!" else msg, Toast.LENGTH_SHORT).show()
                }
            },
            onSavePdf = {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                viewModel.exportBitmapsToPdfInDownloads(
                    bitmaps = listOf(activeBmpForZoom),
                    fileName = "Document_Scan_$timeStamp"
                ) { success, msg ->
                    Toast.makeText(context, if (success) "PDF সংরক্ষিত হয়েছে!" else msg, Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                viewModel.shareBitmapImage(activeBmpForZoom, "Scanned_Doc_${System.currentTimeMillis()}")
            }
        )
    }

    // LINK DOCUMENT TO EXISTING STUDENT DIALOG
    if (showLinkExistingStudentDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
        var docTitle by remember { mutableStateOf("জন্ম নিবন্ধন সনদ") }
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
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = "শিক্ষার্থীর প্রোফাইলে নথি যুক্ত",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("শিক্ষার্থীর নাম/রোল খুঁজুন...", fontSize = 11.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(3.dp)) {
                            items(filteredExistingStudents) { st ->
                                val isSelected = selectedStudent?.id == st.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { selectedStudent = st }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(st.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${st.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(st.rollNumber)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("নথির শিরোনাম *", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

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
                                label = { Text(type, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("মন্তব্য (ঐচ্ছিক)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bmpToSave = processedBitmap ?: currentBitmap
                        if (selectedStudent != null && bmpToSave != null) {
                            viewModel.saveStudentDocument(
                                studentId = selectedStudent!!.id,
                                title = docTitle.ifBlank { docType },
                                documentType = docType,
                                bitmap = bmpToSave,
                                extractedText = ocrExtractedText,
                                notes = notes
                            ) {
                                showLinkExistingStudentDialog = false
                                Toast.makeText(context, "${selectedStudent!!.name} এর প্রোফাইলে নথি যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = selectedStudent != null
                ) {
                    Text("যুক্ত করুন", fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkExistingStudentDialog = false }) {
                    Text("বাতিল", fontSize = 11.5.sp)
                }
            }
        )
    }

    // VIEWING DOCUMENT DETAIL DIALOG
    if (viewingDocumentDetail != null) {
        val doc = viewingDocumentDetail!!
        val student = allStudents.find { it.id == doc.studentId }
        AlertDialog(
            onDismissRequest = { viewingDocumentDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (student != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("শিক্ষার্থী: ${student.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("শ্রেণি: ${student.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 10.5.sp)
                            }
                        }
                    }

                    if (doc.fileUri.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            AsyncImage(
                                model = Uri.parse(doc.fileUri),
                                contentDescription = doc.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (doc.notes.isNotBlank()) {
                        Text("মন্তব্য: ${doc.notes}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = {
                        val uri = Uri.parse(doc.fileUri)
                        viewModel.shareDocument(uri, doc.title, doc.fileType)
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    }

                    TextButton(onClick = { viewingDocumentDetail = null }) {
                        Text("বন্ধ করুন", fontSize = 11.5.sp)
                    }
                }
            }
        )
    }

    // NEW STUDENT FROM OCR PRE-FILLED DIALOG
    if (showNewStudentFromOcrDialog) {
        val initialInfo = parsedStudentInfo ?: com.example.util.GoogleDriveOcrHelper.parseStudentFromOcrText(ocrExtractedText)
        var studentName by remember { mutableStateOf(initialInfo.name) }
        var fatherName by remember { mutableStateOf(initialInfo.fatherName) }
        var motherName by remember { mutableStateOf(initialInfo.motherName) }
        var birthRegNo by remember { mutableStateOf(initialInfo.birthRegNumber) }
        var birthDate by remember { mutableStateOf(initialInfo.birthDate) }
        var studentClass by remember { mutableStateOf(initialInfo.studentClass) }
        var rollNoStr by remember { mutableStateOf(initialInfo.rollNumber.toString()) }
        var mobile by remember { mutableStateOf(initialInfo.mobile) }
        var village by remember { mutableStateOf(initialInfo.village) }
        var address by remember { mutableStateOf(initialInfo.address) }
        var gender by remember { mutableStateOf(initialInfo.gender) }
        var alsoAttachScannedDoc by remember { mutableStateOf(true) }

        val classes = listOf("প্রাক-প্রাথমিক ৪+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
        val genders = listOf("ছাত্র", "ছাত্রী")

        AlertDialog(
            onDismissRequest = { showNewStudentFromOcrDialog = false },
            icon = {
                Icon(
                    Icons.Filled.PersonAddAlt1,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = "OCR থেকে নতুন শিক্ষার্থী তৈরি",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "OCR তথ্য যাচাই করে নতুন শিক্ষার্থী সংরক্ষণ করুন:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Swap helpers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                val temp = studentName
                                studentName = fatherName
                                fatherName = temp
                            },
                            label = { Text("🔄 নাম ⇄ পিতার নাম", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                val temp = fatherName
                                fatherName = motherName
                                motherName = temp
                            },
                            label = { Text("🔄 পিতা ⇄ মাতার নাম", fontSize = 10.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("শিক্ষার্থীর নাম *", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = studentClass,
                            onValueChange = { studentClass = it },
                            label = { Text("শ্রেণি", fontSize = 11.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rollNoStr,
                            onValueChange = { rollNoStr = it },
                            label = { Text("রোল", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    // Class Quick Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        classes.forEach { cls ->
                            FilterChip(
                                selected = studentClass == cls,
                                onClick = { studentClass = cls },
                                label = { Text(cls, fontSize = 9.5.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("লিঙ্গ:", fontSize = 11.sp)
                        genders.forEach { g ->
                            FilterChip(
                                selected = gender == g,
                                onClick = { gender = g },
                                label = { Text(g, fontSize = 10.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = birthRegNo,
                        onValueChange = { birthRegNo = it },
                        label = { Text("জন্ম নিবন্ধন নম্বর", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("জন্ম তারিখ", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("পিতার নাম", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = motherName,
                        onValueChange = { motherName = it },
                        label = { Text("মাতার নাম", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("মোবাইল নম্বর", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("গ্রাম", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ঠিকানা", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { alsoAttachScannedDoc = !alsoAttachScannedDoc }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = alsoAttachScannedDoc,
                            onCheckedChange = { alsoAttachScannedDoc = it },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "এই স্ক্যানকৃত ছবি শিক্ষার্থীর প্রোফাইলে সংরক্ষণ করুন",
                            fontSize = 10.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (studentName.isBlank()) {
                            Toast.makeText(context, "অনুগ্রহ করে শিক্ষার্থীর নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val parsedRoll = BanglaUtils.toEnglishDigits(rollNoStr).toIntOrNull() ?: 1
                        val newStudentId = "STU-${System.currentTimeMillis()}"

                        val newStudent = StudentEntity(
                            id = newStudentId,
                            name = studentName.trim(),
                            studentClass = studentClass.trim(),
                            rollNumber = parsedRoll,
                            fatherName = fatherName.trim(),
                            motherName = motherName.trim(),
                            birthRegNumber = birthRegNo.trim(),
                            birthDate = birthDate.trim(),
                            mobile = mobile.trim(),
                            parentContact = mobile.trim(),
                            village = village.trim(),
                            address = address.trim(),
                            gender = gender,
                            status = "Current"
                        )

                        viewModel.insertStudent(newStudent)

                        val bmpToSave = processedBitmap ?: currentBitmap
                        if (alsoAttachScannedDoc && bmpToSave != null) {
                            viewModel.saveStudentDocument(
                                studentId = newStudentId,
                                title = "জন্ম সনদ / ভর্তি নথি",
                                documentType = "জন্ম নিবন্ধন সনদ",
                                bitmap = bmpToSave,
                                extractedText = ocrExtractedText,
                                notes = "OCR দ্বারা স্ক্যান ও তৈরি"
                            ) { }
                        }

                        Toast.makeText(context, "'${newStudent.name}' শিক্ষার্থী হিসেবে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                        showNewStudentFromOcrDialog = false
                    }
                ) {
                    Text("সংরক্ষণ করুন", fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewStudentFromOcrDialog = false }) {
                    Text("বাতিল", fontSize = 11.5.sp)
                }
            }
        )
    }
}

/**
 * Full-screen zoomable image dialog with pinch-to-zoom and pan support
 */
@Composable
private fun FullScreenImageZoomDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveImage: () -> Unit,
    onSavePdf: () -> Unit,
    onShare: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Zoomable and Pannable Image Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 5f)
                            if (scale > 1f) {
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full Screen Zoomable Document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // Top App Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                Text(
                    text = "ডকুমেন্ট প্রিভিউ (জুম করুন)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSaveImage) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "Save Image", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSavePdf) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Save PDF", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Bottom Reset Zoom indicator if zoomed
            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("রিসেট জুম (ডাবল ট্যাপ)", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Compact document archive card item
 */
@Composable
private fun CompactDocumentArchiveCard(
    doc: StudentDocumentEntity,
    student: StudentEntity?,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
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
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (student != null) {
                    Text(
                        text = "${student.name} (${student.studentClass}, রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)})",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = doc.documentType,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
