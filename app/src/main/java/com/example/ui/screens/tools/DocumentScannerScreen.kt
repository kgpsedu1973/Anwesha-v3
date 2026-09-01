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
import com.example.util.DocScannerBitmapHelper
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

    // Dialog States
    var showLinkExistingStudentDialog by remember { mutableStateOf(false) }
    var viewingDocumentDetail by remember { mutableStateOf<StudentDocumentEntity?>(null) }

    // Archive search query
    var archiveSearchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        if (showFullScreenZoom) {
            showFullScreenZoom = false
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
                val rawBmp = DocScannerBitmapHelper.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
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
                                val cacheUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                                safeCachedUris.add(cacheUri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (safeCachedUris.isNotEmpty()) {
                        scannedPageUris = safeCachedUris
                        selectedPageIndex = 0
                        scannedPdfUri = null
                        processCurrentPage(safeCachedUris[0], shouldStraighten = false)
                        Toast.makeText(context, "${safeCachedUris.size}টি ছবি গ্যালারি থেকে ইম্পোর্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "গ্যালারি ছবি লোড ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isEnhancing = false
                }
            }
        }
    }

    fun launchDirectCamera() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFile = File(context.cacheDir, "camera_doc_$timeStamp.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempCameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "ক্যামেরা চালু করা সম্ভব হয়নি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                            text = "ডকুমেন্ট স্ক্যানার",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "স্ক্যান, এডিট, ফিল্টার ও পিডিএফ মেকার",
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
            // TOP SCANNER ACTION TOOLBAR
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

                // SCANNED IMAGE PREVIEW CARD
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
                                    .height(260.dp)
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

                            // FILTER CHIPS ROW
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

                            // EXPORT & ACTION BUTTONS: Save as Image, Save as PDF, Share, Link Student
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                        .height(36.dp),
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
                                        .height(36.dp),
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
                                        .height(36.dp),
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
                                        .weight(1.1f)
                                        .height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("নথি যুক্ত", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
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
                                style = MaterialTheme.typography.titleMedium,
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
                                extractedText = "",
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
}

/**
 * High-performance full-screen pinch-to-zoom interactive document viewer
 */
@Composable
fun FullScreenImageZoomDialog(
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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.95f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interactive Zoomable Image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.7f, 5f)
                                if (scale > 1f) {
                                    val maxOffset = 1000f * (scale - 1f)
                                    val newOffset = offset + pan
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxOffset, maxOffset),
                                        y = newOffset.y.coerceIn(-maxOffset, maxOffset)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Full Screen Zoom",
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

                // Top Floating Toolbar (Close, Reset Zoom, Scale Label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
                    }

                    if (scale != 1f || offset != Offset.Zero) {
                        FilledTonalButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("রিসেট (${(scale * 100).toInt()}%)", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                // Bottom Floating Action Bar (Save Image, Save PDF, Share)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSaveImage, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = "Save Image", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onSavePdf, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Save PDF", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color(0xFF80D8FF), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact item card for viewing and managing student documents in the local archive
 */
@Composable
fun CompactDocumentArchiveCard(
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
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
            // Thumbnail preview
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.05f),
                modifier = Modifier.size(40.dp)
            ) {
                AsyncImage(
                    model = Uri.parse(doc.fileUri),
                    contentDescription = doc.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                        text = "${student.name} • ${student.studentClass}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = doc.documentType,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = "ডাউনলোড", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "শেয়ার", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "মুছুন", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
