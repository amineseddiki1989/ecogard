package com.ecoguard.app.workers

import android.content.Context
import androidx.work.*
import com.ecoguard.app.anonymization.SmartAnonymizationSystem
import com.ecoguard.app.repository.LogRepository
import com.ecoguard.app.repository.SmartNetworkRepository
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/**
 * Worker responsable de l'envoi des rapports anonymes lorsqu'un appareil volé est détecté.
 * Ce worker utilise le SmartAnonymizationSystem pour créer des rapports anonymes avec des fantômes
 * et les envoie au serveur de manière sécurisée et non corrélable.
 */
class AnonymousReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val logRepository: LogRepository by inject()
    private val networkRepository: SmartNetworkRepository by inject()
    private val anonymizationSystem: SmartAnonymizationSystem by inject()

    companion object {
        const val PARAM_PARTITION_UUID = "partition_uuid"
        const val PARAM_LATITUDE = "latitude"
        const val PARAM_LONGITUDE = "longitude"
        const val PARAM_CONFIDENCE = "confidence"
        const val PARAM_SIGHTING_TIMESTAMP = "sighting_timestamp"
        const val PARAM_ALERT_ID = "alert_id"
        const val PARAM_ALERT_TIMESTAMP = "alert_timestamp"
        const val PARAM_PRIVACY_LEVEL = "privacy_level"
        
        // Délais pour éviter la corrélation temporelle
        private const val MIN_DELAY_SECONDS = 30L
        private const val MAX_DELAY_SECONDS = 300L // 5 minutes
        
        // Nombre maximum de tentatives de rapport
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        
        try {
            // Extraire les paramètres
            val partitionUuid = inputData.getString(PARAM_PARTITION_UUID)
            val latitude = inputData.getDouble(PARAM_LATITUDE, 0.0)
            val longitude = inputData.getDouble(PARAM_LONGITUDE, 0.0)
            val confidence = inputData.getFloat(PARAM_CONFIDENCE, 0.0f)
            val sightingTimestamp = inputData.getLong(PARAM_SIGHTING_TIMESTAMP, 0L)
            val alertId = inputData.getString(PARAM_ALERT_ID) ?: "unknown"
            val alertTimestamp = inputData.getLong(PARAM_ALERT_TIMESTAMP, System.currentTimeMillis())
            
            val privacyLevelName = inputData.getString(PARAM_PRIVACY_LEVEL) ?: "HIGH"
            val privacyLevel = try {
                SmartAnonymizationSystem.PrivacyLevel.valueOf(privacyLevelName)
            } catch (e: Exception) {
                SmartAnonymizationSystem.PrivacyLevel.HIGH // Par défaut pour les rapports
            }

            if (partitionUuid.isNullOrBlank()) {
                logRepository.logError("AnonymousReportWorker: UUID de partition manquant")
                return Result.failure()
            }

            logRepository.logSystem(
                "Début rapport anonyme",
                "UUID: ${partitionUuid.take(8)}..., Alert: $alertId, Privacy: $privacyLevel"
            )

            // Délai aléatoire pour éviter la corrélation temporelle
            val randomDelay = Random.nextLong(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
            logRepository.logSystem("Délai anti-corrélation: ${randomDelay}s")
            delay(randomDelay * 1000)

            // Créer le rapport de base
            val baseReport = createBaseReport(
                partitionUuid = partitionUuid,
                latitude = latitude,
                longitude = longitude,
                confidence = confidence,
                sightingTimestamp = sightingTimestamp,
                alertId = alertId,
                alertTimestamp = alertTimestamp
            )

            // Générer les rapports anonymisés (avec fantômes)
            val anonymizedReports = anonymizationSystem.generateAnonymizedReports(
                realReport = baseReport,
                privacyLevel = privacyLevel
            )

            logRepository.logSystem(
                "Rapports anonymisés générés",
                "Nombre total: ${anonymizedReports.size} (1 réel + ${anonymizedReports.size - 1} fantômes)"
            )

            // Envoyer tous les rapports (réel + fantômes) dans un ordre aléatoire
            val reportResults = sendAnonymizedReports(anonymizedReports)
            
            val successCount = reportResults.count { it }
            val totalReports = reportResults.size
            
            val processingTime = System.currentTimeMillis() - startTime

            if (successCount > 0) {
                logRepository.logSystem(
                    "Rapport anonyme terminé avec succès",
                    "Envoyés: $successCount/$totalReports, Temps: ${processingTime}ms"
                )
                
                return Result.success(createSuccessOutputData(
                    alertId = alertId,
                    reportsCount = totalReports,
                    successCount = successCount,
                    processingTime = processingTime
                ))
            } else {
                logRepository.logError("Échec envoi de tous les rapports anonymes")
                return Result.retry()
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur AnonymousReportWorker: ${e.message}")
            return Result.failure()
        }
    }

    private fun createBaseReport(
        partitionUuid: String,
        latitude: Double,
        longitude: Double,
        confidence: Float,
        sightingTimestamp: Long,
        alertId: String,
        alertTimestamp: Long
    ): AnonymousReport {
        return AnonymousReport(
            partitionUuid = partitionUuid,
            latitude = latitude,
            longitude = longitude,
            confidence = confidence,
            sightingTimestamp = sightingTimestamp,
            reportTimestamp = System.currentTimeMillis(),
            alertId = alertId,
            alertTimestamp = alertTimestamp,
            reporterFingerprint = generateReporterFingerprint(),
            isGhost = false
        )
    }

    private suspend fun sendAnonymizedReports(reports: List<AnonymousReport>): List<Boolean> {
        val results = mutableListOf<Boolean>()
        
        // Mélanger l'ordre des rapports pour éviter les patterns
        val shuffledReports = reports.shuffled()
        
        for ((index, report) in shuffledReports.withIndex()) {
            try {
                // Délai entre les rapports pour éviter la détection de burst
                if (index > 0) {
                    val interReportDelay = Random.nextLong(5, 30) // 5-30 secondes
                    delay(interReportDelay * 1000)
                }
                
                val success = sendSingleReport(report)
                results.add(success)
                
                if (success) {
                    val reportType = if (report.isGhost) "fantôme" else "réel"
                    logRepository.logSystem(
                        "Rapport $reportType envoyé",
                        "Index: ${index + 1}/${shuffledReports.size}"
                    )
                } else {
                    logRepository.logError("Échec envoi rapport ${index + 1}")
                }
                
            } catch (e: Exception) {
                logRepository.logError("Erreur envoi rapport ${index + 1}: ${e.message}")
                results.add(false)
            }
        }
        
        return results
    }

    private suspend fun sendSingleReport(report: AnonymousReport): Boolean {
        return try {
            // Convertir le rapport en format JSON pour l'API
            val reportJson = convertReportToJson(report)
            
            // Envoyer via le repository réseau
            val response = networkRepository.sendAnonymousReport(reportJson)
            
            response.isSuccessful
            
        } catch (e: Exception) {
            logRepository.logError("Erreur envoi rapport individuel: ${e.message}")
            false
        }
    }

    private fun convertReportToJson(report: AnonymousReport): Map<String, Any> {
        return mapOf(
            "partition_uuid" to report.partitionUuid,
            "latitude" to report.latitude,
            "longitude" to report.longitude,
            "confidence" to report.confidence,
            "sighting_timestamp" to report.sightingTimestamp,
            "report_timestamp" to report.reportTimestamp,
            "alert_id" to report.alertId,
            "alert_timestamp" to report.alertTimestamp,
            "reporter_fingerprint" to report.reporterFingerprint,
            "metadata" to mapOf(
                "app_version" to "2.0.0",
                "protocol_version" to "ESP_2.0",
                "anonymization_level" to "HIGH"
            )
        )
    }

    private fun generateReporterFingerprint(): String {
        // Générer une empreinte anonyme du rapporteur
        // Dans une vraie implémentation, ceci serait basé sur des caractéristiques non-identifiantes
        val timestamp = System.currentTimeMillis()
        val randomComponent = Random.nextInt(10000, 99999)
        
        return "reporter_${timestamp.toString().takeLast(8)}_$randomComponent"
    }

    private fun createSuccessOutputData(
        alertId: String,
        reportsCount: Int,
        successCount: Int,
        processingTime: Long
    ): Data {
        return Data.Builder()
            .putString("status", "success")
            .putString("alert_id", alertId)
            .putInt("reports_count", reportsCount)
            .putInt("success_count", successCount)
            .putLong("processing_time_ms", processingTime)
            .putLong("completion_time", System.currentTimeMillis())
            .putFloat("success_rate", successCount.toFloat() / reportsCount)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            2003, // Notification ID unique
            createReportNotification()
        )
    }

    private fun createReportNotification(): android.app.Notification {
        val channelId = "ecoguard_anonymous_report"
        
        // Créer le canal de notification si nécessaire
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "EcoGuard Rapports Anonymes",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Envoi de rapports anonymes d'appareils détectés"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("EcoGuard - Rapport en cours")
            .setContentText("Envoi sécurisé d'un rapport anonyme...")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Structure d'un rapport anonyme.
 */
data class AnonymousReport(
    val partitionUuid: String,
    val latitude: Double,
    val longitude: Double,
    val confidence: Float,
    val sightingTimestamp: Long,
    val reportTimestamp: Long,
    val alertId: String,
    val alertTimestamp: Long,
    val reporterFingerprint: String,
    val isGhost: Boolean = false
) {
    val sightingAge: Long get() = reportTimestamp - sightingTimestamp
    val alertResponseTime: Long get() = reportTimestamp - alertTimestamp
    val isTimely: Boolean get() = sightingAge < (24 * 60 * 60 * 1000) // Moins de 24h
    val isHighConfidence: Boolean get() = confidence > 0.8f
}

/**
 * Résultat de l'envoi d'un rapport.
 */
data class ReportResult(
    val success: Boolean,
    val httpCode: Int? = null,
    val errorMessage: String? = null,
    val responseTime: Long = 0
) {
    val isRetryable: Boolean get() = httpCode in 500..599 || httpCode == 429 // Erreurs serveur ou rate limiting
}

