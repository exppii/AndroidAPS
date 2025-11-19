package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
// import app.aaps.core.data.model.GV
// import app.aaps.core.data.model.SourceSensor
// import app.aaps.core.data.model.TrendArrow
// import app.aaps.core.data.plugin.PluginType
// import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.database.impl.AppRepository
import app.aaps.database.transactions.TransactionGlucoseValue
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.impl.transactions.CgmSourceTransaction
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OttaiPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_ottai)
        .preferencesId(R.xml.pref_bgsource)
        .pluginName(R.string.ottai_app)
        .description(R.string.description_source_patched_ottai_app),
    aapsLogger, rh, injector
), BgSource {
    class OttaiWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var ottaiPlugin: OttaiPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var repository: AppRepository
        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!ottaiPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries"){
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received Ottai Data $data")
                if (!data.isNullOrEmpty()){
                    try {
                        val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->
                                    glucoseValues += TransactionGlucoseValue(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = jsonObject.getDouble("sgv"),
                                        noise = null,
                                        trendArrow = GlucoseValue.TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = GlucoseValue.SourceSensor.OTTAI
                                    )
                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                        repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving values from Eversense App", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { savedValues ->
                                savedValues.all().forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                                }
                            }
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                        ret = Result.failure(workDataOf("Error" to e.toString()))
                    }
                }
            }
            return ret
        }
    }
}