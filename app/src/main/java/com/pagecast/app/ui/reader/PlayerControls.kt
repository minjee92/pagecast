package com.pagecast.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pagecast.app.R
import com.pagecast.app.playback.NarrationController
import com.pagecast.app.playback.NarrationState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerControls(
    state: NarrationState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 슬라이더를 끄는 동안은 화면에만 반영하고, 손을 뗄 때 한 번만 속도를 바꾼다
    // (속도 변경은 현재 문장을 다시 말하게 하므로 드래그 중 매번 적용하면 소리가 끊긴다).
    var draggingSpeed by remember { mutableFloatStateOf(Float.NaN) }
    val shownSpeed = if (draggingSpeed.isNaN()) state.speed else draggingSpeed

    Surface(tonalElevation = 3.dp, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.reader_position, state.currentIndex + 1, state.segmentCount),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.reader_speed, String.format(Locale.US, "%.1f", shownSpeed)),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = shownSpeed,
                onValueChange = { draggingSpeed = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = {
                    if (!draggingSpeed.isNaN()) onSpeedChange(draggingSpeed)
                    draggingSpeed = Float.NaN
                },
                valueRange = NarrationController.MIN_SPEED..NarrationController.MAX_SPEED,
                steps = 14,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = state.hasContent) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.reader_previous))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = state.hasContent,
                    modifier = Modifier.size(64.dp),
                ) {
                    if (state.isPlaying) {
                        Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause), Modifier.size(36.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play), Modifier.size(36.dp))
                    }
                }
                IconButton(onClick = onNext, enabled = state.hasContent) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.reader_next))
                }
            }
        }
    }
}
