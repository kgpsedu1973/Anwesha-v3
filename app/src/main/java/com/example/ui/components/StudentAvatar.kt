package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.InputStream

object PhotoDecoderUtils {
    fun decodePhoto(photoUri: String?, context: android.content.Context? = null): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return try {
            when {
                photoUri.startsWith("data:image") -> {
                    val base64Data = photoUri.substringAfter("base64,")
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
                photoUri.startsWith("content://") && context != null -> {
                    val uri = Uri.parse(photoUri)
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    inputStream?.use { BitmapFactory.decodeStream(it) }
                }
                photoUri.startsWith("file://") || photoUri.startsWith("/") -> {
                    val path = if (photoUri.startsWith("file://")) photoUri.removePrefix("file://") else photoUri
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                else -> {
                    // Try decoding as raw base64 string
                    try {
                        val decodedBytes = Base64.decode(photoUri, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun StudentAvatar(
    photoUri: String?,
    name: String,
    gender: String? = null,
    size: Dp = 46.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bitmap = remember(photoUri) {
        PhotoDecoderUtils.decodePhoto(photoUri, context)
    }

    val isBoy = gender == "ছাত্র" || gender == "Boy" || gender == "Male"
    val avatarBg = if (isBoy) Color(0xFFBBDEFB) else Color(0xFFF8BBD0)
    val avatarFg = if (isBoy) Color(0xFF0D47A1) else Color(0xFF880E4F)
    val initial = name.trim().take(1).ifBlank { "শ" }

    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(avatarBg)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$name-এর ছবি",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
                color = avatarFg
            )
        }
    }
}
