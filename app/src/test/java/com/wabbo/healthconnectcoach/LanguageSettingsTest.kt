package com.wabbo.healthconnectcoach

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],qualifiers="de-rDE-w427dp-h852dp-mdpi",application=Application::class)
class LanguageSettingsTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previousLocale:Locale
    private var previousScale=1f
    @Before fun setup() {
        previousLocale=Locale.getDefault()
        previousScale=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        if(Build.VERSION.SDK_INT>=33) context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.getEmptyLocaleList()
        context.getSharedPreferences("roco_language",Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun restore() {
        if(Build.VERSION.SDK_INT>=33) context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.getEmptyLocaleList()
        context.getSharedPreferences("roco_language",Context.MODE_PRIVATE).edit().clear().commit()
        Locale.setDefault(previousLocale)
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,previousScale)
    }
    @Test fun manualNativeLanguageIsStoredAndAppliedForAllFourLanguages() {
        for(code in listOf("de","en","fr","es")) {
            RocoLocales.selectLanguage(context,code)
            assertEquals(code,RocoLocales.selectedLanguage(context))
            assertEquals(code,RocoLocales.wrap(context).resources.configuration.locales[0].language)
        }
        val spanish=RocoLocales.wrap(context)
        assertEquals("Hoy",RocoStrings.translate(spanish,"Heute"))
    }
    @Test fun systemSelectionClearsTheAndroidOverrideAndUnsupportedLanguagesFallBackToEnglish() {
        RocoLocales.selectLanguage(context,"fr");RocoLocales.selectLanguage(context,"")
        assertTrue(context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
        assertEquals("",RocoLocales.selectedLanguage(context))
        assertEquals("en",RocoLocales.resolve(Locale.forLanguageTag("pt-BR")).language)
        assertEquals("es",RocoLocales.resolve(Locale.forLanguageTag("es-MX")).language)
        assertThrows(IllegalArgumentException::class.java) {RocoLocales.selectLanguage(context,"it")}
    }
    @Test @Config(sdk=[32]) fun olderAndroidPersistsSelectionAndCanReturnToTheActualDeviceLanguage() {
        RocoLocales.selectLanguage(context,"fr")
        assertEquals("fr",RocoLocales.selectedLanguage(context))
        assertEquals("fr",RocoLocales.wrap(context).resources.configuration.locales[0].language)
        assertEquals("fr",context.getSharedPreferences("roco_language",Context.MODE_PRIVATE).getString("selected",null))
        RocoLocales.selectLanguage(context,"")
        val expected=RocoLocales.resolve(Resources.getSystem().configuration.locales[0]).language
        assertEquals(expected,RocoLocales.wrap(context).resources.configuration.locales[0].language)
    }
    @Test fun chooserHasSystemAndNativeLanguageNamesAndSavesTheTappedLanguage() {
        compose.setContent {HealthCoachTheme(darkTheme=false) {Column(Modifier.requiredSize(427.dp,852.dp).padding(24.dp)) {LanguageSettingsCard()}}}
        compose.onNodeWithText("App-Sprache").performClick()
        listOf("Systemsprache","Deutsch","English","Français","Español").forEach {compose.onAllNodesWithText(it).onFirst().assertExists()}
        compose.onNodeWithTag("language-es").performClick()
        compose.onNodeWithTag("detail-sheet").assertDoesNotExist()
        assertEquals("es",RocoLocales.selectedLanguage(context))
        compose.onNodeWithText("Español").assertIsDisplayed()
    }
}
