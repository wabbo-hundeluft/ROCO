package com.wabbo.healthconnectcoach

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.security.MessageDigest

internal val googleHealthReadScopes = listOf(
    "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
    "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
    "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
)
private tailrec fun Context.findActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Native authorization preflight. Does not mix cloud measurements into Health Connect scores. */
@Composable internal fun GoogleHealthConnectionCard(onConnected: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val oauthIdentity = remember(context) { androidOAuthIdentity(context) }
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var technical by remember {mutableStateOf(false)}
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(GoogleHealthSession.enabled(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val auth = remember(activity) { activity?.let { Identity.getAuthorizationClient(it) } }
    fun validate(result: AuthorizationResult) {
        val token=result.accessToken
        if(token.isNullOrBlank()) { busy=false;message="Google hat keinen nutzbaren Zugriffstoken geliefert.";return }
        val allowed=result.grantedScopes.toSet()
        scope.launch {
            try {
                verifyGoogleHealthIdentity(token)
                val count=googleHealthReadScopes.count { it in allowed }
                check(count==3) { "Google hat nur $count von 3 benötigten Lesebereichen freigegeben. Bitte alle drei Bereiche bestätigen; Health Connect bleibt verfügbar." }
                GoogleHealthSession.accept(token)
                GoogleHealthSession.setEnabled(context, true)
                connected=true
                message="Google Health verbunden · $count von 3 Lesebereichen freigegeben. Google-/Pixel-Watch-Messwerte werden bevorzugt geladen. Bei einem Importfehler wird der vollständige Health-Connect-Datensatz als Ersatz verwendet."
                onConnected()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { message=if(e is IllegalStateException) e.message else "Google Health ist derzeit nicht erreichbar. Bitte Netzverbindung prüfen." }
            finally { busy=false }
        }
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        val data=response.data
        if(data==null) { busy=false;message="Google hat keinen abgeschlossenen Freigabenachweis zurückgegeben (Android-Ergebnis ${response.resultCode}). Eine Kontowahl allein bestätigt die Leserechte noch nicht. Prüfe den Android-OAuth-Client und das Testkonto." }
        else try { validate(auth!!.getAuthorizationResultFromIntent(data)) }
        catch(e:ApiException) { busy=false;message=googleAuthorizationFailure(e.statusCode) }
        catch(_:Exception) { busy=false;message="Die Google-Antwort konnte nicht bestätigt werden. Prüfe die unten angezeigten Android-OAuth-Daten und den Testnutzer." }
    }
    Surface(color=Color.White,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            RocoText("Google Health · direkt verbinden",style=MaterialTheme.typography.titleLarge)
            RocoText("Verbinde deine Uhrendaten über dein Google-Konto. Bei einem nicht erreichbaren Google-Import steht Health Connect als vollständige Ersatzquelle bereit.",style=MaterialTheme.typography.bodyMedium)
            ClayButton(onClick={
                busy=true;message=null
                val request=AuthorizationRequest.builder().setRequestedScopes(googleHealthReadScopes.map(::Scope)).build()
                auth?.authorize(request)?.addOnSuccessListener { result ->
                    if(result.hasResolution()) {
                        val pending=result.pendingIntent
                        if(pending==null) { busy=false;message="Google konnte keine Freigabe öffnen." }
                        else try { launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                        catch(_:Exception) { busy=false;message="Die Google-Anmeldung konnte nicht geöffnet werden." }
                    } else validate(result)
                }?.addOnFailureListener {
                    busy=false;message=(it as? ApiException)?.statusCode?.let(::googleAuthorizationFailure) ?: "Google-Anmeldung fehlgeschlagen. Bitte Android-OAuth-Client und Netzwerk prüfen."
                }
            },enabled=!busy && auth!=null) { RocoText(if(busy) "Google-Zugang wird geprüft …" else if(connected) "Google-Verbindung prüfen" else "Mit Google Health verbinden") }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { RocoText(it,style=MaterialTheme.typography.bodyMedium) }
            RocoText("Kein Firebase-/Supabase-Konto erforderlich. Zugriffstokens bleiben im Arbeitsspeicher. Google verwaltet die Freigabe und erneuert den Zugang.",style=MaterialTheme.typography.bodySmall)
            if (GoogleHealthSession.enabled(context)) TextButton(onClick={ GoogleHealthSession.setEnabled(context, false); connected=false; message="Health Connect ist jetzt deine bevorzugte Messquelle."; onConnected() }) { RocoText("Health Connect als Quelle verwenden") }
            TextButton(onClick={technical=!technical}) {RocoText(if(technical) "Technische Angaben schließen" else "Technische Angaben zur Verbindung")}
            if(technical) {RocoText(oauthIdentity,style=MaterialTheme.typography.bodySmall);TextButton(onClick={clipboard.setText(AnnotatedString(oauthIdentity))}) {RocoText("Android-OAuth-Daten kopieren")}}
        }
    }
}

internal fun googleAuthorizationFailure(status: Int): String = when(status) {
    10 -> "Google-Status 10: Paketname und Signatur stimmen nicht mit einem Android-OAuth-Client im Projekt überein. Bitte die unten angezeigten Werte registrieren."
    16,12501 -> "Google-Status $status: Die Freigabe wurde abgebrochen. Falls du nur ein Konto ausgewählt hast: Android-OAuth-Client, Testnutzer, API-Aktivierung und den Google-Health-Zugang dieses Kontos prüfen."
    8 -> "Google-Status 8: Der Google-Dienst meldet einen internen Fehler. Die Ursache ist noch nicht geklärt. Vergleiche zuerst den unten angezeigten Paketnamen und SHA-1 exakt mit dem Android-OAuth-Client; anschließend API-Aktivierung und Testkonto prüfen."
    7 -> "Google-Status 7: Keine Netzwerkverbindung. Bitte erneut versuchen."
    else -> "Google-Freigabe fehlgeschlagen (Status $status). Prüfe Android-OAuth-Client, API-Aktivierung und Testnutzer."
}

private fun androidOAuthIdentity(context: Context): String = runCatching {
    val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
    val certificate=info.signingInfo!!.apkContentsSigners.first().toByteArray()
    val sha=MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(":") {"%02X".format(it)}
    "Android-Paket: ${context.packageName}\nZertifikat SHA-1: $sha"
}.getOrElse {"Android-Paket: ${context.packageName}\nZertifikat konnte nicht angezeigt werden."}

internal suspend fun verifyGoogleHealthIdentity(token:String) = withContext(Dispatchers.IO) {
    val connection=URL("https://health.googleapis.com/v4/users/me/identity").openConnection() as HttpsURLConnection
    try {
        connection.requestMethod="GET";connection.instanceFollowRedirects=false
        connection.connectTimeout=15000;connection.readTimeout=20000
        connection.setRequestProperty("Authorization","Bearer $token")
        when(val status=connection.responseCode) {
            in 200..299 -> connection.inputStream.use { input ->
                // Identity is checked, not persisted or exposed in diagnostic output.
                val buffer=ByteArray(4096);var total=0
                while(true) { val read=input.read(buffer);if(read<0) break;total+=read;check(total<=32768){"Google hat eine unerwartet große Kontoantwort geliefert."} }
            }
            400 -> error("Das Google-Konto ist möglicherweise noch nicht mit einem Google-Health-/Fitbit-Profil verknüpft.")
            401 -> error("Der Google-Zugriff ist abgelaufen. Bitte erneut verbinden.")
            403 -> error("Google Health verweigert den Projektzugriff. Prüfe API-Freischaltung, Testnutzer und Lesebereiche; eine Preview-Sperre kann nur Google freigeben.")
            429 -> error("Das Google-Health-Anfragelimit ist erreicht. Bitte später erneut versuchen.")
            else -> error("Google Health antwortet mit HTTP $status. Die Verbindung ist noch nicht bestätigt.")
        }
    } finally { connection.disconnect() }
}
