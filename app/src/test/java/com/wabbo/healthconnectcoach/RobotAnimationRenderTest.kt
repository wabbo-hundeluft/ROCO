package com.wabbo.healthconnectcoach

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the production Canvas artwork, not a separate illustration or mock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class)
class RobotAnimationRenderTest {
    @Test fun exportActualGestureAndPickupFrames() {
        val out=File("build/outputs/robot-animation").apply {mkdirs()}
        val cell=240
        val sheet=Bitmap.createBitmap(cell*6,cell*6,Bitmap.Config.ARGB_8888)
        val frames=listOf(
            RobotMood.WAVE to .12f,RobotMood.WAVE to .3f,RobotMood.WAVE to .52f,RobotMood.CHEERFUL to .35f,RobotMood.CURIOUS to .4f,RobotMood.LISTENING to .4f,
            RobotMood.SLEEPING to .4f,RobotMood.WAKING to .1f,RobotMood.WAKING to .4f,RobotMood.WAKING to .7f,RobotMood.RECOVER to .5f,RobotMood.PROUD to .5f,
            RobotMood.NOD to .4f,RobotMood.WINK to .4f,RobotMood.SHY to .5f,RobotMood.SURPRISED to .5f,RobotMood.STRETCH to .5f,RobotMood.DANCE to .35f,
            RobotMood.HIGH_FIVE to .5f,RobotMood.COMFORT to .5f,RobotMood.WINK to .7f,RobotMood.SHY to .7f,RobotMood.DANCE to .65f,RobotMood.HIGH_FIVE to .7f)
        frames.forEachIndexed {i,(mood,t)->
            val image=Bitmap.createBitmap(cell,cell,Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(image)),Size(cell.toFloat(),cell.toFloat())) {
                drawRect(Color(0xFFF8F8F3));drawRobotArtwork(mood,t)
            }
            android.graphics.Canvas(sheet).drawBitmap(image,((i%6)*cell).toFloat(),((i/6)*cell).toFloat(),null)
        }
        val times=listOf(4.9f,5.7f,6.35f,7.0f,7.6f,8.2f,8.8f,9.5f,10.1f,18.0f,29.0f,ROBOT_ROAD_SECONDS+1)
        times.forEachIndexed {i,t->
            val image=Bitmap.createBitmap(cell,cell,Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(image)),Size(cell.toFloat(),cell.toFloat())) {
                drawRect(Color(0xFFF8F8F3));drawRobotRoad(t)
            }
            android.graphics.Canvas(sheet).drawBitmap(image,((i%6)*cell).toFloat(),((4+i/6)*cell).toFloat(),null)
        }
        File(out,"gesture-and-road-frames.png").outputStream().use {sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
