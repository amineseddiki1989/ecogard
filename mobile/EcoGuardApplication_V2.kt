package com.ecoguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.*
import com.ecoguard.app.audio.OptimizedAudioEngine_V2
import com.ecoguard.app.repository.*
import com.ecoguard.app.storage.SecurePartitionStorage
import com.ecoguard.app.workers.ProactiveBroadcastWorker
import com.ecoguard.app.workers.ProactiveBroadcastManager
import com.ecoguard.app.anonymization.SmartAnonymizationSystem
import com.ecoguard.app.location.LocationProvider
import com.ecoguard.app.receivers.StolenDeviceAlertManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Classe Application principale pour EcoGuard Version 2.0 - Stratégie Proactive.
 * Initialise tous les composants nécessaires pour l'émission proactive, l'écoute passive,
 * et la gestion des alertes d'appareils volés.
 */
class EcoGuardApplication_V2 : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        const val APP_VERSION = "2.0.0"
        const val PROTOCOL_VERSION = "ESP_2.0"
        
        // Canaux de notification
        const val CHANNEL_PROACTIVE_BROADCAST = "ecoguard_proactive_broadcast"
        const val CHANNEL_PASSIVE_LISTENING = "ecoguard_passive_listening"
        const val CHANNEL_ANONYMOUS_REPORTS = "ecoguard_anonymous_reports"
        const val CHANNEL_ALERTS = "ecoguard_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialiser Koin pour l'injection de dépendances
        initializeKoin()
        
        // Créer les canaux de notification
        createNotificationChannels()
        
        // Configurer WorkManager
        configureWorkManager()
        
        // Initialiser les composants principaux
        applicationScope.launch {
            initializeEcoGuardComponents()
        }
        
        // Enregistrer pour les alertes FCM
        registerForCommunityAlerts()
    }

    private fun initializeKoin() {
        startKoin {
            androidContext(this@EcoGuardApplication_V2)
            modules(ecoGuardModules)
        }
    }

    private val ecoGuardModules = module {
        // Repositories
        single { LogRepository() }
        single { 
            SightingRepository(
                sightingDao = SightingDatabase.getDatabase(get()).sightingDao(),
                logRepository = get()
            )
        }
        single { SmartNetworkRepository(get()) }
        
        // Storage
        single { SecurePartitionStorage(get(), get()) }
        
        // Location
        single { LocationProvider(get()) }
        
        // Audio Engine V2
        single { 
            OptimizedAudioEngine_V2(
                context = get(),
                logRepository = get(),
                sightingRepository = get(),
                locationProvider = get()
            )
        }
        
        // Anonymization System
        single { SmartAnonymizationSystem(get()) }
        
        // API Clients
        single { com.ecoguard.app.api.WebPortalAPI(logRepository = get()) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Canal pour l'émission proactive
            val proactiveChannel = NotificationChannel(
                CHANNEL_PROACTIVE_BROADCAST,
                "Émission Proactive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Émission continue de votre signature ultrasonique unique"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            // Canal pour l'écoute passive
            val passiveChannel = NotificationChannel(
                CHANNEL_PASSIVE_LISTENING,
                "Écoute Passive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Écoute et enregistrement des signatures d'autres appareils"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            // Canal pour les rapports anonymes
            val reportsChannel = NotificationChannel(
                CHANNEL_ANONYMOUS_REPORTS,
                "Rapports Anonymes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Envoi de rapports anonymes d'appareils détectés"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            // Canal pour les alertes importantes
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Alertes EcoGuard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes importantes concernant votre appareil ou la communauté"
                setShowBadge(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannels(listOf(
                proactiveChannel,
                passiveChannel,
                reportsChannel,
                alertsChannel
            ))
        }
    }

    private fun configureWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setMaxSchedulerLimit(20)
            .build()
        
        WorkManager.initialize(this, config)
    }

    private suspend fun initializeEcoGuardComponents() {
        try {
            // Vérifier si l'appareil est configuré
            val secureStorage = SecurePartitionStorage(this, LogRepository())
            val partitionParams = secureStorage.loadPartitionParameters()
            
            if (partitionParams == null || !partitionParams.isValid()) {
                // L'appareil n'est pas encore configuré - démarrer l'onboarding
                startOnboardingProcess()
            } else {
                // L'appareil est configuré - démarrer les services principaux
                startMainServices()
            }
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur initialisation composants: ${e.message}")
        }
    }

    private suspend fun startOnboardingProcess() {
        LogRepository().logSystem("Démarrage processus d'onboarding")
        
        // Dans une vraie implémentation, ceci démarrerait l'activité d'onboarding
        // Pour cette simulation, on assume que l'onboarding est terminé
    }

    private suspend fun startMainServices() {
        val logRepository = LogRepository()
        
        try {
            logRepository.logSystem(
                "Démarrage services principaux EcoGuard V2",
                "Version: $APP_VERSION, Protocole: $PROTOCOL_VERSION"
            )
            
            // 1. Démarrer l'écoute passive
            startPassiveListening()
            
            // 2. Démarrer l'émission proactive
            startProactiveBroadcast()
            
            // 3. Programmer la maintenance périodique
            schedulePeriodicMaintenance()
            
            logRepository.logSystem("Tous les services EcoGuard V2 sont actifs")
            
        } catch (e: Exception) {
            logRepository.logError("Erreur démarrage services: ${e.message}")
        }
    }

    private suspend fun startPassiveListening() {
        try {
            val audioEngine = OptimizedAudioEngine_V2(
                context = this,
                logRepository = LogRepository(),
                sightingRepository = SightingRepository(
                    SightingDatabase.getDatabase(this).sightingDao(),
                    LogRepository()
                ),
                locationProvider = LocationProvider(this)
            )
            
            audioEngine.startPassiveListening()
            
            LogRepository().logSystem("Écoute passive démarrée")
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur démarrage écoute passive: ${e.message}")
        }
    }

    private fun startProactiveBroadcast() {
        try {
            ProactiveBroadcastManager.startProactiveBroadcast(
                context = this,
                privacyLevel = SmartAnonymizationSystem.PrivacyLevel.BALANCED
            )
            
            LogRepository().logSystem("Émission proactive démarrée")
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur démarrage émission proactive: ${e.message}")
        }
    }

    private fun schedulePeriodicMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val maintenanceRequest = PeriodicWorkRequestBuilder<MaintenanceWorker>(
            repeatInterval = 6, // Toutes les 6 heures
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
        .setConstraints(constraints)
        .addTag("ecoguard_maintenance")
        .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "ecoguard_maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                maintenanceRequest
            )

        LogRepository().logSystem("Maintenance périodique programmée")
    }

    private fun registerForCommunityAlerts() {
        try {
            val registered = StolenDeviceAlertManager.registerForAlerts(this)
            
            if (registered) {
                LogRepository().logSystem("Inscription aux alertes communauté réussie")
            } else {
                LogRepository().logError("Échec inscription aux alertes communauté")
            }
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur inscription alertes: ${e.message}")
        }
    }

    /**
     * Arrête tous les services EcoGuard (pour les tests ou la désinstallation).
     */
    fun stopAllServices() {
        applicationScope.launch {
            try {
                // Arrêter l'émission proactive
                ProactiveBroadcastManager.stopProactiveBroadcast(this@EcoGuardApplication_V2)
                
                // Arrêter l'écoute passive
                val audioEngine = OptimizedAudioEngine_V2(
                    context = this@EcoGuardApplication_V2,
                    logRepository = LogRepository(),
                    sightingRepository = SightingRepository(
                        SightingDatabase.getDatabase(this@EcoGuardApplication_V2).sightingDao(),
                        LogRepository()
                    ),
                    locationProvider = LocationProvider(this@EcoGuardApplication_V2)
                )
                audioEngine.stopPassiveListening()
                
                // Annuler tous les workers
                WorkManager.getInstance(this@EcoGuardApplication_V2).cancelAllWork()
                
                LogRepository().logSystem("Tous les services EcoGuard arrêtés")
                
            } catch (e: Exception) {
                LogRepository().logError("Erreur arrêt services: ${e.message}")
            }
        }
    }

    /**
     * Obtient les statistiques globales de l'application.
     */
    suspend fun getApplicationStats(): ApplicationStats {
        return try {
            val sightingRepository = SightingRepository(
                SightingDatabase.getDatabase(this).sightingDao(),
                LogRepository()
            )
            
            val sightingStats = sightingRepository.getSightingStats()
            val proactiveActive = ProactiveBroadcastManager.isProactiveBroadcastActive(this)
            
            ApplicationStats(
                appVersion = APP_VERSION,
                protocolVersion = PROTOCOL_VERSION,
                isProactiveActive = proactiveActive,
                isPassiveActive = true, // Simplifié pour cette démo
                totalSightings = sightingStats.totalSightings,
                recentSightings = sightingStats.recentSightings,
                reportedSightings = sightingStats.reportedSightings,
                uniquePartitions = sightingStats.uniquePartitions,
                uptime = System.currentTimeMillis() // Simplifié
            )
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur récupération statistiques: ${e.message}")
            ApplicationStats()
        }
    }
}

/**
 * Worker de maintenance périodique.
 */
class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val logRepository = LogRepository()
            logRepository.logSystem("Début maintenance périodique")
            
            // Nettoyer les anciennes observations
            val sightingRepository = SightingRepository(
                SightingDatabase.getDatabase(applicationContext).sightingDao(),
                logRepository
            )
            
            val deletedCount = sightingRepository.cleanupOldSightings(maxAgeHours = 168) // 7 jours
            
            // Nettoyer les logs anciens (si implémenté)
            // logRepository.cleanupOldLogs()
            
            logRepository.logSystem(
                "Maintenance terminée",
                "Observations supprimées: $deletedCount"
            )
            
            Result.success()
            
        } catch (e: Exception) {
            LogRepository().logError("Erreur maintenance: ${e.message}")
            Result.failure()
        }
    }
}

/**
 * Statistiques globales de l'application.
 */
data class ApplicationStats(
    val appVersion: String = "2.0.0",
    val protocolVersion: String = "ESP_2.0",
    val isProactiveActive: Boolean = false,
    val isPassiveActive: Boolean = false,
    val totalSightings: Int = 0,
    val recentSightings: Int = 0,
    val reportedSightings: Int = 0,
    val uniquePartitions: Int = 0,
    val uptime: Long = 0L
) {
    val reportingRate: Float get() = if (totalSightings > 0) reportedSightings.toFloat() / totalSightings else 0f
    val averageSightingsPerPartition: Float get() = if (uniquePartitions > 0) totalSightings.toFloat() / uniquePartitions else 0f
    val uptimeHours: Long get() = uptime / (1000 * 60 * 60)
}

