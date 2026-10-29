package com.wabbo.healthconnectcoach

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class)
class RocoIconTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private fun drawable(id:Int)=requireNotNull(app.getDrawable(id)).mutate()
    private fun render(d:Drawable,size:Int=216,circle:Boolean=false): Bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also {
        val canvas=Canvas(it)
        if(circle) canvas.clipPath(android.graphics.Path().apply {addCircle(size/2f,size/2f,size/2f,android.graphics.Path.Direction.CW)})
        d.setBounds(0,0,size,size);d.draw(canvas)
    }
    private fun export(name:String,b:Bitmap) {val file=File("build/outputs/roco-icons/$name.png");file.parentFile?.mkdirs();file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun bothLauncherVariantsDeclareNativeMonochromeLayersAndTheRocoLabel() {
        listOf(R.mipmap.ic_launcher,R.mipmap.ic_launcher_round).forEach {id->
            val icon=drawable(id) as AdaptiveIconDrawable
            assertNotNull(icon.monochrome)
        }
        assertTrue(app.applicationInfo.loadLabel(app.packageManager).toString().startsWith("ROCO"))
        assertEquals("ROCO",app.getString(R.string.app_name))
    }
    @Test fun robotPortraitStaysInsideAdaptiveSafeAreaAndTintHasActualEyeCutouts() {
        for(id in listOf(R.drawable.ic_launcher_foreground,R.drawable.ic_launcher_monochrome)) {
            val b=render(drawable(id));val opaque=mutableListOf<Pair<Int,Int>>()
            for(y in 0 until b.height) for(x in 0 until b.width) if(Color.alpha(b.getPixel(x,y))>8) opaque+=x to y
            assertTrue(opaque.isNotEmpty())
            assertTrue(opaque.minOf {it.first}>=42&&opaque.maxOf {it.first}<=174)
            assertTrue(opaque.minOf {it.second}>=42&&opaque.maxOf {it.second}<=174)
        }
        val mono=render(drawable(R.drawable.ic_launcher_monochrome))
        assertEquals(0,Color.alpha(mono.getPixel(62,110)))
        assertTrue(Color.alpha(mono.getPixel(78,98))>0)
    }
    @Test fun packagedPortraitMatchesTheLiveRobotsSharedRenderer() {
        val expected=programmedRobotPortraitBitmap()
        val actual=android.graphics.BitmapFactory.decodeResource(app.resources,R.drawable.roco_portrait_foreground)
        assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
        var mismatches=0
        for(y in 0 until expected.height step 4) for(x in 0 until expected.width step 4) {
            val a=expected.getPixel(x,y);val b=actual.getPixel(x,y)
            if(kotlin.math.abs(Color.alpha(a)-Color.alpha(b))>2||Color.alpha(a)>64&&listOf(Color.red(a)-Color.red(b),Color.green(a)-Color.green(b),Color.blue(a)-Color.blue(b)).any {kotlin.math.abs(it)>4}) mismatches++
        }
        assertEquals("Regenerate the launcher asset after changing the shared robot artwork",0,mismatches)
    }
    @Test fun renderActualAdaptiveAssetsInNormalAndWallpaperTints() {
        export("roco-color",render(drawable(R.mipmap.ic_launcher),512))
        export("roco-round",render(drawable(R.mipmap.ic_launcher_round),192,true))
        export("roco-pixel-color",render(drawable(R.mipmap.ic_launcher),512,true))
        val themes=listOf("light" to ("#E3EED9" to "#244A34"),"dark" to ("#24372C" to "#C2D9A9"))
        for((name,colors) in themes) {
            val mask=drawable(R.drawable.ic_launcher_monochrome).apply {setTint(Color.parseColor(colors.second))}
            val icon=AdaptiveIconDrawable(ColorDrawable(Color.parseColor(colors.first)),mask)
            export("roco-tinted-$name",render(icon,512))
            export("roco-pixel-tinted-$name",render(icon,512,true))
            export("roco-tinted-$name-small",render(icon,48))
        }
        export("roco-small",render(drawable(R.mipmap.ic_launcher),48))
    }
}
