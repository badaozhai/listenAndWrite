@file:OptIn(ExperimentalMaterial3Api::class)

package app.listenandwrite.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.ListenApp
import app.listenandwrite.data.AppSettings
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.englishCueLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as ListenApp).settings
    val settings = store.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("听写设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("倒计时从读完之后才开始，这样写字时间不会被朗读吃掉。", style = MaterialTheme.typography.bodyLarge)
            SecondsSlider(
                title = "普通词写字时间",
                seconds = settings.writingSeconds,
                range = 3f..15f,
                onChange = { value -> viewModel.update { it.copy(writingSeconds = value) } },
            )
            SecondsSlider(
                title = "四字成语写字时间",
                seconds = settings.idiomSeconds,
                range = 6f..30f,
                onChange = { value -> viewModel.update { it.copy(idiomSeconds = value) } },
            )
            Text("每个词读几遍", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                labels = listOf("1 遍", "2 遍", "3 遍"),
                selected = (settings.repeats - 1).coerceIn(0, 2),
                onSelect = { index -> viewModel.update { it.copy(repeats = index + 1) } },
            )
            Text("语速", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                labels = listOf("慢", "标准", "稍快"),
                selected = rateIndex(settings.speechRate),
                onSelect = { index ->
                    val rate = when (index) {
                        0 -> 0.7f
                        2 -> 1.05f
                        else -> 0.85f
                    }
                    viewModel.update { it.copy(speechRate = rate) }
                },
            )
            SwitchRow(
                title = "语文讲解易混字",
                body = "读完词语后讲意思。势不可挡会说明：势不是事情的事。同一组的词只在开头讲一次。",
                checked = settings.explainChinese,
                onChecked = { checked -> viewModel.update { it.copy(explainChinese = checked) } },
            )
            Text("英语怎么报", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                labels = EnglishCue.entries.map { englishCueLabel(it) },
                selected = EnglishCue.entries.indexOf(settings.englishCue).coerceAtLeast(0),
                onSelect = { index ->
                    viewModel.update { it.copy(englishCue = EnglishCue.entries[index]) }
                },
            )
            Text(
                "报完之后才开始倒计时。英语按字母数留时间：字母数 × 每个字母的秒数，少于最少秒数就用最少秒数。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondsSlider(
                title = "英语每个字母预留",
                seconds = settings.secondsPerLetter,
                range = 1f..3f,
                onChange = { value -> viewModel.update { it.copy(secondsPerLetter = value) } },
            )
            SecondsSlider(
                title = "英语最少预留",
                seconds = settings.englishMinSeconds,
                range = 3f..15f,
                onChange = { value -> viewModel.update { it.copy(englishMinSeconds = value) } },
            )
            SwitchRow(
                title = "打乱顺序",
                body = "同一组的词仍挨在一起，只打乱组的顺序。",
                checked = settings.shuffle,
                onChecked = { checked -> viewModel.update { it.copy(shuffle = checked) } },
            )
            SwitchRow(
                title = "英语再拼一遍字母",
                body = "先读单词，再读 C, A, T。适合拼写听写。",
                checked = settings.spellEnglish,
                onChecked = { checked -> viewModel.update { it.copy(spellEnglish = checked) } },
            )
        }
    }
}

@Composable
private fun SecondsSlider(
    title: String,
    seconds: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Column {
        Text("$title · $seconds 秒", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = seconds.toFloat().coerceIn(range),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
        )
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun rateIndex(rate: Float): Int = when {
    rate < 0.78f -> 0
    rate < 0.95f -> 1
    else -> 2
}
