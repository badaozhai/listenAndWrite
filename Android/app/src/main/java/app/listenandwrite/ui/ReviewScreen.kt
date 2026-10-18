package app.listenandwrite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.DraftWord
import app.listenandwrite.ListenApp
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.StudyGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(listId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as ListenApp
    val viewModel: ReviewViewModel = viewModel(factory = ReviewViewModelFactory(app, listId))
    val lines = viewModel.lines
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Int?>(null) }
    val checked = lines.count { it.checked }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("核对识别结果") },
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
        if (!viewModel.ready) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("没有待确认的识别结果。", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) { Text("返回") }
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
                    "取消不需要的，点词语可以改。已经在词单里的词会自动跳过，只并入新词。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (notice.isNotBlank()) {
                    Text(notice, color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.applyHanGroups(2) }) { Text("两字一组") }
                    TextButton(onClick = { viewModel.applyHanGroups(4) }) { Text("四字一组") }
                    TextButton(onClick = viewModel::splitEnglish) { Text("英文按空格拆") }
                    TextButton(onClick = viewModel::restore) { Text("还原") }
                }
            }
            itemsIndexed(lines, key = { index, word -> "$index-${word.text}" }) { index, word ->
                ReviewRow(word, onToggle = { viewModel.toggle(index) }, onEdit = { editing = index })
            }
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = checked > 0,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("加入词单（$checked）") }
            }
        }
    }

    val index = editing
    if (index != null && index in lines.indices) {
        val word = lines[index]
        EditWordDialog(
            initial = word.text,
            lang = word.lang,
            onDismiss = { editing = null },
            onSave = { text, lang, _, _, _ ->
                viewModel.edit(index, text, lang)
                editing = null
            },
        )
    }
}

@Composable
private fun ReviewRow(word: DraftWord, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = word.checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            TextButton(onClick = onEdit) {
                Text(word.text, style = MaterialTheme.typography.titleMedium)
            }
            val hit = StudyGuide.lookup(word.text, word.lang)
            val note = listOfNotNull(hit?.meaning?.takeIf { it.isNotBlank() }, hit?.contrast?.takeIf { it.isNotBlank() })
            if (note.isNotEmpty()) {
                Text(note.joinToString(""), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(if (word.lang == Lang.EN) "英" else "中", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
