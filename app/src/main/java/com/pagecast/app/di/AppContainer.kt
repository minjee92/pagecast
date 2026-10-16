package com.pagecast.app.di

import android.content.Context
import com.pagecast.app.data.local.PagecastDatabase
import com.pagecast.app.data.repository.BookRepositoryImpl
import com.pagecast.app.domain.repository.BookRepository

/**
 * 앱 전역 의존성을 조립하는 수동 DI 컨테이너.
 * Application이 하나만 보유하며, 화면(ViewModel)은 여기서 필요한 객체를 꺼내 쓴다.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: PagecastDatabase by lazy { PagecastDatabase.create(appContext) }

    val bookRepository: BookRepository by lazy { BookRepositoryImpl(database.bookDao()) }
}
