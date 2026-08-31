package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.SpatialOcrEngine

@Composable
fun BoundingBoxOverlayView(
    bitmap: Bitmap,
    spatialResult: SpatialOcrEngine.SpatialAnalysisResult,
    modifier: Modifier = Modifier,
    onBoxSelected: (SpatialOcrEngine.OcrLine?) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedLine by remember { mutableStateOf<SpatialOcrEngine.OcrLine?>(null) }
    var showOnlyLabelsAndValues by remember { mutableStateOf(false) }

    val imgWidth = spatialResult.imageWidth.toFloat()
    val imgHeight = spatialResult.imageHeight.toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Legend
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF1976D2), RoundedCornerShape(2.dp))
                )
                Text("লেবেল (Label)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF2E7D32), RoundedCornerShape(2.dp))
                )
                Text("মান (Value)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            FilterChip(
                selected = showOnlyLabelsAndValues,
                onClick = { showOnlyLabelsAndValues = !showOnlyLabelsAndValues },
                label = { Text("শুধু লেবেল-মান", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(12.dp)) }
            )
        }

        // Canvas Area with interactive tap detection
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.04f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth.value
            val containerHeight = maxHeight.value

            // Compute aspect ratio scaling
            val bmpAspect = imgWidth / imgHeight
            val containerAspect = containerWidth / containerHeight

            val scale: Float
            val renderWidth: Float
            val renderHeight: Float
            val offsetX: Float
            val offsetY: Float

            if (bmpAspect > containerAspect) {
                renderWidth = containerWidth
                renderHeight = containerWidth / bmpAspect
                scale = renderWidth / imgWidth
                offsetX = 0f
                offsetY = (containerHeight - renderHeight) / 2f
            } else {
                renderHeight = containerHeight
                renderWidth = containerHeight * bmpAspect
                scale = renderHeight / imgHeight
                offsetX = (containerWidth - renderWidth) / 2f
                offsetY = 0f
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(spatialResult) {
                        detectTapGestures { tapOffset ->
                            val localX = (tapOffset.x - offsetX) / scale
                            val localY = (tapOffset.y - offsetY) / scale

                            val tappedLine = spatialResult.lines.firstOrNull { line ->
                                localX >= line.box.left && localX <= line.box.right &&
                                localY >= line.box.top && localY <= line.box.bottom
                            }

                            selectedLine = tappedLine
                            onBoxSelected(tappedLine)
                        }
                    }
            ) {
                // Draw background bitmap scaled
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(renderWidth.toInt(), renderHeight.toInt())
                )

                // Draw bounding boxes for all detected lines
                for (line in spatialResult.lines) {
                    val box = line.box
                    val isSelected = selectedLine == line
                    val isLabel = line.role == SpatialOcrEngine.BoxRole.LABEL
                    val isValue = line.role == SpatialOcrEngine.BoxRole.VALUE

                    if (showOnlyLabelsAndValues && !isLabel && !isValue && !isSelected) {
                        continue
                    }

                    val boxColor = when {
                        isSelected -> Color(0xFFFF9800) // Amber for selected
                        isLabel -> Color(0xFF1976D2) // Blue for labels
                        isValue -> Color(0xFF2E7D32) // Green for values
                        else -> Color(0xFF757575) // Gray for general text
                    }

                    val boxLeft = offsetX + (box.left * scale)
                    val boxTop = offsetY + (box.top * scale)
                    val boxW = box.width * scale
                    val boxH = box.height * scale

                    // Draw semi-transparent fill
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.35f else 0.18f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH)
                    )

                    // Draw solid border
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        style = Stroke(width = if (isSelected) 3.5f else 1.8f)
                    )
                }
            }
        }

        // Inspector card for selected box
        if (selectedLine != null) {
            val line = selectedLine!!
            val matchedPair = spatialResult.labelValuePairs.find { it.valueBox == line.box || it.labelBox == line.box }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (line.role == SpatialOcrEngine.BoxRole.LABEL) "🏷️ লেবেল" else if (line.role == SpatialOcrEngine.BoxRole.VALUE) "📝 মান (Value)" else "📄 টেক্সট",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (matchedPair != null) {
                                Text(
                                    text = "• ${matchedPair.labelNameBn} (${matchedPair.relation.titleBn})",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Text(
                            text = line.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Text(
                            text = "অবস্থান: [X:${line.box.left.toInt()}, Y:${line.box.top.toInt()}, W:${line.box.width.toInt()}, H:${line.box.height.toInt()}]",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Box Text", line.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "টেক্সট কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "কপি", modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            Text(
                text = "💡 বাউন্ডিং বক্সে চাপ দিয়ে নির্দিষ্ট টেক্সট ও পজিশন দেখুন ও কপি করুন।",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
