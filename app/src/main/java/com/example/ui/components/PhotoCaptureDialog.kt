package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun PhotoCaptureDialog(
    currentPhotoUri: String?,
    onDismiss: () -> Unit,
    onPhotoSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember {
        mutableStateOf<Bitmap?>(loadInitialBitmap(context, currentPhotoUri))
    }
    var isProcessing by remember { mutableStateOf(false) }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { rawBitmap ->
        if (rawBitmap != null) {
            isProcessing = true
            val processed = processPassportPhoto(rawBitmap)
            previewBitmap = processed
            isProcessing = false
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                isProcessing = true
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val decoded = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (decoded != null) {
                    val processed = processPassportPhoto(decoded)
                    previewBitmap = processed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("শিক্ষার্থীর ছবি যুক্তকরণ (Passport Photo)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "পাসপোর্ট সাইজ (৩:৪ অনুপাত) ও স্বাভাবিক আলোতে ছবি তুলুন। অ্যাপ স্বয়ংক্রিয়ভাবে ছবিটি সেন্টারিং ও সাইজ করবে।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Photo Preview Area with Passport Frame
                Box(
                    modifier = Modifier
                        .size(150.dp, 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8ECEF))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Passport Photo Preview",
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
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ছবি নির্বাচন করুন", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    if (isProcessing) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }

                // Action Buttons (Camera & Gallery)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher.launch() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_capture_camera"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ক্যামেরা", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_pick_gallery")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("গ্যালারি", fontSize = 12.sp)
                    }
                }

                if (previewBitmap != null) {
                    Text(
                        text = "স্বয়ংক্রিয় ৩:৪ অনুপাত ও উজ্জ্বলতা অ্যাডজাস্ট করা হয়েছে।",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (previewBitmap != null) {
                        val base64 = bitmapToBase64(previewBitmap!!)
                        onPhotoSelected(base64)
                    }
                    onDismiss()
                },
                enabled = previewBitmap != null,
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

private fun processPassportPhoto(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height

    // Calculate 3:4 target crop dimensions
    val targetAspect = 3f / 4f
    val currentAspect = width.toFloat() / height.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    val startX: Int
    val startY: Int

    if (currentAspect > targetAspect) {
        // Image is wider than 3:4 -> crop horizontally
        cropHeight = height
        cropWidth = (height * targetAspect).toInt()
        startX = (width - cropWidth) / 2
        startY = 0
    } else {
        // Image is taller than 3:4 -> crop vertically, keeping upper-middle (face region)
        cropWidth = width
        cropHeight = (width / targetAspect).toInt()
        startX = 0
        startY = ((height - cropHeight) * 0.35f).toInt().coerceAtLeast(0) // slight top bias for passport face
    }

    val cropped = Bitmap.createBitmap(
        source,
        startX.coerceIn(0, width - 1),
        startY.coerceIn(0, height - 1),
        cropWidth.coerceAtMost(width - startX),
        cropHeight.coerceAtMost(height - startY)
    )

    // Scale to standard passport resolution 300x400
    val scaled = Bitmap.createScaledBitmap(cropped, 300, 400, true)

    // Gentle contrast/brightness enhancement
    val output = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val colorMatrix = ColorMatrix().apply {
        // Slight brightness (+10) and subtle contrast boost (1.05)
        set(floatArrayOf(
            1.05f, 0f, 0f, 0f, 10f,
            0f, 1.05f, 0f, 0f, 10f,
            0f, 0f, 1.05f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(scaled, 0f, 0f, paint)

    return output
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val byteArray = outputStream.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

private fun loadInitialBitmap(context: android.content.Context, uriOrBase64: String?): Bitmap? {
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
