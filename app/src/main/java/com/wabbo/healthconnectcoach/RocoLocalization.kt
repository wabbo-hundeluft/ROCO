package com.wabbo.healthconnectcoach

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.content.ContextWrapper
import android.content.res.Resources
import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale
import java.util.regex.Pattern

/** Supported UI locales. A device language outside this set uses English inside ROCO. */
internal object RocoLocales {
    private val supported = setOf("en", "de", "fr", "es")

    fun resolve(primary: Locale): Locale = Locale.forLanguageTag(
        primary.language.takeIf(supported::contains) ?: "en"
    )

    fun resolve(context: Context): Locale {
        val configuration = context.resources.configuration
        return resolve(configuration.locales.get(0) ?: Locale.ENGLISH)
    }

    fun selectedLanguage(context:Context):String {
        val selected=if(Build.VERSION.SDK_INT>=33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language.orEmpty()
        } else context.getSharedPreferences("roco_language",Context.MODE_PRIVATE).getString("selected","").orEmpty()
        return selected.takeIf {it in supported}.orEmpty()
    }

    fun selectLanguage(context:Context,language:String) {
        require(language.isEmpty()||language in supported) {"Unsupported app language"}
        if(selectedLanguage(context)==language) return
        if(Build.VERSION.SDK_INT>=33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(language)
        } else {
            context.getSharedPreferences("roco_language",Context.MODE_PRIVATE).edit().putString("selected",language).apply()
            var current:Context?=context
            while(current is ContextWrapper&&current !is Activity) current=current.baseContext
            (current as? Activity)?.recreate()
        }
    }

    fun wrap(context: Context): Context {
        val selected=selectedLanguage(context)
        val primary=if(selected.isNotEmpty()) Locale.forLanguageTag(selected)
            else if(Build.VERSION.SDK_INT>=33) context.resources.configuration.locales.get(0)
            else Resources.getSystem().configuration.locales.get(0)
        val locale = resolve(primary ?: Locale.ENGLISH)
        Locale.setDefault(locale)
        return withLocale(context, locale)
    }

    fun withLocale(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

/**
 * Localizes built-in Compose copy at the rendering boundary while leaving stored route keys and
 * user content untouched. Resources contain the translated catalog; dynamic values are matched
 * only inside known Kotlin templates and are reinserted verbatim.
 */
internal object RocoStrings {
    private data class Template(val pattern: Pattern, val source: String, val target: String, val args: Int)
    private data class Catalog(val exact: Map<String, String>, val templates: Map<String, List<Template>>, val generic: List<Template>)
    private val catalogs = HashMap<String, Catalog>()
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) { appContext = context.applicationContext }

    fun translate(value: String): String = appContext?.let {
        translate(RocoLocales.withLocale(it, RocoLocales.resolve(Locale.getDefault())), value)
    } ?: value

    private fun catalog(context: Context): Catalog = synchronized(catalogs) {
        val language = RocoLocales.resolve(context).language
        catalogs[language]?.let { return it }
        val rows = JSONArray(context.assets.open("roco-locales.json").bufferedReader().use { it.readText() })
        val exact = HashMap<String, String>(rows.length())
        val templates = HashMap<String, MutableList<Template>>()
        val generic = ArrayList<Template>()
        val marker = Regex("ZXQARG\\d{2}ZXQ")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val source = row.getString("source")
            val target = row.optString(language, source)
            val matches = marker.findAll(source).toList()
            if (matches.isEmpty()) {
                if (source != target) exact[source] = target
            } else {
                val expression = buildString {
                    append('^')
                    var cursor = 0
                    matches.forEach { match ->
                        append(Pattern.quote(source.substring(cursor, match.range.first)))
                        append("([\\s\\S]*?)")
                        cursor = match.range.last + 1
                    }
                    append(Pattern.quote(source.substring(cursor)))
                    append('$')
                }
                val template = Template(Pattern.compile(expression), source, target, matches.size)
                val prefix = source.substring(0, matches.first().range.first).take(2)
                if (prefix.length == 2) templates.getOrPut(prefix) { ArrayList() }.add(template) else generic.add(template)
            }
        }
        templates.values.forEach { candidates -> candidates.sortByDescending { it.source.length } }
        generic.sortByDescending { it.source.length }
        val loaded = Catalog(exact, templates, generic)
        catalogs[language] = loaded
        loaded
    }

    fun translate(context: Context, value: String): String {
        val dictionary = catalog(context)
        dictionary.exact[value]?.let { return it }
        val prefix = value.take(2)
        val candidates = dictionary.templates[prefix].orEmpty() + dictionary.generic
        for (item in candidates) {
            val match = item.pattern.matcher(value)
            if (!match.matches()) continue
            var translated = item.target
            for (index in 1..item.args) {
                translated = translated.replace("ZXQARG${index.toString().padStart(2, '0')}ZXQ", match.group(index).orEmpty())
            }
            return translated
        }
        return value
    }
}

@Composable
internal fun RocoVerbatimText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) = MaterialText(text=text, modifier=modifier, color=color, fontSize=fontSize, fontStyle=fontStyle,
    fontWeight=fontWeight, fontFamily=fontFamily, letterSpacing=letterSpacing, textDecoration=textDecoration,
    textAlign=textAlign, lineHeight=lineHeight, overflow=overflow, softWrap=softWrap, maxLines=maxLines,
    minLines=minLines, onTextLayout=onTextLayout, style=style)

@Composable
internal fun RocoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localized = remember(context, text) { RocoStrings.translate(context, text) }
    MaterialText(text=localized, modifier=modifier, color=color, fontSize=fontSize, fontStyle=fontStyle,
        fontWeight=fontWeight, fontFamily=fontFamily, letterSpacing=letterSpacing, textDecoration=textDecoration,
        textAlign=textAlign, lineHeight=lineHeight, overflow=overflow, softWrap=softWrap, maxLines=maxLines,
        minLines=minLines, onTextLayout=onTextLayout, style=style)
}

@Composable
internal fun RocoText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) = MaterialText(text=text, modifier=modifier, color=color, fontSize=fontSize, fontStyle=fontStyle,
    fontWeight=fontWeight, fontFamily=fontFamily, letterSpacing=letterSpacing, textDecoration=textDecoration,
    textAlign=textAlign, lineHeight=lineHeight, overflow=overflow, softWrap=softWrap, maxLines=maxLines,
    minLines=minLines, onTextLayout=onTextLayout, style=style)
