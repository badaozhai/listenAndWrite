package app.listenandwrite.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import app.listenandwrite.domain.InkBox
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.ParsedWord
import app.listenandwrite.domain.SeenChar
import app.listenandwrite.domain.SheetLine
import app.listenandwrite.domain.WordText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrEngine {
    suspend fun recognize(context: Context, uri: Uri, fallback: Lang): List<ParsedWord> {
        return WordText.parseBatch(read(context, uri), fallback)
    }

    suspend fun read(context: Context, uri: Uri): String {
        return readLines(context, uri).joinToString("\n") { it.text }.ifBlank { "" }
    }

    suspend fun readSheet(context: Context, uri: Uri): List<SheetLine> {
        return readLines(context, uri).sortedWith(compareBy({ it.box?.top ?: 0 }, { it.box?.left ?: 0 }))
    }

    private suspend fun readLines(context: Context, uri: Uri): List<SheetLine> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = result.textBlocks.flatMap { block -> block.lines }.map { line ->
                            val symbols = line.elements.flatMap { element ->
                                val fromSymbols = element.symbols.map { symbol ->
                                    val ch = symbol.text.firstOrNull() ?: ' '
                                    SeenChar(ch, symbol.confidence)
                                }
                                fromSymbols.ifEmpty {
                                    element.text.map { SeenChar(it, element.confidence) }
                                }
                            }
                            val rect = line.boundingBox
                            SheetLine(
                                text = line.text,
                                chars = symbols,
                                box = rect?.let { InkBox(it.left, it.top, it.right, it.bottom) },
                            )
                        }
                        val ready = lines.ifEmpty {
                            listOf(SheetLine(text = result.text))
                        }.filter { it.text.isNotBlank() }
                        if (cont.isActive) cont.resume(ready)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }
}

data class PageShot(val bitmap: Bitmap, val scale: Float)

fun loadUpright(context: Context, uri: Uri, maxSide: Int = 1600): PageShot? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
    val upright = if (rotation == 0) {
        raw
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }
    val fullWidth = if (rotation == 90 || rotation == 270) bounds.outHeight else bounds.outWidth
    val scale = upright.width.toFloat() / fullWidth.coerceAtLeast(1)
    return PageShot(upright, scale)
}

fun cropInk(page: PageShot, box: InkBox): Bitmap? {
    val left = (box.left * page.scale).toInt().coerceIn(0, page.bitmap.width - 1)
    val top = (box.top * page.scale).toInt().coerceIn(0, page.bitmap.height - 1)
    val right = (box.right * page.scale).toInt().coerceIn(left + 1, page.bitmap.width)
    val bottom = (box.bottom * page.scale).toInt().coerceIn(top + 1, page.bitmap.height)
    return runCatching {
        Bitmap.createBitmap(page.bitmap, left, top, right - left, bottom - top)
    }.getOrNull()
}
