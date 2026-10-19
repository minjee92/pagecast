package com.pagecast.app.di

import android.content.Context
import com.pagecast.app.data.importer.BookImporter
import com.pagecast.app.data.local.PagecastDatabase
import com.pagecast.app.data.repository.BookRepositoryImpl
import com.pagecast.app.domain.repository.BookRepository
import com.pagecast.app.domain.text.SentenceSplitter
import com.pagecast.app.playback.NarrationController
import com.pagecast.app.playback.ProgressRecorder
import com.pagecast.app.tts.TtsEngine
import com.pagecast.app.tts.android.AndroidTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전역 의존성을 조립하는 수동 DI 컨테이너.
 * Application이 하나만 보유하며, 화면(ViewModel)은 여기서 필요한 객체를 꺼내 쓴다.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** 화면 수명과 무관하게 살아 있어야 하는 작업(낭독, 위치 저장)용 스코프. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database: PagecastDatabase by lazy { PagecastDatabase.create(appContext) }

    val bookRepository: BookRepository by lazy { BookRepositoryImpl(database.bookDao()) }

    val bookImporter: BookImporter by lazy {
        BookImporter(appContext.contentResolver, bookRepository, SentenceSplitter())
    }

    /** TTS 엔진 교체 지점. 4단계에서 클라우드 엔진 구현체로 바꿀 수 있다. */
    private val ttsEngine: TtsEngine by lazy { AndroidTtsEngine(appContext) }

    val narrationController: NarrationController by lazy {
        NarrationController(ttsEngine, applicationScope)
    }

    val progressRecorder: ProgressRecorder by lazy {
        ProgressRecorder(narrationController, bookRepository, applicationScope)
    }
}
