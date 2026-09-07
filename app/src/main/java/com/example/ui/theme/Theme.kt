package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark,
    onSecondary = BrandOnPrimaryDark,
    secondaryContainer = BrandPrimaryContainerDark,
    onSecondaryContainer = BrandOnPrimaryContainerDark,
    background = BrandBackgroundDark,
    onBackground = BrandOnBackgroundDark,
    surface = BrandBackgroundDark,
    onSurface = BrandOnBackgroundDark,
    surfaceVariant = BrandSurfaceVariantDark,
    onSurfaceVariant = BrandOnSurfaceVariantDark,
    outline = BrandOutlineDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = BrandPrimaryLight,
    onSecondary = BrandOnPrimaryLight,
    secondaryContainer = BrandPrimaryContainerLight,
    onSecondaryContainer = BrandOnPrimaryContainerLight,
    background = BrandBackgroundLight,
    onBackground = BrandOnBackgroundLight,
    surface = BrandBackgroundLight,
    onSurface = BrandOnBackgroundLight,
    surfaceVariant = BrandSurfaceVariantLight,
    onSurfaceVariant = BrandOnSurfaceVariantLight,
    outline = BrandOnSurfaceVariantLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
