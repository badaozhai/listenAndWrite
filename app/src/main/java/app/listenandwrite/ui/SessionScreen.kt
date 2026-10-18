package app.listenandwrite.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.speech.VoiceCommandListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(onBack: () -> Unit, onFinished: (String) -> Unit) {
    val viewModel: SessionViewModel = viewModel()
    val ui = viewModel.ui
    val context = LocalContext.current
    val view = LocalView.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askMic = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        micGranted = granted
        if (!granted) viewModel.onVoiceError("没有麦克风权限。点「下一个」按钮就行。")
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        viewModel.finished.collect { route -> onFinished(route) }
    }
    LaunchedEffect(ui.mode) {
        if (ui.mode == PlayMode.VOICE && !micGranted) askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(ui.listening, micGranted) {
        if (!ui.listening || !micGranted) return@DisposableEffect onDispose {}
        val listener = VoiceCommandListener(
            context = context,
            onText = viewModel::onHeard,
            onError = viewModel::onVoiceError,
        )
        listener.start()
        onDispose { listener.destroy() }
    }

    val progress = if (ui.total == 0) 0f else ui.index.toFloat() / ui.total
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (ui.total == 0) "听写" else "第 ${ui.index + 1} / ${ui.total} 个") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "结束")
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            ui.ttsWarning?.let { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = { openTtsSettings(context) }) { Text("打开语音设置") }
            }
            ui.voiceWarning?.let { warning ->
                Text(warning, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (ui.phase) {
                    Phase.Empty -> Text(ui.hint, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Phase.Loading, Phase.Speaking -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(ui.hint, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall)
                        Text("词语已隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Phase.Writing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ui.remainingSeconds.toString(),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ui.remainingSeconds <= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                        Text("请写在本子上", style = MaterialTheme.typography.titleLarge)
                    }
                    Phase.Listening -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.height(72.dp))
                        Text(ui.hint, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    Phase.Paused -> Text("已暂停", style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (ui.phase != Phase.Empty) {
                if (ui.hideAnswer) {
                    Text(
                        "默写不显示答案。听到「嘀」就写，写完会自动读下一题。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    HoldToPeek(ui.answer)
                }
                if (ui.phase == Phase.Paused) {
                    Button(onClick = viewModel::resume, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Text("继续", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    FilledTonalButton(onClick = viewModel::replay, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Text("再听一次", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().height(76.dp)) {
                        Text("下一个", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::previous, modifier = Modifier.weight(1f)) { Text("上一个") }
                    if (ui.phase != Phase.Paused) {
                        OutlinedButton(onClick = viewModel::pause, modifier = Modifier.weight(1f)) { Text("暂停") }
                    }
                }
            } else {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
}

@Composable
private fun HoldToPeek(answer: String) {
    var show by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(answer) {
                awaitEachGesture {
                    awaitFirstDown()
                    show = true
                    waitForUpOrCancellation()
                    show = false
                }
            },
    ) {
        Text(
            text = if (show && answer.isNotBlank()) answer else "读不出来时，按住这里查看",
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = if (show) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        )
    }
}
