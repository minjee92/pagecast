package com.pagecast.app.util

import android.util.Log

/**
 * 앱 공용 로그 래퍼.
 *
 * 책 본문이 로그로 새지 않도록, 메시지는 고정된 이벤트 이름과 **숫자 메타데이터**만 받는다.
 * 문자열(본문, 제목, 파일명 등)을 넘길 수 있는 오버로드를 일부러 두지 않는다.
 * release 빌드에서는 R8 규칙(proguard-rules.pro)이 Log 호출 자체를 제거한다.
 */
object AppLog {
    private const val TAG = "Pagecast"

    fun d(event: String, vararg meta: Pair<String, Number>) {
        Log.d(TAG, format(event, meta))
    }

    fun w(event: String, vararg meta: Pair<String, Number>) {
        Log.w(TAG, format(event, meta))
    }

    private fun format(event: String, meta: Array<out Pair<String, Number>>): String =
        if (meta.isEmpty()) event else meta.joinToString(prefix = "$event ", separator = " ") { "${it.first}=${it.second}" }
}
