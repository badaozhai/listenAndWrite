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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.prepareEnglishDrills
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.englishCueLabel
import app.listenandwrite.domain.unitSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DRILL_UNITS = listOf(1, 2, 3, 4)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DrillViewModel(app: Application) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()
    private val listId = MutableStateFlow<Long?>(null)
    val ready = listId
    val words = listId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeWords(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selected = MutableStateFlow<Set<Int>>(emptySet())
    val randomOrder = MutableStateFlow(false)
    val cue = MutableStateFlow(EnglishCue.CHINESE)
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    init {
        viewModelScope.launch { listId.value = dao.prepareEnglishDrills() }
    }

    fun toggle(unit: Int) {
        val current = selected.value
        selected.value = if (unit in current) current - unit else current + unit
    }

    fun selectAll() {
        selected.value = DRILL_UNITS.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun setCue(next: EnglishCue) {
        cue.value = next
    }

    fun start(mode: PlayMode) {
        val id = listId.value ?: return
        val chosen = selected.value
        if (chosen.isEmpty()) {
            _message.tryEmit("先勾选要默写的单元，可以选多个")
            return
        }
        viewModelScope.launch {
            val items = dao.words(id).filter { unitSlot(it.unitName) in chosen }
            if (items.isEmpty()) {
                _message.emit("勾选的单元里还没有练习")
                return@launch
            }
            val ordered = if (randomOrder.value) {
                items.shuffled()
            } else {
                items.sortedWith(compareBy({ unitSlot(it.unitName) }, { it.position }, { it.id }))
            }
            val title = if (chosen.size == 1) "默写练习 第${chosen.first()}单元" else "默写练习"
            listenApp.practice.set(
                listId = id,
                title = title,
                mode = mode,
                words = ordered,
                backRoute = "drills",
                englishCue = cue.value,
            )
            _message.emit("open-session")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(
    onBack: () -> Unit,
    onOpenUnit: (listId: Long, unit: Int) -> Unit,
    onSession: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ListenApp
    val viewModel: DrillViewModel = viewModel(factory = DrillViewModelFactory(app))
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val words by viewModel.words.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val random by viewModel.randomOrder.collectAsStateWithLifecycle()
    val cue by viewModel.cue.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.message.collect { text ->
            if (text == "open-session") onSession() else snackbar.showSnackbar(text)
        }
    }
    val counts = words.groupingBy { unitSlot(it.unitName) }.eachCount()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("默写练习") },
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
            item {
                Text(
                    "听中文，在纸上写英文。默认只报中文，不改英语词表的报法。第 3 单元没有印答案，是按词表补的，不对可以点进单元改。",
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
            item {
                Text("怎么报", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnglishCue.entries.forEach { item ->
                        FilterChip(
                            selected = cue == item,
                            onClick = { viewModel.setCue(item) },
                            label = { Text(englishCueLabel(item)) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.start(PlayMode.TIMER) }, modifier = Modifier.weight(1f)) { Text("定时默写") }
                    FilledTonalButton(onClick = { viewModel.start(PlayMode.VOICE) }, modifier = Modifier.weight(1f)) {
                        Text("说完再继续")
                    }
                }
            }
            items(DRILL_UNITS) { unit ->
                DrillUnitRow(
                    title = "第${unit}单元",
                    count = counts[unit] ?: 0,
                    checked = unit in selected,
                    onCheck = { viewModel.toggle(unit) },
                    onOpen = { ready?.let { onOpenUnit(it, unit) } },
                )
            }
        }
    }
}

@Composable
private fun DrillUnitRow(
    title: String,
    count: Int,
    checked: Boolean,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onCheck() })
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(if (count == 0) "空" else "$count 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

class DrillViewModelFactory(
    private val app: Application,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return DrillViewModel(app) as T
    }
}
