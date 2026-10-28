package com.pagecast.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val PagecastTypography = Typography()

/** 책 본문용 스타일. 원서 읽기에 맞춰 세리프 + 넉넉한 줄 간격. */
val ReaderBodyStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 19.sp,
    lineHeight = 30.sp,
)
