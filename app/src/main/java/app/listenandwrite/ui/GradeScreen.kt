package app.listenandwrite.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.WordEntity
import app.listenandwrite.data.rememberWrong
import app.listenandwrite.data.undoWrong
import app.listenandwrite.domain.GradeHit
import app.listenandwrite.domain.GradeMark
import app.listenandwrite.domain.InkBox
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.gradeDetail
import app.listenandwrite.domain.gradeSheet
import app.listenandwrite.ocr.OcrEngine
import app.listenandwrite.ocr.PageShot
import app.listenandwrite.ocr.cropInk
import app.listenandwrite.ocr.loadUpright
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class GradeChoice { AUTO, USER, OK }

class GradeViewModel(app: Application) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()
    val words: List<WordEntity> = listenApp.practice.words
    var hits by mutableStateOf<List<GradeHit>>(emptyList())
        private set
    var page by mutableStateOf<PageShot?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private val choice = mutableStateMapOf<Int, GradeChoice>()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    internal fun choiceOf(index: Int): GradeChoice? = choice[index]

    fun grade(uri: Uri) {
        if (words.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            try {
                words.forEachIndexed { index, word ->
                    if (choice[index] == GradeChoice.AUTO || choice[index] == GradeChoice.USER) {
                        dao.undoWrong(word)
                    }
                }
                choice.clear()
                val lines = withContext(Dispatchers.IO) { OcrEngine.readSheet(getApplication(), uri) }
                val shot = withContext(Dispatchers.IO) { loadUpright(getApplication(), uri) }
                page?.bitmap?.recycle()
                page = shot
                val graded = gradeSheet(
                    words.map { word -> word.text to (word.lang != Lang.EN.name) },
                    lines,
                )
                graded.forEachIndexed { index, hit ->
                    if (hit.mark == GradeMark.WRONG) {
                        dao.rememberWrong(words[index])
                        choice[index] = GradeChoice.AUTO
                    }
                }
                hits = graded
                val wrong = graded.count { it.mark == GradeMark.WRONG }
                val stroke = graded.count { it.mark == GradeMark.STROKE }
                _message.emit("明确写错 $wrong 个，已进错题本。笔画没到位 $stroke 个，请看一下。")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.emit("这张照片没有认出字。请拍正、字写大一点再试。")
            } finally {
                busy = false
            }
        }
    }

    fun countWrong(index: Int) {
        val word = words.getOrNull(index) ?: return
        if (choice[index] == GradeChoice.AUTO || choice[index] == GradeChoice.USER) return
        choice[index] = GradeChoice.USER
        viewModelScope.launch { dao.rememberWrong(word) }
    }

    fun accept(index: Int) {
        val word = words.getOrNull(index) ?: return
        val previous = choice[index]
        if (previous != GradeChoice.AUTO && previous != GradeChoice.USER) {
            choice[index] = GradeChoice.OK
            return
        }
        choice[index] = GradeChoice.OK
        viewModelScope.launch { dao.undoWrong(word) }
    }

    override fun onCleared() {
        page?.bitmap?.recycle()
        page = null
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as ListenApp
    val viewModel: GradeViewModel = viewModel()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null) viewModel.grade(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val uri = captureUri
        if (granted && uri != null) takePicture.launch(uri)
        else if (!granted && uri != null) takePicture.launch(uri)
    }
    val pickImage = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.grade(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbar.showSnackbar(it) }
    }
    fun shoot() {
        val uri = createCaptureUri(context)
        captureUri = uri
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) takePicture.launch(uri) else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    if (viewModel.words.isEmpty() || app.practice.words.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("这次没有可批改的词。")
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("拍照批改") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("只看这次听写的词。认成另一个字算明确写错，会进错题本。笔画没写到位的先并列给你看。笔顺看不出来。")
                Text("卷子上最好写上 1、2、3，好和题目对上。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shoot() }, enabled = !viewModel.busy) { Text("拍照") }
                    OutlinedButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                        enabled = !viewModel.busy,
                    ) { Text("相册") }
                }
            }
            if (viewModel.busy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { Text("正在逐字看…") }
            }
            itemsIndexed(viewModel.hits) { index, hit ->
                GradeRow(
                    index = index,
                    hit = hit,
                    chinese = viewModel.words[index].lang != Lang.EN.name,
                    choice = viewModel.choiceOf(index),
                    page = viewModel.page,
                    onWrong = { viewModel.countWrong(index) },
                    onAccept = { viewModel.accept(index) },
                )
            }
        }
    }
}

@Composable
private fun GradeRow(
    index: Int,
    hit: GradeHit,
    chinese: Boolean,
    choice: GradeChoice?,
    page: PageShot?,
    onWrong: () -> Unit,
    onAccept: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hit.expected, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        Text(
            when (choice) {
                GradeChoice.OK -> "算对"
                GradeChoice.AUTO, GradeChoice.USER -> gradeDetail(hit, chinese) + "，已进错题本"
                null -> gradeDetail(hit, chinese)
            },
            color = when (hit.mark) {
                GradeMark.WRONG -> MaterialTheme.colorScheme.error
                GradeMark.STROKE, GradeMark.MISSING -> MaterialTheme.colorScheme.secondary
                GradeMark.RIGHT -> MaterialTheme.colorScheme.primary
            },
        )
        if (hit.seen.isNotBlank() && hit.mark != GradeMark.RIGHT) {
            Text("认成：${hit.seen}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hit.mark == GradeMark.STROKE || hit.mark == GradeMark.WRONG) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InkThumb(page, hit.box)
                Text(hit.expected, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (hit.mark != GradeMark.RIGHT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (choice != GradeChoice.OK) {
                    TextButton(onClick = onAccept) { Text(if (choice == null) "算对" else "认错了，算对") }
                }
                if (choice == null || choice == GradeChoice.OK) {
                    Button(onClick = onWrong) { Text("写错了") }
                }
            }
        }
    }
}

@Composable
private fun InkThumb(page: PageShot?, box: InkBox?) {
    val crop = remember(page, box) {
        if (page == null || box == null) null else cropInk(page, box)?.asImageBitmap()
    }
    if (crop != null) {
        Image(
            crop,
            contentDescription = "写出的字",
            modifier = Modifier.heightIn(max = 96.dp).widthIn(max = 220.dp),
        )
    }
}
