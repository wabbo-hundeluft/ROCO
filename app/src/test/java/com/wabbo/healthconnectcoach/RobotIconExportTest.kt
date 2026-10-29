package com.wabbo.healthconnectcoach

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Deterministic export from the SAME renderer that paints Pip in the app. No generated illustration. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class)
class RobotIconExportTest {
    @Test fun exportPortraitAssetsFromTheProgrammedCompanion() {
        val pixels=864
        val color=programmedRobotPortraitBitmap(pixels)
        val mono=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888)
        var detailColors=mutableSetOf<Int>()
        for(y in 0 until pixels) for(x in 0 until pixels) {
            val c=color.getPixel(x,y);val a=Color.alpha(c)
            if(a>220) detailColors+=c
            val brightness=.2126*Color.red(c)+.7152*Color.green(c)+.0722*Color.blue(c)
            // Dark visors/ports become true holes; existing bevels retain softer alpha.
            val alpha=(a*((brightness-90)/120).coerceIn(0.0,1.0)).toInt()
            mono.setPixel(x,y,Color.argb(alpha,255,255,255))
        }
        assertTrue("Original contours, eye reflections and shaded materials retained",detailColors.size>12)
        val out=File("build/outputs/roco-portrait-source");out.mkdirs()
        File(out,"roco_portrait_foreground.png").outputStream().use {color.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(out,"roco_portrait_monochrome.png").outputStream().use {mono.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}

internal fun programmedRobotPortraitBitmap(pixels: Int=864): Bitmap {
    val bitmap=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(bitmap)),Size(pixels.toFloat(),pixels.toFloat())) {drawRobotLauncherPortrait()}
    return bitmap
}
