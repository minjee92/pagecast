package com.pagecast.app.playback

import com.pagecast.app.domain.model.ReadingProgress
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * 낭독 상태를 지켜보다가 마지막 읽은 위치를 저장한다.
 *
 * 화면이 아니라 앱 스코프에서 동작하므로, 잠금화면·알림에서 문장을 넘겨도 위치가 저장된다.
 * - 재생 중: 문장이 바뀔 때마다 [playingDebounceMs] 뒤에 저장한다(문장 처음부터 재개).
 *   단어 단위로 매번 쓰지 않도록 재생 중에는 문장 안 위치를 무시한다.
 * - 일시정지·정지: 그 즉시 단어 위치까지 저장한다.
 */
class ProgressRecorder(
    private val narration: NarrationController,
    private val repository: BookRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val playingDebounceMs: Long = 1_500,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            narration.state
                .mapNotNull { it.toSnapshot() }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    if (snapshot.isPlaying) delay(playingDebounceMs)
                    save(snapshot)
                }
        }
    }

    private suspend fun save(snapshot: Snapshot) {
        try {
            repository.saveProgress(
                ReadingProgress(
                    bookId = snapshot.bookId,
                    chapterId = snapshot.chapterId,
                    segmentIndex = snapshot.segmentIndex,
                    charOffset = snapshot.charOffset,
                    updatedAt = clock(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 책이 삭제된 직후(FK 위반) 등. 위치 저장 실패로 낭독을 멈추지 않는다.
            AppLog.w("progress_save_failed", "index" to snapshot.segmentIndex)
        }
    }

    private fun NarrationState.toSnapshot(): Snapshot? {
        val book = bookId ?: return null
        val chapter = chapterId ?: return null
        if (!hasContent) return null
        return Snapshot(
            bookId = book,
            chapterId = chapter,
            segmentIndex = currentIndex,
            charOffset = if (isPlaying) 0 else resumeOffset,
            isPlaying = isPlaying,
        )
    }

    private data class Snapshot(
        val bookId: Long,
        val chapterId: Long,
        val segmentIndex: Int,
        val charOffset: Int,
        val isPlaying: Boolean,
    )
}
