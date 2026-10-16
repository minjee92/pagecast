package com.pagecast.app.di

import android.content.Context

/**
 * 앱 전역 의존성을 조립하는 수동 DI 컨테이너.
 * Application이 하나만 보유하며, 화면(ViewModel)은 여기서 필요한 객체를 꺼내 쓴다.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
}
