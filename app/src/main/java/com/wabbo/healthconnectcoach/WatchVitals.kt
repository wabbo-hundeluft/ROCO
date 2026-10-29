package com.wabbo.healthconnectcoach

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Optional watch measurements enrich the overview; they never silently change score formulas. */
internal object WatchVitals {
    val permissions = setOf(OxygenSaturationRecord::class, RespiratoryRateRecord::class,
        SkinTemperatureRecord::class, ActiveCaloriesBurnedRecord::class, DistanceRecord::class,
        FloorsClimbedRecord::class, Vo2MaxRecord::class).map { HealthPermission.getReadPermission(it) }.toSet()
    data class Result(val values: Map<LocalDate, Map<String, Double>>, val origins: Map<String, Set<String>>, val errors: List<String>,val selectedSources: Map<String,String> = emptyMap())
    suspend fun read(repository: HealthConnectRepository, start: Instant, end: Instant, zone: ZoneId,
        granted: Set<String>, bindings: PrimarySourceBindingStore): Result {
        val days = mutableMapOf<LocalDate, MutableMap<String, Double>>()
        val sources = mutableMapOf<String, Set<String>>()
        val errors = mutableListOf<String>()
        val selectedSources=mutableMapOf<String,String>()
        suspend fun <T:Record> collect(type:kotlin.reflect.KClass<T>, key:String, sum:Boolean=false, samples:(T)->List<Pair<Instant,Double>>) {
            if(HealthPermission.getReadPermission(type) !in granted) return
            try {
                val records=repository.readAdditional(type,start,end).records
                withContext(Dispatchers.Default) {
                val name=type.simpleName!!
                sources[name]=records.map { it.metadata.dataOrigin.packageName }.toSet()
                bindings.autoBind(name,records.map { it.metadata.dataOrigin })
                val chosen=bindings.get(name)?.packageName
                if(chosen==null) { if(sources[name].orEmpty().size>1) errors+="$name: Hauptquelle auswählen";return@withContext }
                selectedSources[key]=chosen
                val values=records.distinctBy { if (it.metadata.id.isNotEmpty()) it.metadata.dataOrigin.packageName + ":" + it.metadata.id else it.toString() }
                    .filter { it.metadata.dataOrigin.packageName==chosen }.flatMap(samples)
                    .filter { (time,value)->time in start..end && value.isFinite() }
                values.groupBy { it.first.atZone(zone).toLocalDate() }.forEach { (date,entries)->
                    val sorted=entries.map { it.second }.sorted()
                    days.getOrPut(date){mutableMapOf()}[key]=if(sum) sorted.sum() else if(sorted.size%2==0) (sorted[sorted.size/2-1]+sorted[sorted.size/2])/2 else sorted[sorted.size/2]
                }
                }
            } catch(e:CancellationException){throw e}
            catch(e:HealthConnectPermissionRevokedException){throw e}
            catch(e:HealthConnectAppDisconnectedException){throw e}
            catch(e:HealthConnectForegroundRequiredException){throw e}
            catch(_:Exception){errors+="${type.simpleName}: derzeit nicht lesbar"}
        }
        collect(OxygenSaturationRecord::class,"spo2") { listOf(it.time to it.percentage.value) }
        collect(RespiratoryRateRecord::class,"respiratory_rate") { listOf(it.time to it.rate) }
        collect(SkinTemperatureRecord::class,"skin_delta") { record -> record.deltas.map { it.time to it.delta.inCelsius } }
        collect(ActiveCaloriesBurnedRecord::class,"active_kcal",true) { listOf(it.endTime.minusNanos(1) to it.energy.inKilocalories) }
        collect(DistanceRecord::class,"distance_km",true) { listOf(it.endTime.minusNanos(1) to it.distance.inKilometers) }
        collect(FloorsClimbedRecord::class,"floors",true) { listOf(it.endTime.minusNanos(1) to it.floors) }
        collect(Vo2MaxRecord::class,"vo2max") { listOf(it.time to it.vo2MillilitersPerMinuteKilogram) }
        return Result(days,sources,errors,selectedSources)
    }
}
