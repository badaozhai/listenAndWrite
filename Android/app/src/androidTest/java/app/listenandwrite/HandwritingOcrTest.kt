package app.listenandwrite

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.listenandwrite.domain.SeenChar
import app.listenandwrite.domain.gradeDetail
import app.listenandwrite.domain.gradeOne
import app.listenandwrite.ocr.OcrEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandwritingOcrTest {
    @Test
    fun readGeneratedHandwriting() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val expected = mapOf(
            "wrong-yi.png" to "己",
            "messy-wei.png" to "未",
            "right-ji.png" to "己",
        )
        expected.forEach { (name, word) ->
            val dir = File(app.cacheDir, "ocr").apply { mkdirs() }
            val file = File(dir, name)
            assets.open("handwriting/$name").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val lines = OcrEngine.readSheet(app, uri)
            println("OCR file=$name bytes=${file.length()} bitmap=${bitmap?.width}x${bitmap?.height}")
            val chars = lines.flatMap { line ->
                line.chars.ifEmpty { line.text.map { SeenChar(it) } }
            }
            val hit = gradeOne(word, chars, chinese = true)
            val detail = gradeDetail(hit, chinese = true)
            val text = lines.joinToString(" | ") { it.text }
            println("OCR file=$name expected=$word text=$text seen=${hit.seen} detail=$detail")
        }
    }
}
