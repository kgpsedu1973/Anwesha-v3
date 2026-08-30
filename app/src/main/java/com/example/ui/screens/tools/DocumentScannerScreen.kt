package com.example.ui.screens.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.entity.StudentEntity
import com.example.util.BanglaUtils
import com.example.util.DocScanFilterMode
import com.example.util.DocScannerOcrHelper
import com.example.util.ExtractedStudentData
import com.example.viewmodel.MainViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State for Scanned Pages & Output
    var scannedPageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var scannedPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Loaded Bitmap & Filter states for current page
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentFilter by remember { mutableStateOf(DocScanFilterMode.ORIGINAL) }
    var rotationAngle by remember { mutableStateOf(0f) }

    // OCR & Extracted Data state
    var isOcrProcessing by remember { mutableStateOf(false) }
    var extractedData by remember { mutableStateOf<ExtractedStudentData?>(null) }
    var rawOcrText by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("extracted") } // "extracted", "editor", "raw_ocr"

    // Temporary camera capture Uri state
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Dialog State for Direct Student Import
    var showStudentImportDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {
        if (showStudentImportDialog) {
            showStudentImportDialog = false
        } else {
            onNavigateBack()
        }
    }

    // Function to reload bitmap and re-run filter/OCR safely with memory protection
    fun processCurrentPage(uri: Uri) {
        coroutineScope.launch {
            isOcrProcessing = true
            try {
                val bmp = DocScannerOcrHelper.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
                currentBitmap = bmp
                rotationAngle = 0f
                currentFilter = DocScanFilterMode.ORIGINAL

                val filtered = DocScannerOcrHelper.applyFilter(bmp, DocScanFilterMode.ORIGINAL, 0f)
                processedBitmap = filtered

                // Perform OCR
                val visionText = DocScannerOcrHelper.recognizeTextFromBitmap(filtered)
                rawOcrText = visionText.text
                val parsed = DocScannerOcrHelper.extractStudentInformation(visionText.text)
                extractedData = parsed
                statusMessage = "ডকুমেন্ট বিশ্লেষণ সম্পন্ন"
            } catch (e: Exception) {
                e.printStackTrace()
                statusMessage = "প্রসেসিং ত্রুটি: ${e.localizedMessage}"
                Toast.makeText(context, "ইমেজ পড়তে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isOcrProcessing = false
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
                    processCurrentPage(pages[0])
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
            processCurrentPage(capturedUri)
            Toast.makeText(context, "ছবি ধারণ সম্পন্ন!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Multiple/Single Picker Fallback
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            scannedPageUris = uris
            selectedPageIndex = 0
            scannedPdfUri = null
            processCurrentPage(uris[0])
        }
    }

    // Helper to start direct camera capture
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

    // Trigger Google ML Kit Document Scanner
    fun launchMlKitDocumentScanner() {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(15)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
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
                        // Fallback to camera or gallery if ML Kit Play services not downloaded yet
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
                            text = "Google ML Kit স্ক্যানার, ক্রপ, ফিল্টার ও ডাটা এক্সট্রাক্টর",
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
                                    text = "ক্যামস্ক্যানার ক্যামেরা ও এআই স্ক্যান",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "স্বয়ংক্রিয় বর্ডার ক্রপ, ডেস্কেউ, শ্যাডো রিমুভাল ও OCR",
                                    fontSize = 11.5.sp,
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

                // Section Tabs (Extracted Data, Filter Studio, Raw OCR Text)
                item {
                    TabRow(
                        selectedTabIndex = when (activeTab) {
                            "extracted" -> 0
                            "editor" -> 1
                            else -> 2
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == "extracted",
                            onClick = { activeTab = "extracted" },
                            text = { Text("এক্সট্রাক্ট তথ্য", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = activeTab == "editor",
                            onClick = { activeTab = "editor" },
                            text = { Text("ফিল্টার ও ক্রপ", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = activeTab == "raw_ocr",
                            onClick = { activeTab = "raw_ocr" },
                            text = { Text("মূল OCR টেক্সট", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                                    Text("OCR ও তথ্য এক্সট্রাকশন চলছে...", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
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
                                    // Detected Doc Type Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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

                                        // Quick Copy All Button
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
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Extracted Items Grid
                                    ExtractedDataFieldRow("শিক্ষার্থীর নাম (বাংলা)", data.nameBn.ifBlank { "সনাক্ত হয়নি" })
                                    if (data.nameEn.isNotBlank()) {
                                        ExtractedDataFieldRow("শিক্ষার্থীর নাম (ইংরেজি)", data.nameEn)
                                    }
                                    ExtractedDataFieldRow("পিতার নাম", data.fatherName.ifBlank { "সনাক্ত হয়নি" })
                                    ExtractedDataFieldRow("মাতার নাম", data.motherName.ifBlank { "সনাক্ত হয়নি" })
                                    ExtractedDataFieldRow("জন্ম নিবন্ধন নম্বর (১৭ ডিজিট)", data.birthRegNumber.ifBlank { "সনাক্ত হয়নি" })
                                    ExtractedDataFieldRow("জন্ম তারিখ", data.birthDate.ifBlank { "সনাক্ত হয়নি" })
                                    ExtractedDataFieldRow("শ্রেণি ও রোল", "${data.studentClass}, রোল: ${BanglaUtils.toBanglaDigits(data.rollNumber?.toString() ?: "১")}")
                                    ExtractedDataFieldRow("লিঙ্গ", data.gender)
                                    if (data.mobileNumber.isNotBlank()) {
                                        ExtractedDataFieldRow("মোবাইল নম্বর", data.mobileNumber)
                                    }
                                    if (data.village.isNotBlank() || data.address.isNotBlank()) {
                                        ExtractedDataFieldRow("ঠিকানা / গ্রাম", data.address.ifBlank { data.village })
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Action to Import into Database
                                    Button(
                                        onClick = { showStudentImportDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("btn_import_extracted_student")
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("শিক্ষার্থী হিসেবে ডাটাবেসে সংরক্ষণ করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                Text(
                                    text = "ক্যামস্ক্যানার ফিল্টার প্রিসেট",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Filter Selection Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DocScanFilterMode.values().forEach { mode ->
                                        val isSelected = currentFilter == mode
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                currentFilter = mode
                                                if (currentBitmap != null) {
                                                    coroutineScope.launch {
                                                        processedBitmap = DocScannerOcrHelper.applyFilter(
                                                            currentBitmap!!,
                                                            mode,
                                                            rotationAngle
                                                        )
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

                                // Rotation and Action Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            rotationAngle = (rotationAngle + 90f) % 360f
                                            if (currentBitmap != null) {
                                                coroutineScope.launch {
                                                    processedBitmap = DocScannerOcrHelper.applyFilter(
                                                        currentBitmap!!,
                                                        currentFilter,
                                                        rotationAngle
                                                    )
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ঘোরান (90°)", fontSize = 11.5.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (processedBitmap != null) {
                                                coroutineScope.launch {
                                                    val savedUri = DocScannerOcrHelper.saveBitmapToCache(context, processedBitmap!!)
                                                    // Re-run OCR on filtered bitmap
                                                    val visionText = DocScannerOcrHelper.recognizeTextFromBitmap(processedBitmap!!)
                                                    rawOcrText = visionText.text
                                                    extractedData = DocScannerOcrHelper.extractStudentInformation(visionText.text)
                                                    Toast.makeText(context, "ফিল্টার প্রয়োগ ও OCR আপডেট সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                                    activeTab = "extracted"
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("প্রয়োগ ও OCR রিফ্রেশ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                                    Text(
                                        text = "সনাক্তকৃত মোট টেক্সট (${rawOcrText.length} অক্ষর)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

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
                                text = "জন্ম নিবন্ধন সনদ, ভর্তি ফরম বা যেকোনো নথি ক্যামস্ক্যানারের মতো নিখুঁতভাবে স্ক্যান ও স্বয়ংক্রিয় ডাটা এন্ট্রি করতে উপরের 'স্ক্যান করুন' বাটনে চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Direct Student Import Dialog
    if (showStudentImportDialog && extractedData != null) {
        val data = extractedData!!
        var tempName by remember { mutableStateOf(data.nameBn.ifBlank { data.nameEn }) }
        var tempFather by remember { mutableStateOf(data.fatherName) }
        var tempMother by remember { mutableStateOf(data.motherName) }
        var tempBirthReg by remember { mutableStateOf(data.birthRegNumber) }
        var tempBirthDate by remember { mutableStateOf(data.birthDate) }
        var tempClass by remember { mutableStateOf(data.studentClass.ifBlank { "১ম শ্রেণি" }) }
        var tempRoll by remember { mutableStateOf(data.rollNumber?.toString() ?: "1") }
        var tempMobile by remember { mutableStateOf(data.mobileNumber) }
        var tempGender by remember { mutableStateOf(data.gender) }
        var tempAddress by remember { mutableStateOf(data.address.ifBlank { data.village }) }

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
                    text = "শিক্ষার্থী তথ্য যাচাই ও সংযোজন",
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
                        text = "OCR থেকে সনাক্তকৃত তথ্য পর্যালোচনা করুন এবং প্রয়োজন হলে সংশোধন করুন:",
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isBlank()) {
                            Toast.makeText(context, "অনুগ্রহ করে শিক্ষার্থীর নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val rollInt = tempRoll.toIntOrNull() ?: 1
                        val newStudent = StudentEntity(
                            id = "STU-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
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
                            photoUri = scannedPageUris.firstOrNull()?.toString(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        viewModel.insertStudent(newStudent)
                        showStudentImportDialog = false
                        Toast.makeText(context, "${tempName} এর তথ্য ডাটাবেসে সফলভাবে যুক্ত হয়েছে!", Toast.LENGTH_LONG).show()
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
