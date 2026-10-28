package com.pagecast.app.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * `ACTION_OPEN_DOCUMENT` 계약.
 * androidx의 OpenDocument는 CATEGORY_OPENABLE을 붙여 구글 문서 같은 가상 파일을 목록에서 제외한다.
 * 공식 문서(virtual files)에 따라 이 카테고리를 빼서 가상 파일도 고를 수 있게 한다.
 */
class OpenDocumentContract : ActivityResultContract<Array<String>, Uri?>() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}
