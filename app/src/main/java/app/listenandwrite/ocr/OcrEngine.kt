package app.listenandwrite.ocr

import android.content.Context
import android.net.Uri
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.ParsedWord
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
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                        val raw = if (lines.isNotEmpty()) lines.joinToString("\n") else result.text
                        if (cont.isActive) cont.resume(raw)
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
