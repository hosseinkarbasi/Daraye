@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.darayi.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val defaultTypography = Typography()
val DarayiTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = Vazirmatn, lineHeight = 28.sp, letterSpacing = 0.sp),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = Vazirmatn, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = Vazirmatn, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    displayLargeEmphasized = defaultTypography.displayLargeEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    displayMediumEmphasized = defaultTypography.displayMediumEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    displaySmallEmphasized = defaultTypography.displaySmallEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    headlineLargeEmphasized = defaultTypography.headlineLargeEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    headlineMediumEmphasized = defaultTypography.headlineMediumEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    headlineSmallEmphasized = defaultTypography.headlineSmallEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    titleLargeEmphasized = defaultTypography.titleLargeEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    titleMediumEmphasized = defaultTypography.titleMediumEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    titleSmallEmphasized = defaultTypography.titleSmallEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    bodyLargeEmphasized = defaultTypography.bodyLargeEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    bodyMediumEmphasized = defaultTypography.bodyMediumEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    bodySmallEmphasized = defaultTypography.bodySmallEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    labelLargeEmphasized = defaultTypography.labelLargeEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    labelMediumEmphasized = defaultTypography.labelMediumEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp),
    labelSmallEmphasized = defaultTypography.labelSmallEmphasized.copy(fontFamily = Vazirmatn, letterSpacing = 0.sp)
)
