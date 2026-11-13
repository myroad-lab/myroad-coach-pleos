package com.myroad.coach.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디자인 가이드(라이트 테마) 기준 색상.
 * informative_* 값은 가이드의 Informative 토큰 값을 그대로 사용했습니다.
 */
object PleosColor {
    val background = Color(0xFFFFFFFF)       // background_primary (light)
    val surface = Color(0xFFF3F4F6)          // 카드 배경
    val line = Color(0xFFE4E6EA)
    val text1 = Color(0xFF121417)            // text_dark_primary
    val text2 = Color(0xFF5A5F68)            // text_dark_secondary
    val text3 = Color(0xFF8E939C)            // text_dark_tertiary
    val text4 = Color(0xFFB9BDC4)            // text_dark_quaternary
    val buttonBasic = Color(0x0D121417)      // button_basic_enabled (alpha_dark_50)
    val buttonBasicPressed = Color(0x17121417)
    val buttonFilled = Color(0xFF3F434B)     // button_filled_enabled (basic_600)

    val active = Color(0xFF02C265)           // informative_active
    val positive = Color(0xFF0064FF)         // informative_positive
    val negative = Color(0xFFFE3D16)         // informative_negative
    val autonomous = Color(0xFF5A46FA)       // informative_autonomous_driving
    val caution = Color(0xFFF29A00)
    val cautionText = Color(0xFFB86F00)

    val activeSoft = Color(0x1A02C265)
    val positiveSoft = Color(0x170064FF)
    val negativeSoft = Color(0x17FE3D16)
    val cautionSoft = Color(0x1FF29A00)
    val autonomousSoft = Color(0x175A46FA)
}

/** 가이드 타이포그래피 스케일 (Headline 60/56/48, Title 40/36/32, Body 30/28/26) */
object PleosType {
    val headlineS = TextStyle(fontSize = 48.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, color = PleosColor.text1)
    val titleL = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, color = PleosColor.text1)
    val titleM = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = PleosColor.text1)
    val titleS = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = PleosColor.text1)
    val bodyL = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, color = PleosColor.text1)
    val bodyM = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, color = PleosColor.text1)
    val bodyS = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = PleosColor.text1)
    val label = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = PleosColor.text2)
    val caption = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, color = PleosColor.text3)
}
