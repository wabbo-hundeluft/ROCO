package com.wabbo.healthconnectcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wabbo.healthconnectcoach.R

data class HealthCoachCategoryColors(
    val sleep: Color,
    val readiness: Color,
    val training: Color,
)

val LocalHealthCoachCategoryColors = staticCompositionLocalOf {
    HealthCoachCategoryColors(
        sleep = Color(0xFFDAD34E),
        readiness = Color(0xFF71C878),
        training = Color(0xFFF18477),
    )
}

val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

val IbmPlexSerif = FontFamily(Font(R.font.ibm_plex_serif_regular, FontWeight.Normal))

private val LightCategoryColors = HealthCoachCategoryColors(
    sleep = Color(0xFF557AA9),
    readiness = Color(0xFF202820),
    training = Color(0xFFBB7342),
)

private val DarkCategoryColors = HealthCoachCategoryColors(
    sleep = Color(0xFFE9E071),
    readiness = Color(0xFF8CDB92),
    training = Color(0xFFFFA398),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF202820),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4EE),
    onPrimaryContainer = Color(0xFF124B47),
    secondary = Color(0xFF202820),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F1EB),
    onSecondaryContainer = Color(0xFF202820),
    tertiary = Color(0xFF557AA9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE7FA),
    onTertiaryContainer = Color(0xFF453175),
    background = Color(0xFFF8F8F3),
    onBackground = Color(0xFF202820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202820),
    surfaceVariant = Color(0xFFF0F1EB),
    onSurfaceVariant = Color(0xFF60665F),
    inverseSurface = Color(0xFF202820),
    inverseOnSurface = Color(0xFFF8F8F3),
    inversePrimary = Color(0xFF9BCB96),
    error = Color(0xFFC6322C),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E4),
    onErrorContainer = Color(0xFF7F1D1A),
    outline = Color(0xFF7B8494),
    outlineVariant = Color(0xFFD8DFE7),
    scrim = Color(0x99000000),
    surfaceTint = Color(0xFF202820),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CDB92),
    onPrimary = Color(0xFF0C2B15),
    primaryContainer = Color(0xFF214C2B),
    onPrimaryContainer = Color(0xFFE3F6DD),
    secondary = Color(0xFF9BCB96),
    onSecondary = Color(0xFF102418),
    secondaryContainer = Color(0xFF20372A),
    onSecondaryContainer = Color(0xFFE7F2E2),
    tertiary = Color(0xFFE9E071),
    onTertiary = Color(0xFF453175),
    tertiaryContainer = Color(0xFF45430C),
    onTertiaryContainer = Color(0xFFF6F2BE),
    background = Color(0xFF111C16),
    onBackground = Color(0xFFF0F5ED),
    surface = Color(0xFF19271F),
    onSurface = Color(0xFFF0F5ED),
    surfaceVariant = Color(0xFF20372A),
    onSurfaceVariant = Color(0xFFBACABB),
    inverseSurface = Color(0xFFF0F5ED),
    inverseOnSurface = Color(0xFF111C16),
    inversePrimary = Color(0xFF202820),
    error = Color(0xFFFFA398),
    onError = Color(0xFF4B110C),
    errorContainer = Color(0xFF63221B),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF8C9E8E),
    outlineVariant = Color(0xFF7B9182),
    scrim = Color(0x99000000),
    surfaceTint = Color(0xFF8CDB92),
)

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

val Manrope = FontFamily(Font(R.font.manrope_book,FontWeight(450)),Font(R.font.manrope_medium,FontWeight.Medium),Font(R.font.manrope_demibold,FontWeight(650)),Font(R.font.manrope_bold,FontWeight.Bold),Font(R.font.manrope_heavy,FontWeight(750)),Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_semibold, FontWeight.SemiBold), Font(R.font.manrope_extrabold, FontWeight.ExtraBold))

private val HealthCoachTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold),
    displayMedium = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold),
    displaySmall = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold),
    headlineLarge = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold,letterSpacing=(-1.3).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight(750),letterSpacing=(-.65).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight(650),letterSpacing=(-.75).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight(750),letterSpacing=(-.55).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight(750),letterSpacing=(-.3).sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Manrope, fontFeatureSettings="tnum", fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val HealthCoachShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Brand colors and type are bundled locally; Android dynamic colors are intentionally disabled. */
@Composable
fun HealthCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHealthCoachCategoryColors provides if (darkTheme) DarkCategoryColors else LightCategoryColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HealthCoachTypography,
            shapes = HealthCoachShapes,
            content = content,
        )
    }
}
