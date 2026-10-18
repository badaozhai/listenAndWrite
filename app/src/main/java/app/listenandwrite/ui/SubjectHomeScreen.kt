package app.listenandwrite.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.WordEntity
import app.listenandwrite.data.prepareSubject
import app.listenandwrite.data.studiedWord
import app.listenandwrite.data.AppSettings
import app.listenandwrite.data.prepareEnglishDrills
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.LastPlay
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.Subject
import app.listenandwrite.domain.playCaption
import app.listenandwrite.domain.englishCueLabel
import app.listenandwrite.domain.CHINESE_UNIT_COUNT
import app.listenandwrite.domain.GARDEN_AFTER_18
import app.listenandwrite.domain.GARDEN_AFTER_22
import app.listenandwrite.domain.UNIT_COUNT
import app.listenandwrite.domain.slotTitle
import app.listenandwrite.domain.subjectLabel
import app.listenandwrite.domain.unitSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectGate(
    onSubject: (Subject) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("听写助手") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("先选一科", style = MaterialTheme.typography.headlineMedium)
            Text(
                "语文和英语分开记。每一科有 15 个单元，可以先空着。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SubjectCard(
                title = "语文",
                body = "词语、成语。易混字会讲解，比如势不是事情的事。",
                onClick = { onSubject(Subject.CHINESE) },
                modifier = Modifier.weight(1f),
            )
            SubjectCard(
                title = "英语",
                body = "认读会报出英文。默写听中文，在纸上写，播放时不看手机。",
                onClick = { onSubject(Subject.ENGLISH) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubjectCard(title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SubjectHomeViewModel(app: Application, val subject: Subject) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()
    private val listId = MutableStateFlow<Long?>(null)
    val ready = listId
    val words = listId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeWords(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val langName = if (subject == Subject.ENGLISH) Lang.EN.name else Lang.ZH.name
    val mistakeCount = dao.observeMistakeCount(langName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val settings = listenApp.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val selected = MutableStateFlow<Set<Int>>(emptySet())
    val randomOrder = MutableStateFlow(false)
    val resumeMode = MutableStateFlow(PlayMode.TIMER)
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            listId.value = dao.prepareSubject(subject)
            val saved = listenApp.settings.flow.first().let {
                if (subject == Subject.CHINESE) it.lastChinese else it.lastRead
            }
            selected.value = saved.units
            randomOrder.value = saved.random
            resumeMode.value = saved.mode
        }
    }

    fun toggle(unit: Int) {
        val current = selected.value
        selected.value = if (unit in current) current - unit else current + unit
    }

    fun selectAll() {
        val last = if (subject == Subject.CHINESE) CHINESE_UNIT_COUNT else UNIT_COUNT
        val gardens = if (subject == Subject.CHINESE) setOf(GARDEN_AFTER_18, GARDEN_AFTER_22) else emptySet()
        selected.value = (1..last).toSet() + gardens
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun setCue(cue: EnglishCue) {
        viewModelScope.launch { listenApp.settings.update { it.copy(englishCue = cue) } }
    }

    fun start(mode: PlayMode) {
        val id = listId.value ?: return
        val chosen = selected.value
        if (chosen.isEmpty()) {
            _message.tryEmit(if (subject == Subject.CHINESE) "先勾选要听写的课，可以选多课" else "先勾选要听写的单元，可以选多个")
            return
        }
        viewModelScope.launch {
            val items = dao.words(id).filter { unitSlot(it.unitName) in chosen }
            if (items.isEmpty()) {
                _message.emit("勾选的范围里还没有词")
                return@launch
            }
            val ordered = orderWords(items, randomOrder.value)
            val cue = listenApp.settings.flow.first().englishCue
            resumeMode.value = mode
            listenApp.settings.update { current ->
                val play = LastPlay(chosen, randomOrder.value, mode, cue)
                if (subject == Subject.CHINESE) current.copy(lastChinese = play) else current.copy(lastRead = play)
            }
            val action = if (subject == Subject.ENGLISH) "认读" else "听写"
            listenApp.practice.set(
                listId = id,
                title = subjectLabel(subject.name) + action,
                mode = mode,
                words = ordered,
                backRoute = "subject/${subject.name}",
            )
            _message.emit("open-session")
        }
    }

    fun startSavedDrill() {
        viewModelScope.launch {
            val saved = listenApp.settings.flow.first().lastDrill
            if (saved.units.isEmpty()) {
                _message.emit("先到默写里勾选单元")
                return@launch
            }
            val id = dao.prepareEnglishDrills()
            val items = dao.words(id).filter { unitSlot(it.unitName) in saved.units }
            if (items.isEmpty()) {
                _message.emit("上次的默写单元里还没有题")
                return@launch
            }
            listenApp.practice.set(
                listId = id,
                title = drillTitle(saved.units),
                mode = saved.mode,
                words = orderWords(items, saved.random),
                backRoute = "subject/${Subject.ENGLISH.name}",
                englishCue = saved.cue,
                hideAnswer = true,
            )
            _message.emit("open-session")
        }
    }

    fun seed() {
        val id = listId.value ?: return
        viewModelScope.launch {
            if (dao.words(id).isNotEmpty()) {
                _message.emit("已经有词了，示例没有再放")
                return@launch
            }
            val samples = if (subject == Subject.ENGLISH) {
                listOf("apple", "school", "there", "their", "they're")
            } else {
                listOf("春天", "一心一意", "势不可挡", "气势", "优势")
            }
            val lang = if (subject == Subject.ENGLISH) Lang.EN else Lang.ZH
            dao.insertWords(samples.mapIndexed { index, text -> studiedWord(id, text, lang, index, "1") })
        }
    }
}

class SubjectHomeViewModelFactory(
    private val app: Application,
    private val subject: Subject,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SubjectHomeViewModel(app, subject) as T
    }
}

private fun orderWords(items: List<WordEntity>, random: Boolean): List<WordEntity> {
    if (random) return items.shuffled()
    return items.sortedWith(
        compareBy({ unitSlot(it.unitName).let { slot -> if (slot == 0) 10_000 else slot } }, { it.position }, { it.id }),
    )
}

private fun drillTitle(units: Set<Int>): String {
    val only = units.filter { it > 0 }.singleOrNull()
    return if (only != null) "默写练习 第${only}单元" else "默写练习"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectHomeScreen(
    subject: Subject,
    onBack: () -> Unit,
    onOpenUnit: (listId: Long, unit: Int) -> Unit,
    onMistakes: () -> Unit,
    onDrills: () -> Unit,
    onSession: () -> Unit,
    onSettings: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ListenApp
    val viewModel: SubjectHomeViewModel = viewModel(
        key = subject.name,
        factory = SubjectHomeViewModelFactory(app, subject),
    )
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val words by viewModel.words.collectAsStateWithLifecycle()
    val mistakes by viewModel.mistakeCount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val random by viewModel.randomOrder.collectAsStateWithLifecycle()
    val resumeMode by viewModel.resumeMode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.message.collect { text ->
            if (text == "open-session") onSession() else snackbar.showSnackbar(text)
        }
    }
    val counts = words.groupingBy { unitSlot(it.unitName) }.eachCount()
    val lessonCount = if (subject == Subject.CHINESE) CHINESE_UNIT_COUNT else UNIT_COUNT
    val lessonLabel = if (subject == Subject.CHINESE) "课" else "单元"
    val action = if (subject == Subject.ENGLISH) "认读" else "听写"
    val playable = selected.filter { (counts[it] ?: 0) > 0 }.toSet()
    val savedPlay = if (subject == Subject.CHINESE) settings.lastChinese else settings.lastRead
    val currentPlay = LastPlay(selected, random, resumeMode, settings.englishCue)
    val sameAsLast = savedPlay.units.isNotEmpty() &&
        savedPlay.units == currentPlay.units &&
        savedPlay.random == currentPlay.random &&
        savedPlay.mode == currentPlay.mode &&
        (subject != Subject.ENGLISH || savedPlay.cue == currentPlay.cue)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(subjectLabel(subject.name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
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
        if (ready == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (playable.isNotEmpty()) {
                item {
                    Card(
                        onClick = { viewModel.start(resumeMode) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (sameAsLast) "继续$action" else "开始$action",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                playCaption(currentPlay.copy(units = playable), subject == Subject.CHINESE, subject == Subject.ENGLISH),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "听到「嘀」就写在本子上，不用看手机。写完这一条，下一题会接着读。",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = onMistakes,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("错题本", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (mistakes == 0) "写错的词会收在这里" else "$mistakes 个词，可以再听一遍",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (subject == Subject.ENGLISH) {
                item {
                    val drill = settings.lastDrill
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("默写", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "听中文，在纸上写英文。播放时不报英文，也不显示答案。",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            if (drill.units.isNotEmpty()) {
                                Text(
                                    playCaption(drill, chinese = false, withCue = true),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Button(onClick = viewModel::startSavedDrill, modifier = Modifier.fillMaxWidth()) {
                                    Text("继续默写")
                                }
                            }
                            TextButton(onClick = onDrills) { Text(if (drill.units.isEmpty()) "去选单元" else "换单元") }
                        }
                    }
                }
                item {
                    Text("认读", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "这里会报出英文，用来认单词。要默写用上面那一块。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "勾选${lessonLabel}后$action，可以多选。点进去可以录入。没有词的${lessonLabel}会显示为空。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::selectAll) { Text("全选") }
                    TextButton(onClick = viewModel::clearSelection) { Text("清空选择") }
                    FilterChip(selected = !random, onClick = { viewModel.randomOrder.value = false }, label = { Text("顺序") })
                    FilterChip(selected = random, onClick = { viewModel.randomOrder.value = true }, label = { Text("随机") })
                }
            }
            if (subject == Subject.ENGLISH) {
                item {
                    Text("怎么报", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnglishCue.entries.forEach { cue ->
                            FilterChip(
                                selected = settings.englishCue == cue,
                                onClick = { viewModel.setCue(cue) },
                                label = { Text(englishCueLabel(cue)) },
                            )
                        }
                    }
                    Text(
                        "报完后按字母留时间：每个字母 ${settings.secondsPerLetter} 秒，最少 ${settings.englishMinSeconds} 秒。可在设置里改。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.start(PlayMode.TIMER) }, modifier = Modifier.weight(1f)) { Text("定时$action") }
                    FilledTonalButton(onClick = { viewModel.start(PlayMode.VOICE) }, modifier = Modifier.weight(1f)) {
                        Text(if (subject == Subject.ENGLISH) "说完再认读" else "说完再继续")
                    }
                }
            }
            items(lessonRows(subject)) { slot ->
                UnitRow(
                    title = slotTitle(slot, subject == Subject.CHINESE),
                    count = counts[slot] ?: 0,
                    checked = slot in selected,
                    onCheck = { viewModel.toggle(slot) },
                    onOpen = { ready?.let { onOpenUnit(it, slot) } },
                )
            }
            if ((counts[0] ?: 0) > 0) {
                item {
                    UnitRow(
                        title = "其他",
                        count = counts[0] ?: 0,
                        checked = false,
                        onCheck = {},
                        onOpen = { ready?.let { onOpenUnit(it, 0) } },
                        showCheck = false,
                    )
                }
            }
            if (words.isEmpty()) {
                item {
                    TextButton(onClick = viewModel::seed) { Text("把示例放进第1单元") }
                }
            }
        }
    }
}

private fun lessonRows(subject: Subject): List<Int> {
    val last = if (subject == Subject.CHINESE) CHINESE_UNIT_COUNT else UNIT_COUNT
    return buildList {
        for (unit in 1..last) {
            add(unit)
            if (subject == Subject.CHINESE && unit == 18) add(GARDEN_AFTER_18)
            if (subject == Subject.CHINESE && unit == 22) add(GARDEN_AFTER_22)
        }
    }
}

@Composable
private fun UnitRow(
    title: String,
    count: Int,
    checked: Boolean,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    showCheck: Boolean = true,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCheck) {
                Checkbox(checked = checked, onCheckedChange = { onCheck() })
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(if (count == 0) "空" else "$count 个", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
