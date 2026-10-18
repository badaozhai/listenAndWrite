package app.listenandwrite.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.WordEntity
import app.listenandwrite.data.rememberWrong
import app.listenandwrite.domain.PlayMode
import kotlinx.coroutines.launch

class SummaryViewModel(app: Application) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val practice = listenApp.practice
    private val dao = listenApp.database.dao()
    val title = practice.title
    private val listId = practice.listId
    val words = practice.words
    var mode by mutableStateOf(practice.mode)
    val wrong = mutableStateListOf<Long>()

    fun toggleWrong(word: WordEntity) {
        if (!wrong.remove(word.id)) {
            wrong.add(word.id)
            viewModelScope.launch { dao.rememberWrong(word) }
        }
    }

    fun replay(onlyWrong: Boolean): Boolean {
        val source = if (onlyWrong) words.filter { it.id in wrong } else words
        if (source.isEmpty()) return false
        practice.set(
            listId,
            title,
            mode,
            source,
            englishCue = practice.englishCue,
            hideAnswer = practice.hideAnswer,
        )
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(onBack: () -> Unit, onAgain: () -> Unit, onGrade: () -> Unit) {
    val app = LocalContext.current.applicationContext as ListenApp
    val viewModel: SummaryViewModel = viewModel()
    if (viewModel.words.isEmpty() || app.practice.listId == 0L && viewModel.words.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("这次没有可对照的词语。")
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("对一对", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "看和本子上写的一样不一样。点「写错了」会放进错题本。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onGrade, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("拍照批改")
                }
            }
            itemsIndexed(viewModel.words) { index, word ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(word.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                        if (word.meaning.isNotBlank()) {
                            Text(word.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (word.contrast.isNotBlank()) {
                            Text(word.contrast, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilterChip(
                        selected = word.id in viewModel.wrong,
                        onClick = { viewModel.toggleWrong(word) },
                        label = { Text("写错了") },
                    )
                }
            }
            item {
                Text("再听时用哪种方式", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.mode == PlayMode.TIMER,
                        onClick = { viewModel.mode = PlayMode.TIMER },
                        label = { Text("定时") },
                    )
                    FilterChip(
                        selected = viewModel.mode == PlayMode.VOICE,
                        onClick = { viewModel.mode = PlayMode.VOICE },
                        label = { Text("说完再继续") },
                    )
                }
            }
            item {
                Button(
                    onClick = { if (viewModel.replay(onlyWrong = false)) onAgain() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("再听全部") }
            }
            item {
                OutlinedButton(
                    onClick = { if (viewModel.replay(onlyWrong = true)) onAgain() },
                    enabled = viewModel.wrong.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("只听写错的") }
            }
            item { TextButton(onClick = onBack) { Text("返回词单") } }
        }
    }
}
