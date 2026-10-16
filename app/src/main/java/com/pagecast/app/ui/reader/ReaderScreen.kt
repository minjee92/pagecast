package com.pagecast.app.ui.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagecast.app.R
import com.pagecast.app.playback.EngineStatus
import com.pagecast.app.playback.NarrationState
import com.pagecast.app.ui.AppViewModelFactory
import com.pagecast.app.ui.theme.ReaderColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val narration by viewModel.narrationState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (content as? ReaderContent.Ready)?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                StatusBanner(narration)
                PlayerControls(
                    state = narration,
                    onPlayPause = viewModel::onPlayPause,
                    onPrevious = viewModel::onPrevious,
                    onNext = viewModel::onNext,
                    onSpeedChange = viewModel::onSpeedChange,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val c = content) {
                ReaderContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ReaderContent.NotFound -> Text(stringResource(R.string.reader_not_found), Modifier.align(Alignment.Center))
                is ReaderContent.Ready -> ReaderBody(
                    layout = c.layout,
                    narration = narration,
                    onSentenceTap = viewModel::onSentenceTap,
                )
            }
        }
    }
}

/** 사용자가 직접 스크롤한 뒤 자동 스크롤을 멈춰 두는 시간. */
private const val USER_SCROLL_HOLD_MS = 4_000L

@Composable
private fun ReaderBody(
    layout: ReaderLayout,
    narration: NarrationState,
    onSentenceTap: (Int) -> Unit,
) {
    val activeParagraph = layout.paragraphOf(narration.currentIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeParagraph)
    val sentenceHighlight = if (isSystemInDarkTheme()) ReaderColors.sentenceDark else ReaderColors.sentenceLight

    var lastUserDragAt by remember { mutableLongStateOf(0L) }
    var recenterRequest by remember { mutableIntStateOf(0) }
    // 현재 문단의 텍스트 레이아웃. 줄 단위 자동 스크롤에 쓴다.
    var activeLayout by remember { mutableStateOf<Pair<Int, TextLayoutResult>?>(null) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) lastUserDragAt = System.currentTimeMillis()
        }
    }

    // 지금 읽는 위치(단어가 없으면 문장 시작)가 문단 안에서 몇 번째 줄인지
    val paragraph = layout.paragraphs[activeParagraph]
    val sentenceInParagraph = (narration.currentIndex - paragraph.firstSentence).coerceIn(0, paragraph.sentences.lastIndex)
    val charInParagraph = paragraph.sentenceStarts[sentenceInParagraph] + (narration.wordRange?.first ?: 0)
    val textLayout = activeLayout?.takeIf { it.first == activeParagraph }?.second
    val targetLine = textLayout?.getLineForOffset(charInParagraph.coerceAtMost(textLayout.layoutInput.text.length)) ?: 0

    LaunchedEffect(activeParagraph, targetLine, recenterRequest) {
        // "현재 위치로" 버튼은 lastUserDragAt을 0으로 돌려 이 검사를 통과시킨다.
        val userScrolledRecently = System.currentTimeMillis() - lastUserDragAt < USER_SCROLL_HOLD_MS
        if (userScrolledRecently) return@LaunchedEffect
        scrollToLine(listState, activeParagraph, textLayout, targetLine)
    }

    val activeVisible by remember(activeParagraph) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == activeParagraph } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(layout.paragraphs, key = { it.index }) { p ->
                val isActive = narration.currentIndex in p
                ParagraphText(
                    paragraph = p,
                    activeSentence = if (isActive) narration.currentIndex else null,
                    wordRange = if (isActive) narration.wordRange else null,
                    sentenceHighlight = sentenceHighlight,
                    onSentenceTap = onSentenceTap,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { if (isActive) activeLayout = p.index to it },
                )
            }
        }
        if (!activeVisible) {
            FilledTonalButton(
                onClick = {
                    lastUserDragAt = 0L
                    recenterRequest++
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) { Text(stringResource(R.string.reader_jump_to_current)) }
        }
    }
}

/** 대상 줄이 화면의 위쪽 30% 근처에 오도록 스크롤한다. 이미 편하게 보이는 위치면 움직이지 않는다. */
private suspend fun scrollToLine(
    listState: LazyListState,
    paragraphIndex: Int,
    textLayout: TextLayoutResult?,
    line: Int,
) {
    val info = listState.layoutInfo
    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    val lineTop = textLayout?.getLineTop(line)?.toInt() ?: 0
    val lineBottom = textLayout?.getLineBottom(line)?.toInt() ?: 0

    val item = info.visibleItemsInfo.firstOrNull { it.index == paragraphIndex }
    if (item != null) {
        val top = item.offset + lineTop
        val bottom = item.offset + lineBottom
        val comfortableTop = info.viewportStartOffset + viewportHeight / 10
        val comfortableBottom = info.viewportEndOffset - viewportHeight / 4
        if (top >= comfortableTop && bottom <= comfortableBottom) return
    }
    val offset = (lineTop - viewportHeight * 3 / 10).coerceAtLeast(0)
    listState.animateScrollToItem(paragraphIndex, offset)
}

@Composable
private fun StatusBanner(state: NarrationState) {
    val messageRes = when (state.engineStatus) {
        EngineStatus.ENGINE_UNAVAILABLE -> R.string.reader_engine_unavailable
        EngineStatus.LANGUAGE_UNAVAILABLE -> R.string.reader_language_unavailable
        EngineStatus.ERROR -> R.string.reader_engine_error
        else -> if (state.wordEventsSupported == false) R.string.reader_no_word_events else null
    } ?: return
    val showSettings = state.engineStatus == EngineStatus.ENGINE_UNAVAILABLE ||
        state.engineStatus == EngineStatus.LANGUAGE_UNAVAILABLE || state.wordEventsSupported == false
    val context = LocalContext.current

    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(messageRes), style = MaterialTheme.typography.bodySmall)
            if (showSettings) {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(TTS_SETTINGS_ACTION))
                    } catch (_: ActivityNotFoundException) {
                        // 설정 화면이 없는 기기 — 안내 문구만 보여 준다.
                    }
                }) { Text(stringResource(R.string.reader_tts_settings)) }
            }
        }
    }
}

private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
