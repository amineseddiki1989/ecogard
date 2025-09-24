package com.ecoguard.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.ecoguard.app.repository.LogRepository
import com.ecoguard.app.repository.SightingRepository
import com.ecoguard.app.workers.AnonymousReportWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service Firebase Cloud Messaging pour recevoir les alertes d'appareils volés.
 * Lorsqu'un appareil est déclaré volé via le portail web, le serveur envoie une notification
 * push silencieuse à tous les appareils de la communauté.
 */
class EcoGuardMessagingService : FirebaseMessagingService(), KoinComponent {

    private val logRepository: LogRepository by inject()
    private val sightingRepository: SightingRepository by inject()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        try {
            logRepository.logSystem(
                "Notification FCM reçue",
                "From: ${remoteMessage.from}, Data: ${remoteMessage.data.size} éléments"
            )

            // Vérifier si c'est une alerte d'appareil volé
            val messageType = remoteMessage.data["type"]
            if (messageType == "stolen_device_alert") {
                handleStolenDeviceAlert(remoteMessage.data)
            } else {
                logRepository.logSystem("Type de message FCM non géré: $messageType")
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur traitement message FCM: ${e.message}")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        logRepository.logSystem("Nouveau token FCM reçu")
        
        // Dans une vraie implémentation, envoyer le token au serveur
        // pour que l'appareil puisse recevoir les notifications
        sendTokenToServer(token)
    }

    private fun handleStolenDeviceAlert(data: Map<String, String>) {
        try {
            val stolenUuid = data["stolen_uuid"]
            val alertId = data["alert_id"]
            val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

            if (stolenUuid.isNullOrBlank()) {
                logRepository.logError("Alerte appareil volé sans UUID")
                return
            }

            logRepository.logSystem(
                "Alerte appareil volé reçue",
                "UUID: ${stolenUuid.take(8)}..., Alert ID: $alertId"
            )

            // Déclencher la vérification et le rapport en arrière-plan
            triggerSightingCheck(stolenUuid, alertId ?: "unknown", timestamp)

        } catch (e: Exception) {
            logRepository.logError("Erreur traitement alerte appareil volé: ${e.message}")
        }
    }

    private fun triggerSightingCheck(stolenUuid: String, alertId: String, alertTimestamp: Long) {
        // Utiliser WorkManager pour traiter l'alerte en arrière-plan
        val inputData = Data.Builder()
            .putString(StolenDeviceAlertWorker.PARAM_STOLEN_UUID, stolenUuid)
            .putString(StolenDeviceAlertWorker.PARAM_ALERT_ID, alertId)
            .putLong(StolenDeviceAlertWorker.PARAM_ALERT_TIMESTAMP, alertTimestamp)
            .build()

        val alertWorkRequest = OneTimeWorkRequestBuilder<StolenDeviceAlertWorker>()
            .setInputData(inputData)
            .setInitialDelay(2, TimeUnit.SECONDS) // Petit délai pour éviter la surcharge
            .addTag("stolen_alert_$alertId")
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "stolen_alert_$stolenUuid",
                ExistingWorkPolicy.REPLACE,
                alertWorkRequest
            )

        logRepository.logSystem("Worker d'alerte programmé pour UUID: ${stolenUuid.take(8)}...")
    }

    private fun sendTokenToServer(token: String) {
        // Dans une vraie implémentation, envoyer le token au serveur EcoGuard
        // pour que l'appareil puisse recevoir les notifications d'alertes
        logRepository.logSystem("Token FCM à envoyer au serveur: ${token.take(20)}...")
    }
}

/**
 * Worker qui traite les alertes d'appareils volés en vérifiant le SightingRepository local
 * et en déclenchant un rapport anonyme si une observation pertinente est trouvée.
 */
class StolenDeviceAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val logRepository: LogRepository by inject()
    private val sightingRepository: SightingRepository by inject()

    companion object {
        const val PARAM_STOLEN_UUID = "stolen_uuid"
        const val PARAM_ALERT_ID = "alert_id"
        const val PARAM_ALERT_TIMESTAMP = "alert_timestamp"
        
        // Âge maximum d'une observation pour être considérée comme pertinente
        private const val MAX_SIGHTING_AGE_HOURS = 48L
    }

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        
        try {
            val stolenUuid = inputData.getString(PARAM_STOLEN_UUID)
            val alertId = inputData.getString(PARAM_ALERT_ID) ?: "unknown"
            val alertTimestamp = inputData.getLong(PARAM_ALERT_TIMESTAMP, System.currentTimeMillis())

            if (stolenUuid.isNullOrBlank()) {
                logRepository.logError("StolenDeviceAlertWorker: UUID manquant")
                return Result.failure()
            }

            logRepository.logSystem(
                "Traitement alerte appareil volé",
                "UUID: ${stolenUuid.take(8)}..., Alert ID: $alertId"
            )

            // Vérifier si nous avons une observation récente pour cet UUID
            val sighting = sightingRepository.getLatestSightingFor(stolenUuid)

            if (sighting == null) {
                logRepository.logSystem(
                    "Aucune observation trouvée",
                    "UUID: ${stolenUuid.take(8)}... - Aucune action nécessaire"
                )
                return Result.success(createNoSightingOutputData(alertId))
            }

            // Vérifier si l'observation est suffisamment récente
            val sightingAgeHours = (System.currentTimeMillis() - sighting.timestamp) / (1000 * 60 * 60)
            
            if (sightingAgeHours > MAX_SIGHTING_AGE_HOURS) {
                logRepository.logSystem(
                    "Observation trop ancienne",
                    "UUID: ${stolenUuid.take(8)}..., Âge: ${sightingAgeHours}h"
                )
                return Result.success(createOldSightingOutputData(alertId, sightingAgeHours))
            }

            // Vérifier si l'observation a déjà été signalée
            if (sighting.reported) {
                logRepository.logSystem(
                    "Observation déjà signalée",
                    "UUID: ${stolenUuid.take(8)}..."
                )
                return Result.success(createAlreadyReportedOutputData(alertId))
            }

            logRepository.logSystem(
                "Observation pertinente trouvée",
                "UUID: ${stolenUuid.take(8)}..., Âge: ${sightingAgeHours}h, Confiance: ${String.format("%.2f", sighting.confidence)}"
            )

            // Déclencher le rapport anonyme
            val reportSuccess = triggerAnonymousReport(sighting, alertId, alertTimestamp)

            if (reportSuccess) {
                // Marquer l'observation comme signalée
                sightingRepository.markSightingAsReported(stolenUuid)
                
                val processingTime = System.currentTimeMillis() - startTime
                
                logRepository.logSystem(
                    "Rapport anonyme déclenché avec succès",
                    "UUID: ${stolenUuid.take(8)}..., Temps: ${processingTime}ms"
                )

                return Result.success(createSuccessOutputData(alertId, sightingAgeHours, processingTime))
            } else {
                logRepository.logError("Échec déclenchement rapport anonyme")
                return Result.retry()
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur StolenDeviceAlertWorker: ${e.message}")
            return Result.failure()
        }
    }

    private suspend fun triggerAnonymousReport(
        sighting: com.ecoguard.app.repository.Sighting,
        alertId: String,
        alertTimestamp: Long
    ): Boolean {
        return try {
            // Préparer les données pour le rapport anonyme
            val reportData = Data.Builder()
                .putString(AnonymousReportWorker.PARAM_PARTITION_UUID, sighting.partitionUuid)
                .putDouble(AnonymousReportWorker.PARAM_LATITUDE, sighting.latitude)
                .putDouble(AnonymousReportWorker.PARAM_LONGITUDE, sighting.longitude)
                .putFloat(AnonymousReportWorker.PARAM_CONFIDENCE, sighting.confidence)
                .putLong(AnonymousReportWorker.PARAM_SIGHTING_TIMESTAMP, sighting.timestamp)
                .putString(AnonymousReportWorker.PARAM_ALERT_ID, alertId)
                .putLong(AnonymousReportWorker.PARAM_ALERT_TIMESTAMP, alertTimestamp)
                .build()

            // Créer le worker de rapport anonyme
            val reportWorkRequest = OneTimeWorkRequestBuilder<AnonymousReportWorker>()
                .setInputData(reportData)
                .setInitialDelay(5, TimeUnit.SECONDS) // Délai pour éviter la détection de corrélation
                .addTag("anonymous_report_$alertId")
                .build()

            // Programmer le worker
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "anonymous_report_${sighting.partitionUuid}",
                    ExistingWorkPolicy.REPLACE,
                    reportWorkRequest
                )

            true
        } catch (e: Exception) {
            logRepository.logError("Erreur programmation rapport anonyme: ${e.message}")
            false
        }
    }

    private fun createSuccessOutputData(alertId: String, sightingAgeHours: Long, processingTime: Long): Data {
        return Data.Builder()
            .putString("status", "report_triggered")
            .putString("alert_id", alertId)
            .putLong("sighting_age_hours", sightingAgeHours)
            .putLong("processing_time_ms", processingTime)
            .putLong("completion_time", System.currentTimeMillis())
            .build()
    }

    private fun createNoSightingOutputData(alertId: String): Data {
        return Data.Builder()
            .putString("status", "no_sighting")
            .putString("alert_id", alertId)
            .putLong("completion_time", System.currentTimeMillis())
            .build()
    }

    private fun createOldSightingOutputData(alertId: String, ageHours: Long): Data {
        return Data.Builder()
            .putString("status", "sighting_too_old")
            .putString("alert_id", alertId)
            .putLong("sighting_age_hours", ageHours)
            .putLong("completion_time", System.currentTimeMillis())
            .build()
    }

    private fun createAlreadyReportedOutputData(alertId: String): Data {
        return Data.Builder()
            .putString("status", "already_reported")
            .putString("alert_id", alertId)
            .putLong("completion_time", System.currentTimeMillis())
            .build()
    }
}

