package app.listenandwrite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

fun createCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    if (file.exists()) file.delete()
    file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun openTtsSettings(context: Context) {
    val intents = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )
    intents.forEach { intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Try the next settings screen.
        }
    }
}
