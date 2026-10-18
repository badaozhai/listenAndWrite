package app.listenandwrite.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.MistakeEntity
import app.listenandwrite.data.WordEntity
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.StudyGuide
import app.listenandwrite.domain.WordSet
import app.listenandwrite.domain.englishCueLabel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MistakeViewModel(app: Application, private val langName: String) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()
    val mistakes = dao.observeMistakes(langName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()
    private val lang = runCatching { Lang.valueOf(langName) }.getOrDefault(Lang.ZH)

    fun add(text: String, meaning: String, contrast: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (dao.mistake(lang.name, WordSet.key(clean, lang)) != null) {
                _message.emit("错题本里已经有这个词")
                return@launch
            }
            val guide = StudyGuide.lookup(clean, lang)
            dao.insertMistake(
                MistakeEntity(
                    text = clean,
                    lang = lang.name,
                    meaning = meaning.trim().ifBlank { guide?.meaning.orEmpty() },
                    contrast = contrast.trim().ifBlank { guide?.contrast.orEmpty() },
                    focus = guide?.focus.orEmpty(),
                    wrongCount = 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun edit(item: MistakeEntity, text: String, meaning: String, contrast: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val found = dao.mistake(lang.name, WordSet.key(clean, lang))
            if (found != null && found.id != item.id) {
                _message.emit("错题本里已经有这个词")
                return@launch
            }
            val guide = StudyGuide.lookup(clean, lang)
            dao.updateMistake(
                id = item.id,
                text = clean,
                meaning = meaning.trim(),
                contrast = contrast.trim(),
                focus = guide?.focus.orEmpty(),
                now = System.currentTimeMillis(),
            )
        }
    }

    fun remove(item: MistakeEntity) {
        viewModelScope.launch { dao.deleteMistake(item.id) }
    }

    fun start(mode: PlayMode, random: Boolean): Boolean {
        val source = mistakes.value
        if (source.isEmpty()) return false
        val ordered = if (random) source.shuffled() else source
        listenApp.practice.set(
            listId = 0,
            title = if (langName == "EN") "英语错题本" else "语文错题本",
            mode = mode,
            backRoute = "mistakes/$langName",
            words = ordered.mapIndexed { index, item ->
                WordEntity(
                    id = item.id,
                    listId = 0,
                    text = item.text,
                    lang = item.lang,
                    position = index,
                    meaning = item.meaning,
                    contrast = item.contrast,
                    focus = item.focus,
                )
            },
        )
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeScreen(langName: String, onBack: () -> Unit, onSession: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ListenApp
    val viewModel: MistakeViewModel = viewModel(factory = MistakeViewModelFactory(app, langName))
    val mistakes by viewModel.mistakes.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var random = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var editing by remember { mutableStateOf<MistakeEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MistakeEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val chineseBook = langName != "EN"
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbar.showSnackbar(it) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加") },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("错题本") },
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
        if (mistakes.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("还没有错题", style = MaterialTheme.typography.headlineSmall)
                Text("点右下角自己添加，也可以在对答案时点「写错了」。")
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("专门把这些词再听一遍。", style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !random.value, onClick = { random.value = false }, label = { Text("顺序播放") })
                    FilterChip(selected = random.value, onClick = { random.value = true }, label = { Text("随机播放") })
                }
            }
            if (langName == "EN") {
                item {
                    Text("怎么报")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnglishCue.entries.forEach { cue ->
                            FilterChip(
                                selected = settings.englishCue == cue,
                                onClick = { settingsViewModel.update { it.copy(englishCue = cue) } },
                                label = { Text(englishCueLabel(cue)) },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { if (viewModel.start(PlayMode.TIMER, random.value)) onSession() },
                        modifier = Modifier.weight(1f),
                    ) { Text("定时巩固") }
                    FilledTonalButton(
                        onClick = { if (viewModel.start(PlayMode.VOICE, random.value)) onSession() },
                        modifier = Modifier.weight(1f),
                    ) { Text("说完再继续") }
                }
            }
            items(mistakes, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { editing = item }) {
                            Text(item.text, style = MaterialTheme.typography.titleLarge)
                        }
                        val note = buildString {
                            if (item.meaning.isNotBlank()) append(item.meaning)
                            if (item.contrast.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(item.contrast)
                            }
                            if (isNotEmpty()) append(" · ")
                            append("错过 ${item.wrongCount} 次")
                        }
                        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingDelete = item }) {
                        Icon(Icons.Filled.Delete, contentDescription = "移出错题本")
                    }
                }
            }
        }
        }
    }
    if (adding || editing != null) {
        MistakeEditDialog(
            title = if (adding) "添加错题" else "修改错题",
            chinese = chineseBook,
            initialText = editing?.text.orEmpty(),
            initialMeaning = editing?.meaning.orEmpty(),
            initialContrast = editing?.contrast.orEmpty(),
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { text, meaning, contrast ->
                val current = editing
                if (current == null) viewModel.add(text, meaning, contrast) else viewModel.edit(current, text, meaning, contrast)
                adding = false
                editing = null
            },
        )
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("移出「${item.text}」？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(item)
                    pendingDelete = null
                }) { Text("移出") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun MistakeEditDialog(
    title: String,
    chinese: Boolean,
    initialText: String,
    initialMeaning: String,
    initialContrast: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var meaning by remember(initialMeaning) { mutableStateOf(initialMeaning) }
    var contrast by remember(initialContrast) { mutableStateOf(initialContrast) }
    val lang = if (chinese) Lang.ZH else Lang.EN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chinese) "词语" else "单词") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chinese) "词语意思" else "中文意思") },
                )
                OutlinedTextField(
                    value = contrast,
                    onValueChange = { contrast = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (chinese) "易混字怎么分" else "同音词怎么分") },
                    minLines = 2,
                )
                TextButton(onClick = {
                    val hit = StudyGuide.lookup(text.trim(), lang)
                    if (meaning.isBlank()) meaning = hit?.meaning.orEmpty()
                    if (contrast.isBlank()) contrast = hit?.contrast.orEmpty()
                }) { Text("按词库填入空白") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, meaning, contrast) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

class MistakeViewModelFactory(
    private val app: Application,
    private val langName: String,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return MistakeViewModel(app, langName) as T
    }
}
