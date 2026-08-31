package com.example.ui.screens.tools

import android.app.Activity
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
import com.example.domain.usecase.DocumentEdgeDetectionUseCase
import com.example.domain.usecase.EnhancementMode
import com.example.domain.usecase.ImageEnhancementUseCase
import com.example.ui.components.BoundingBoxOverlayView
import com.example.util.BanglaUtils
import com.example.util.DocScannerOcrHelper
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
    var currentFilter by remember { mutableStateOf(EnhancementMode.ORIGINAL) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var isEnhancing by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf("editor") } // "editor", "archive"

    // Temporary camera capture Uri state
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Google Drive & Local Spatial OCR States
    val isOcrProcessing by viewModel.isOcrProcessing.collectAsState()
    val ocrProgressMessage by viewModel.ocrProgressMessage.collectAsState()
    var showOcrResultDialog by remember { mutableStateOf(false) }
    var ocrExtractedText by remember { mutableStateOf("") }
    var spatialAnalysisResult by remember { mutableStateOf<SpatialOcrEngine.SpatialAnalysisResult?>(null) }
    var parsedStudentInfo by remember { mutableStateOf<com.example.util.GoogleDriveOcrHelper.ParsedStudentInfo?>(null) }
    var showNewStudentFromOcrDialog by remember { mutableStateOf(false) }

    // Dialog States
    var showLinkExistingStudentDialog by remember { mutableStateOf(false) }
    var viewingDocumentDetail by remember { mutableStateOf<StudentDocumentEntity?>(null) }

    // Archive search query
    var archiveSearchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        if (showNewStudentFromOcrDialog) {
            showNewStudentFromOcrDialog = false
        } else if (showOcrResultDialog) {
            showOcrResultDialog = false
        } else if (showLinkExistingStudentDialog || viewingDocumentDetail != null) {
            showLinkExistingStudentDialog = false
            viewingDocumentDetail = null
        } else {
            onNavigateBack()
        }
    }

    // Function to reload bitmap, apply edge detection/deskew (if needed), and apply current enhancement
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
                    activeTab = "editor"
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
            activeTab = "editor"
            processCurrentPage(capturedUri, shouldStraighten = false)
            Toast.makeText(context, "ছবি ধারণ সম্পন্ন!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Multiple/Single Picker with safe caching
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
                        activeTab = "editor"
                        processCurrentPage(safeCachedUris[0], shouldStraighten = false)
                        Toast.makeText(context, "${safeCachedUris.size}টি ডকুমেন্ট গ্যালারি থেকে লোড সম্পন্ন!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "গ্যালারি থেকে লোড ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্মার্ট ডকুমেন্ট স্ক্যানার",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "বর্ডার ক্রপ, ইমেজ ফিল্টার, পিডিএফ মেকার ও নথি সংরক্ষণ",
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
                                Text(
                                    text = "ডকুমেন্ট স্ক্যানার ও এডিটর",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ক্যামস্ক্যানারের মতো বর্ডার ক্রপ, ফিল্টার, পিডিএফ মেকার ও শিক্ষার্থী প্রোফাইলে নথি সংরক্ষণ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Action Buttons Row
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
                                        text = "PDF প্রস্তুত",
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

                // Section Tabs (Filter/Crop Studio, Archive)
                item {
                    TabRow(
                        selectedTabIndex = if (activeTab == "editor") 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == "editor",
                            onClick = { activeTab = "editor" },
                            text = { Text("ফিল্টার ও ক্রপ এডিটর", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = activeTab == "archive",
                            onClick = { activeTab = "archive" },
                            text = { Text("সংযুক্ত নথি (${BanglaUtils.toBanglaDigits(allStudentDocuments.size)})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                // TAB 1: FILTER & CROP STUDIO
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
                                        text = "ইমেজ ফিল্টার ও এনহ্যান্সমেন্ট",
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

                                // Rotation, Straighten/Deskew Controls
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
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("সোজা করুন", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (processedBitmap != null) {
                                                viewModel.exportBitmapsToPdfInDownloads(
                                                    bitmaps = listOf(processedBitmap!!),
                                                    fileName = "Document_Scan_${System.currentTimeMillis()}"
                                                ) { success, path ->
                                                    if (success) {
                                                        Toast.makeText(context, "PDF সংরক্ষিত: $path", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "PDF সংরক্ষণ ব্যর্থ: $path", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("PDF সংরক্ষণ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                                    // Save / Link to Student Profile Button
                                    Button(
                                        onClick = { showLinkExistingStudentDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("btn_link_existing_student")
                                    ) {
                                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("শিক্ষার্থীর প্রোফাইলে এই নথি সংযুক্ত করুন", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // On-Device Instant Spatial OCR Button (Bounding Box Aware)
                                    Button(
                                        onClick = {
                                            val bmp = processedBitmap ?: currentBitmap
                                            if (bmp != null) {
                                                viewModel.performLocalSpatialOcr(bmp) { success, result, msg ->
                                                    if (success && result != null) {
                                                        spatialAnalysisResult = result
                                                        ocrExtractedText = result.rawText
                                                        parsedStudentInfo = result.formattedResult.studentInfo
                                                        showOcrResultDialog = true
                                                    } else {
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "কোনো স্ক্যানকৃত ছবি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("btn_local_spatial_ocr")
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("⚡ স্মার্ট OCR স্ক্যান (বাউন্ডিং বক্স ও লেবেল)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Google Drive Cloud OCR Button
                                    OutlinedButton(
                                        onClick = {
                                            val bmp = processedBitmap ?: currentBitmap
                                            if (bmp != null) {
                                                viewModel.performGoogleDriveOcr(bmp) { success, text, parsed, spatialRes ->
                                                    if (success) {
                                                        ocrExtractedText = text
                                                        parsedStudentInfo = parsed
                                                        spatialAnalysisResult = spatialRes
                                                        showOcrResultDialog = true
                                                    } else {
                                                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "কোনো স্ক্যানকৃত ছবি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("btn_google_drive_ocr")
                                    ) {
                                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("☁️ ড্রাইভ OCR + বাউন্ডিং বক্স", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
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
                                text = "জন্ম নিবন্ধন সনদ, ভর্তি ফরম বা যেকোনো নথি ক্যামস্ক্যানারের মতো নিখুঁতভাবে স্ক্যান, ফিল্টার ও পিডিএফ তৈরি করতে উপরের বাটনগুলোতে চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // ARCHIVE & ATTACHED DOCUMENTS LIST (Always Accessible)
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

    // Link Scanned Document to Existing Student Dialog
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
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "শিক্ষার্থীর প্রোফাইলে নথি সংযুক্ত করুন",
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

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("মন্তব্য (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
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
                            extractedText = "",
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

                    if (doc.notes.isNotBlank()) {
                        Text("মন্তব্য: ${doc.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Google Drive OCR Processing Loading Dialog
    if (isOcrProcessing) {
        AlertDialog(
            onDismissRequest = { /* cannot cancel while in progress */ },
            confirmButton = {},
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Google Drive OCR প্রসেসিং",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = ocrProgressMessage ?: "গুগল ড্রাইভে আপলোড ও OCR দ্বারা টেক্সট কনভার্ট হচ্ছে...",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // Google Drive & Local Spatial OCR Result Dialog (Formatted, Spatial Labels & Bounding Box)
    if (showOcrResultDialog) {
        var ocrViewTab by remember { mutableStateOf("spatial") } // "spatial", "formatted", "canvas", "raw"
        var selectedCategoryOverride by remember { mutableStateOf<com.example.util.DocumentOcrFormatter.DocCategory?>(null) }

        val activeBitmap = processedBitmap ?: currentBitmap

        val formattedDocResult = remember(ocrExtractedText, selectedCategoryOverride, spatialAnalysisResult) {
            val baseResult = spatialAnalysisResult?.formattedResult
                ?: com.example.util.DocumentOcrFormatter.formatOcrText(ocrExtractedText)

            if (selectedCategoryOverride != null && selectedCategoryOverride != baseResult.category) {
                // User explicitly selected a different category override
                when (selectedCategoryOverride!!) {
                    com.example.util.DocumentOcrFormatter.DocCategory.BIRTH_CERTIFICATE -> com.example.util.DocumentOcrFormatter.formatOcrText("জন্ম নিবন্ধন সনদ\n$ocrExtractedText")
                    com.example.util.DocumentOcrFormatter.DocCategory.NATIONAL_ID -> com.example.util.DocumentOcrFormatter.formatOcrText("জাতীয় পরিচয়পত্র NID\n$ocrExtractedText")
                    com.example.util.DocumentOcrFormatter.DocCategory.STUDENT_ADMISSION -> com.example.util.DocumentOcrFormatter.formatOcrText("শিক্ষার্থী ভর্তি ফরম প্রত্যয়নপত্র\n$ocrExtractedText")
                    com.example.util.DocumentOcrFormatter.DocCategory.ACADEMIC_MARKSHEET -> com.example.util.DocumentOcrFormatter.formatOcrText("নম্বরপত্র গ্রেডশিট মার্কশিট\n$ocrExtractedText")
                    com.example.util.DocumentOcrFormatter.DocCategory.GENERAL_DOCUMENT -> com.example.util.DocumentOcrFormatter.formatOcrText(ocrExtractedText)
                }
            } else {
                baseResult
            }
        }

        AlertDialog(
            onDismissRequest = { showOcrResultDialog = false },
            icon = {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "স্মার্ট OCR ও বাউন্ডিং বক্স",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val boxCount = spatialAnalysisResult?.lines?.size ?: 0
                            val labelCount = spatialAnalysisResult?.labelValuePairs?.size ?: formattedDocResult.fields.size
                            Text(
                                text = if (boxCount > 0) "${labelCount} লেবেল • ${boxCount} বক্স" else "${formattedDocResult.fields.size}টি ফিল্ড",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Detected Document Type Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(formattedDocResult.category.icon, fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = formattedDocResult.documentTitleBn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = formattedDocResult.documentTitleEn,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Document Category Switcher Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.example.util.DocumentOcrFormatter.DocCategory.values().forEach { cat ->
                            FilterChip(
                                selected = formattedDocResult.category == cat,
                                onClick = { selectedCategoryOverride = cat },
                                label = { Text("${cat.icon} ${cat.titleBn}", fontSize = 10.sp) }
                            )
                        }
                    }

                    // 4-Way Tab Selector: Spatial vs Formatted vs Visual Canvas vs Raw
                    val tabs = listOf(
                        "spatial" to "🏷️ লেবেল-মান",
                        "formatted" to "📋 সাজানো",
                        "canvas" to "📐 বাউন্ডিং বক্স",
                        "raw" to "📝 টেক্সট"
                    )
                    val selectedTabIndex = tabs.indexOfFirst { it.first == ocrViewTab }.coerceAtLeast(0)

                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        tabs.forEach { (tabKey, tabTitle) ->
                            Tab(
                                selected = ocrViewTab == tabKey,
                                onClick = { ocrViewTab = tabKey },
                                text = { Text(tabTitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    when (ocrViewTab) {
                        "spatial" -> {
                            // Tab: Spatial Label-Value Pairs with Relative Positioning
                            val pairs = spatialAnalysisResult?.labelValuePairs ?: emptyList()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (pairs.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("লেবেল-মান জোড়া শনাক্ত করা যায়নি।", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("সাজানো তথ্য বা বাউন্ডিং বক্স ট্যাব দেখুন।", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "📍 লেবেলের অবস্থান অনুযায়ী পাশের মান স্বয়ংক্রিয়ভাবে সাজানো হয়েছে:",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    pairs.forEach { pair ->
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = pair.labelNameBn,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                                        ) {
                                                            Text(
                                                                text = pair.relation.titleBn,
                                                                fontSize = 8.5.sp,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = pair.valueText,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (pair.isImportant) FontWeight.Bold else FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )

                                                    if (pair.valueBox != null) {
                                                        Text(
                                                            text = "বক্স: [X:${pair.valueBox.left.toInt()}, Y:${pair.valueBox.top.toInt()}]",
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText(pair.labelNameBn, pair.valueText)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "${pair.labelNameBn} কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentCopy,
                                                        contentDescription = "কপি",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "canvas" -> {
                            // Tab: Visual Bounding Box Canvas Overlay
                            if (activeBitmap != null && spatialAnalysisResult != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BoundingBoxOverlayView(
                                        bitmap = activeBitmap,
                                        spatialResult = spatialAnalysisResult!!
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("বাউন্ডিং বক্স বিশ্লেষণ পাওয়া যায়নি। স্মার্ট OCR স্ক্যান চালান।", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        "formatted" -> {
                            // Tab: Structured Document Cards
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (formattedDocResult.fields.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                            Text("কোনো নির্দিষ্ট ফিল্ড আলাদা করা যায়নি। মূল টেক্সট ট্যাব দেখুন।", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                } else {
                                    val grouped = formattedDocResult.fields.groupBy { it.category }
                                    grouped.forEach { (catTitle, fieldsInCat) ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = catTitle,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                                fieldsInCat.forEach { field ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = field.labelBn,
                                                                fontSize = 10.5.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                lineHeight = 12.sp
                                                            )
                                                            Text(
                                                                text = field.value,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (field.isImportant) FontWeight.Bold else FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                lineHeight = 15.sp
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText(field.labelBn, field.value)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, "${field.labelBn} কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.ContentCopy,
                                                                contentDescription = "কপি",
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Action Bar: Full Formatted Summary Copy & Quick Auto-Fix
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Formatted Summary", formattedDocResult.formattedSummary)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "সম্পূর্ণ সাজানো তথ্য কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.CopyAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("সব কপি", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val updated = com.example.util.DocumentOcrFormatter.formatOcrText(ocrExtractedText)
                                            parsedStudentInfo = updated.studentInfo
                                            Toast.makeText(context, "✨ স্মার্ট পুনর্বিন্যাস সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("অটো-ফিক্স", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        "raw" -> {
                            // Tab: Raw OCR Text
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
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
                                        .weight(1f)
                                        .testTag("input_ocr_extracted_text"),
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("OCR Raw Text", ocrExtractedText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "মূল OCR টেক্সট কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("মূল টেক্সট কপি", fontSize = 11.sp)
                                    }

                                    TextButton(
                                        onClick = {
                                            parsedStudentInfo = com.example.util.GoogleDriveOcrHelper.parseStudentFromOcrText(ocrExtractedText)
                                            Toast.makeText(context, "পুনরায় সাজানো সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("পুনরায় সাজান", fontSize = 11.sp)
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
                        parsedStudentInfo = formattedDocResult.studentInfo
                        showNewStudentFromOcrDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("btn_create_student_from_ocr")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("নতুন শিক্ষার্থী এন্ট্রি", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrResultDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    // New Student From OCR Pre-filled Dialog
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
                    modifier = Modifier.size(28.dp)
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
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OCR থেকে পাওয়া তথ্য যাচাই ও সংশোধন করে নতুন শিক্ষার্থী ডাটাবেসে সংরক্ষণ করুন:",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Smart Name Swapping & Reordering Shortcuts
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
                                Toast.makeText(context, "🔄 নাম ও পিতার নাম অদলবদল করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🔄 নাম ⇄ পিতার নাম", fontSize = 10.5.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                val temp = fatherName
                                fatherName = motherName
                                motherName = temp
                                Toast.makeText(context, "🔄 পিতা ও মাতার নাম অদলবদল করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🔄 পিতা ⇄ মাতার নাম", fontSize = 10.5.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                val temp = studentName
                                studentName = motherName
                                motherName = temp
                                Toast.makeText(context, "🔄 নাম ও মাতার নাম অদলবদল করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🔄 নাম ⇄ মাতার নাম", fontSize = 10.5.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("শিক্ষার্থীর নাম *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = studentClass,
                            onValueChange = { studentClass = it },
                            label = { Text("শ্রেণি") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rollNoStr,
                            onValueChange = { rollNoStr = it },
                            label = { Text("রোল") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    // Class Quick Select Chips
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
                                label = { Text(cls, fontSize = 10.sp) }
                            )
                        }
                    }

                    // Gender Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("লিঙ্গ:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        genders.forEach { g ->
                            FilterChip(
                                selected = gender == g,
                                onClick = { gender = g },
                                label = { Text(g, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = birthRegNo,
                        onValueChange = { birthRegNo = it },
                        label = { Text("জন্ম নিবন্ধন নম্বর") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("জন্ম তারিখ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("পিতার নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = motherName,
                        onValueChange = { motherName = it },
                        label = { Text("মাতার নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("মোবাইল নম্বর") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("গ্রাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ঠিকানা") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Checkbox to also attach document
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { alsoAttachScannedDoc = !alsoAttachScannedDoc }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = alsoAttachScannedDoc,
                            onCheckedChange = { alsoAttachScannedDoc = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "এই স্ক্যানকৃত ছবি শিক্ষার্থীর প্রোফাইলে নথি হিসেবে সংরক্ষণ করুন",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
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

                        // If user wants to attach document
                        val bmpToSave = processedBitmap ?: currentBitmap
                        if (alsoAttachScannedDoc && bmpToSave != null) {
                            viewModel.saveStudentDocument(
                                studentId = newStudentId,
                                title = "জন্ম সনদ / ভর্তি নথি",
                                documentType = "জন্ম নিবন্ধন সনদ",
                                bitmap = bmpToSave,
                                extractedText = ocrExtractedText,
                                notes = "Google Drive OCR দিয়ে স্ক্যান ও তৈরি"
                            ) {
                                // Saved
                            }
                        }

                        Toast.makeText(context, "'${newStudent.name}' সফলভাবে শিক্ষার্থী হিসেবে যুক্ত হয়েছে!", Toast.LENGTH_LONG).show()
                        showNewStudentFromOcrDialog = false
                        showOcrResultDialog = false
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewStudentFromOcrDialog = false }) {
                    Text("বাতিল")
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
