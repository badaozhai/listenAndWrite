package app.listenandwrite.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.WordEntity
import app.listenandwrite.domain.DRILL_SUBJECT
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.Subject
import app.listenandwrite.domain.englishCueLabel
import app.listenandwrite.domain.slotTitle
import app.listenandwrite.domain.StudyGuide
import app.listenandwrite.domain.WordText
import app.listenandwrite.domain.langLabel
import app.listenandwrite.domain.sortedUnits
import app.listenandwrite.domain.unitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: Long,
    fixedUnit: Int?,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onSession: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as ListenApp
    val viewModel: ListDetailViewModel = viewModel(
        key = "$listId-${fixedUnit ?: "all"}",
        factory = ListDetailViewModelFactory(app, listId, fixedUnit),
    )
    val list by viewModel.list.collectAsStateWithLifecycle()
    val words by viewModel.words.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val groupOffer by viewModel.groupOffer.collectAsStateWithLifecycle()
    val entryUnit by viewModel.entryUnit.collectAsStateWithLifecycle()
    val playUnits by viewModel.playUnits.collectAsStateWithLifecycle()
    val randomOrder by viewModel.randomOrder.collectAsStateWithLifecycle()
    val drillCue by viewModel.drillCue.collectAsStateWithLifecycle()
    var newUnit by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var pasteOpen by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WordEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null) viewModel.recognize(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val uri = captureUri
        if (granted && uri != null) takePicture.launch(uri)
        else if (!granted) {
            // The system camera can still be used on some phones; try once.
            if (uri != null) takePicture.launch(uri)
        }
    }
    val pickImage = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.recognize(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.openReview.collect { onReview() }
    }
    LaunchedEffect(Unit) {
        viewModel.openSession.collect { onSession() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    fun shoot() {
        val uri = createCaptureUri(context)
        captureUri = uri
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) takePicture.launch(uri) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    val chinese = list?.subject == Subject.CHINESE.name
                    Text(if (fixedUnit == null) list?.title ?: "词单" else slotTitle(fixedUnit, chinese))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = if (fixedUnit == null) "删除词单" else "清空本单元")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    when (list?.subject) {
                        Subject.ENGLISH.name ->
                            "英语${englishCueLabel(settings.englishCue)}。报完后按字母留时间：每个字母 ${settings.secondsPerLetter} 秒，最少 ${settings.englishMinSeconds} 秒。"
                        DRILL_SUBJECT ->
                            "默写练习现在是${englishCueLabel(drillCue)}。听中文就写英文。报完后按字母留时间：每个字母 ${settings.secondsPerLetter} 秒，最少 ${settings.englishMinSeconds} 秒。"
                        else ->
                            studyHint(list?.subject) + "写字时间：普通词 ${settings.writingSeconds} 秒，四字成语 ${settings.idiomSeconds} 秒。每个词先读 ${settings.repeats} 遍。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (list?.subject == DRILL_SUBJECT) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnglishCue.entries.forEach { cue ->
                            androidx.compose.material3.FilterChip(
                                selected = drillCue == cue,
                                onClick = { viewModel.setDrillCue(cue) },
                                label = { Text(englishCueLabel(cue)) },
                            )
                        }
                    }
                }
            }
            if (fixedUnit != null) {
                item {
                    Text(
                        "新词记在${slotTitle(fixedUnit, list?.subject == Subject.CHINESE.name)}。这一课里已经有的词不会再存一份。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (fixedUnit == null) {
            item {
                Text("录入到", style = MaterialTheme.typography.titleMedium)
                val entryChoices = sortedUnits(words.map { it.unitName }.toSet() + entryUnit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entryChoices.forEach { unit ->
                        androidx.compose.material3.FilterChip(
                            selected = entryUnit == unit,
                            onClick = { viewModel.setEntryUnit(unit) },
                            label = { Text(unitLabel(unit)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUnit,
                        onValueChange = { newUnit = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("新单元，例如 3") },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            viewModel.setEntryUnit(newUnit)
                            newUnit = ""
                        },
                        enabled = newUnit.isNotBlank(),
                    ) { Text("用这个单元") }
                }
            }
            if (words.any { it.unitName.isNotBlank() }) {
                item {
                    Text("这次听写", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedUnits(words.map { it.unitName }).forEach { unit ->
                            androidx.compose.material3.FilterChip(
                                selected = unit in playUnits,
                                onClick = { viewModel.togglePlayUnit(unit) },
                                label = { Text(unitLabel(unit)) },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = !randomOrder,
                        onClick = { viewModel.setRandom(false) },
                        label = { Text("顺序播放") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = randomOrder,
                        onClick = { viewModel.setRandom(true) },
                        label = { Text("随机播放") },
                    )
                }
            }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.start(PlayMode.TIMER) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    ) { Text("定时${playAction(list?.subject)}") }
                    FilledTonalButton(
                        onClick = { viewModel.start(PlayMode.VOICE) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    ) { Text(voiceAction(list?.subject)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { shoot() }, enabled = !busy) { Text("拍照识别") }
                    OutlinedButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                        enabled = !busy,
                    ) { Text("相册") }
                    TextButton(onClick = viewModel::preview, enabled = !busy) { Text("试听") }
                }
            }
            item {
                Text(
                    "拍照时尽量横排、字大、光线足。拼音如果被认进去，下一步可以取消勾选。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { Text("正在识别…") }
            }
            item {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入一个词") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                viewModel.addManual(draft)
                                draft = ""
                            },
                            enabled = draft.isNotBlank(),
                        ) { Text("添加") }
                    },
                )
            }
            item {
                TextButton(onClick = { pasteOpen = true }) { Text("一次粘贴很多词") }
            }
            itemsIndexed(words, key = { _, word -> word.id }) { index, word ->
                val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
                val missing = StudyGuide.missingCompanions(word.text, lang, words.map { it.text }.toSet())
                WordRow(
                    word = word,
                    seconds = WordText.writingSeconds(
                        word.text,
                        settings.writingSeconds,
                        settings.idiomSeconds,
                        settings.secondsPerLetter,
                        settings.englishMinSeconds,
                    ),
                    base = settings.writingSeconds,
                    canUp = index > 0,
                    canDown = index < words.lastIndex,
                    groupLabel = if (missing.isEmpty()) "" else StudyGuide.focusLabel(word.focus).ifBlank { "这一组" },
                    onEdit = { editing = word },
                    onUp = { viewModel.move(word, -1) },
                    onDown = { viewModel.move(word, 1) },
                    onDelete = { viewModel.deleteWord(word) },
                    onAddGroup = { viewModel.requestGroup(word) },
                )
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("批量添加") },
            text = {
                OutlinedTextField(
                    value = paste,
                    onValueChange = { paste = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    placeholder = { Text("一行一个，或用顿号、逗号分开") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addManual(paste)
                        paste = ""
                        pasteOpen = false
                    },
                ) { Text("加入词单") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("取消") } },
        )
    }

    editing?.let { word ->
        EditWordDialog(
            initial = word.text,
            lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH),
            onDismiss = { editing = null },
            onSave = { text, lang, meaning, contrast, unit ->
                viewModel.updateWord(word, text, lang, meaning, contrast, unit)
                editing = null
            },
            initialMeaning = word.meaning,
            initialContrast = word.contrast,
            initialUnit = word.unitName,
        )
    }

    if (groupOffer != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGroupOffer,
            title = { Text("要一起记这一组吗？") },
            text = { Text(groupOffer.orEmpty() + "\n加进去之后，听写时会先讲清区别，再一个一个写。") },
            confirmButton = { TextButton(onClick = viewModel::acceptGroupOffer) { Text("加入") } },
            dismissButton = { TextButton(onClick = viewModel::dismissGroupOffer) { Text("先不加") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (fixedUnit == null) "删除这个词单？" else "清空这个单元？") },
            text = { Text(if (fixedUnit == null) "词会一起删掉。" else "只清空这一单元里的词。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteList() }) {
                    Text(if (fixedUnit == null) "删除" else "清空")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun WordRow(
    word: WordEntity,
    seconds: Int,
    base: Int,
    canUp: Boolean,
    canDown: Boolean,
    groupLabel: String,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    onAddGroup: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            TextButton(onClick = onEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(word.text, style = MaterialTheme.typography.titleMedium)
            }
            val note = buildString {
                append(langLabel(word.lang))
                if (word.unitName.isNotBlank()) append(" · ${unitLabel(word.unitName)}")
                if (word.meaning.isNotBlank()) append(" · ${word.meaning}")
                if (seconds != base) append(" · 写字 ${seconds} 秒")
            }
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (word.contrast.isNotBlank()) {
                Text(word.contrast, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (groupLabel.isNotBlank()) {
                TextButton(onClick = onAddGroup) { Text("补全「$groupLabel」这一组") }
            }
        }
        IconButton(onClick = onUp, enabled = canUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
        }
        IconButton(onClick = onDown, enabled = canDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除这个词")
        }
    }
}

@Composable
fun EditWordDialog(
    initial: String,
    lang: Lang,
    onDismiss: () -> Unit,
    onSave: (String, Lang, String, String, String) -> Unit,
    initialMeaning: String = "",
    initialContrast: String = "",
    initialUnit: String = "",
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var chosen by remember(lang) { mutableStateOf(lang) }
    var meaning by remember(initialMeaning) { mutableStateOf(initialMeaning) }
    var contrast by remember(initialContrast) { mutableStateOf(initialContrast) }
    var unit by remember(initialUnit) { mutableStateOf(initialUnit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改词语") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("词语") })
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("单元，不填就是未分单元") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { chosen = Lang.ZH }) { Text(if (chosen == Lang.ZH) "中文 ✓" else "中文") }
                    TextButton(onClick = { chosen = Lang.EN }) { Text(if (chosen == Lang.EN) "英语 ✓" else "英语") }
                }
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chosen == Lang.EN) "中文意思" else "词语意思") },
                )
                OutlinedTextField(
                    value = contrast,
                    onValueChange = { contrast = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chosen == Lang.EN) "同音词怎么分" else "易混字怎么分") },
                    minLines = 2,
                )
                TextButton(
                    onClick = {
                        val hit = StudyGuide.lookup(text.trim(), chosen)
                        meaning = hit?.meaning.orEmpty()
                        contrast = hit?.contrast.orEmpty()
                    },
                ) { Text("按词库填入") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, chosen, meaning, contrast, unit) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun playAction(subject: String?): String = when (subject) {
    Subject.ENGLISH.name -> "认读"
    DRILL_SUBJECT -> "默写"
    else -> "听写"
}

private fun voiceAction(subject: String?): String = when (subject) {
    Subject.ENGLISH.name -> "说完再认读"
    DRILL_SUBJECT -> "说完再默写"
    else -> "说完再继续"
}

private fun studyHint(subject: String?): String {
    return when (subject) {
        Subject.ENGLISH.name -> "英语先读单词，再说中文意思。同音词会讲清该写哪一个，然后再读一遍。"
        Subject.CHINESE.name -> "语文先读词语，再讲意思。容易写错的字会说清楚，比如势不可挡的势，不是事情的事。同一组的词可以一起加进来记。"
        else -> "语文会讲易混字，英语会讲中文意思和同音词。"
    }
}