/**
 * BroadcastReceiver pour les notifications locales (fallback si FCM n'est pas disponible).
 */
class LocalStolenDeviceAlertReceiver : BroadcastReceiver(), KoinComponent {

    private val logRepository: LogRepository by inject()

    companion object {
        const val ACTION_STOLEN_DEVICE_ALERT = "com.ecoguard.app.STOLEN_DEVICE_ALERT"
        const val EXTRA_STOLEN_UUID = "stolen_uuid"
        const val EXTRA_ALERT_ID = "alert_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOLEN_DEVICE_ALERT) {
            val stolenUuid = intent.getStringExtra(EXTRA_STOLEN_UUID)
            val alertId = intent.getStringExtra(EXTRA_ALERT_ID) ?: "local_alert"

            if (!stolenUuid.isNullOrBlank()) {
                logRepository.logSystem(
                    "Alerte locale reçue",
                    "UUID: ${stolenUuid.take(8)}..."
                )

                // Déclencher le même processus que pour FCM
                triggerLocalSightingCheck(context, stolenUuid, alertId)
            }
        }
    }

    private fun triggerLocalSightingCheck(context: Context, stolenUuid: String, alertId: String) {
        val inputData = Data.Builder()
            .putString(StolenDeviceAlertWorker.PARAM_STOLEN_UUID, stolenUuid)
            .putString(StolenDeviceAlertWorker.PARAM_ALERT_ID, alertId)
            .putLong(StolenDeviceAlertWorker.PARAM_ALERT_TIMESTAMP, System.currentTimeMillis())
            .build()

        val alertWorkRequest = OneTimeWorkRequestBuilder<StolenDeviceAlertWorker>()
            .setInputData(inputData)
            .addTag("local_stolen_alert_$alertId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "local_stolen_alert_$stolenUuid",
                ExistingWorkPolicy.REPLACE,
                alertWorkRequest
            )
    }
}

/**
 * Gestionnaire pour simuler les alertes d'appareils volés (pour les tests).
 */
object StolenDeviceAlertManager {
    
    fun simulateAlert(context: Context, stolenUuid: String, alertId: String = "test_alert") {
        val intent = Intent(LocalStolenDeviceAlertReceiver.ACTION_STOLEN_DEVICE_ALERT).apply {
            putExtra(LocalStolenDeviceAlertReceiver.EXTRA_STOLEN_UUID, stolenUuid)
            putExtra(LocalStolenDeviceAlertReceiver.EXTRA_ALERT_ID, alertId)
        }
        
        context.sendBroadcast(intent)
    }
    
    fun registerForAlerts(context: Context): Boolean {
        // Dans une vraie implémentation, s'inscrire aux notifications FCM
        // et configurer les topics appropriés
        return try {
            // Configuration FCM simulée
            true
        } catch (e: Exception) {
            false
        }
    }
}

