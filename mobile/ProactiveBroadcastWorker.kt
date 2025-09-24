package com.ecoguard.app.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.*
import com.ecoguard.app.audio.PartitionParameters
import com.ecoguard.app.audio.PartitionProtocol
import com.ecoguard.app.audio.SmartFSKGenerator
import com.ecoguard.app.repository.LogRepository
import com.ecoguard.app.storage.SecurePartitionStorage
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.ecoguard.app.anonymization.SmartAnonymizationSystem
import java.util.concurrent.TimeUnit

/**
 * Worker responsable de l'émission proactive et continue de la partition ultrasonique unique de l'appareil.
 * Ce worker fonctionne indépendamment du statut "volé" de l'appareil et optimise la consommation de batterie
 * en adaptant les intervalles d'émission selon le niveau de charge et l'état de la batterie.
 */
class ProactiveBroadcastWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val fskGenerator: SmartFSKGenerator by inject()
    private val logRepository: LogRepository by inject()
    private val secureStorage: SecurePartitionStorage by inject()

    companion object {
        const val WORK_NAME = "EcoGuard_Proactive_Broadcast_Worker"
        const val PARAM_PRIVACY_LEVEL = "privacy_level"
        
        private const val DEFAULT_SAMPLE_RATE = 48000
        
        // Intervalles de veille adaptatifs (en secondes)
        private const val INTERVAL_CHARGING_HIGH_BATTERY = 15L // 15-20s
        private const val INTERVAL_HIGH_BATTERY = 37L // 30-45s  
        private const val INTERVAL_MEDIUM_BATTERY = 75L // 60-90s
        private const val INTERVAL_LOW_BATTERY = 300L // 5 minutes
        
        // Seuils de batterie
        private const val HIGH_BATTERY_THRESHOLD = 50
        private const val LOW_BATTERY_THRESHOLD = 20
        
        // Durée maximale d'une session d'émission (pour éviter les workers trop longs)
        private const val MAX_SESSION_DURATION_MINUTES = 30L
    }

    private var audioTrack: AudioTrack? = null
    private var isActive = false
    private var sessionStartTime = 0L
    private var emissionCount = 0

    override suspend fun doWork(): Result {
    try {
        sessionStartTime = System.currentTimeMillis()
        isActive = true
        emissionCount = 0
        
        try {
            // Charger les paramètres de partition
            val partitionParams = secureStorage.loadPartitionParameters()
            if (partitionParams == null || !partitionParams.isValid()) {
                logRepository.logError("ProactiveBroadcastWorker: Paramètres de partition manquants ou invalides")
                return Result.failure()
            }

            // Déterminer le niveau de privacy
            val privacyLevelName = inputData.getString(PARAM_PRIVACY_LEVEL) ?: "BALANCED"
            val privacyLevel = try {
                SmartAnonymizationSystem.PrivacyLevel.valueOf(privacyLevelName)
            } catch (e: Exception) {
                SmartAnonymizationSystem.PrivacyLevel.BALANCED
            }

            logRepository.logSystem(
                "ProactiveBroadcastWorker démarré",
                "UUID: ${partitionParams.ownerUUID.take(8)}..., Privacy: $privacyLevel"
            )

            // Générer le signal audio une seule fois pour cette session
            val generationResult = fskGenerator.generateSecurePacketAudio(
                partitionParams = partitionParams,
                privacyLevel = privacyLevel,
                sampleRate = DEFAULT_SAMPLE_RATE
            )

            // Créer et configurer AudioTrack
            audioTrack = createOptimizedAudioTrack(generationResult.audioData.size)
            audioTrack?.write(generationResult.audioData, 0, generationResult.audioData.size)

            logRepository.logSystem(
                "Signal FSK préparé pour émission proactive",
                "Taille: ${generationResult.audioData.size} échantillons, Durée: ${String.format("%.2f", generationResult.estimatedDuration)}s"
            )

            // Boucle principale d'émission proactive
            while (!isStopped && isActive && !isSessionExpired()) {
                try {
                    // Obtenir l'état de la batterie
                    val batteryInfo = getBatteryInfo()
                    
                    // Vérifier si on doit arrêter l'émission pour préserver la batterie
                    if (shouldStopForBattery(batteryInfo)) {
                        logRepository.logSystem(
                            "Arrêt émission pour préservation batterie",
                            "Niveau: ${batteryInfo.level}%"
                        )
                        break
                    }

                    // Émettre la rafale de paquets
                    val emissionSuccess = emitPacketBurst(generationResult, batteryInfo)
                    
                    if (emissionSuccess) {
                        emissionCount++
                        logRepository.logSystem(
                            "Émission proactive #$emissionCount réussie",
                            "Batterie: ${batteryInfo.level}%, En charge: ${batteryInfo.isCharging}"
                        )
                    } else {
                        logRepository.logError("Échec émission proactive #${emissionCount + 1}")
                    }

                    // Calculer l'intervalle de veille adaptatif
                    val sleepInterval = calculateAdaptiveSleepInterval(batteryInfo)
                    
                    logRepository.logSystem(
                        "Veille adaptative",
                        "Durée: ${sleepInterval}s, Prochaine émission dans ${sleepInterval}s"
                    )

                    // Veille adaptative
                    delay(sleepInterval * 1000)

                } catch (e: Exception) {
                    logRepository.logError("Erreur dans boucle émission proactive: ${e.message}")
                    delay(30000) // Attendre 30s avant de réessayer
                }
            }

            val sessionDuration = System.currentTimeMillis() - sessionStartTime
            logRepository.logSystem(
                "Session ProactiveBroadcastWorker terminée",
                "Émissions: $emissionCount, Durée: ${sessionDuration / 1000}s"
            )

            // Programmer la prochaine session
            scheduleNextSession(privacyLevel)

            return Result.success(createOutputData())
    } finally {
        cleanup()
    }

        } catch (e: Exception) {
            logRepository.logError("Erreur ProactiveBroadcastWorker: ${e.message}")
            return Result.retry()
        } finally {
            cleanup()
        }
    }

    /**
     * Émet une rafale de 3 paquets selon le protocole ESP.
     */
    private suspend fun emitPacketBurst(
        generationResult: SmartFSKGenerator.GenerationResult,
        batteryInfo: BatteryInfo
    ): Boolean {
        return try {
            repeat(PartitionProtocol.BURST_TRANSMISSION_COUNT) { transmissionIndex ->
                if (isStopped || !isActive) return false

                // Jouer le signal
                val playResult = playAudioSignal(generationResult)
                
                if (!playResult) {
                    return false
                }

                // Pause entre les transmissions (sauf pour la dernière)
                if (transmissionIndex < PartitionProtocol.BURST_TRANSMISSION_COUNT - 1) {
                    delay(PartitionProtocol.INTER_PACKET_SILENCE_MS)
                }
            }
            true
        } catch (e: Exception) {
            logRepository.logError("Erreur lors de l'émission de la rafale: ${e.message}")
            false
        }
    }

    /**
     * Joue le signal audio via AudioTrack.
     */
    private suspend fun playAudioSignal(generationResult: SmartFSKGenerator.GenerationResult): Boolean {
        return try {
            audioTrack?.let { track ->
                track.reloadStaticData()
                track.play()
                
                // Attendre la fin de la lecture (très courte, < 2 secondes)
                val playbackDurationMs = (generationResult.estimatedDuration * 1000).toLong() + 100
                delay(playbackDurationMs)
                
                track.pause()
                track.flush()
                
                true
            } ?: false
        } catch (e: Exception) {
            logRepository.logError("Erreur lecture audio proactive: ${e.message}")
            false
        }
    }

    /**
     * Calcule l'intervalle de veille adaptatif selon l'état de la batterie.
     */
    private fun calculateAdaptiveSleepInterval(batteryInfo: BatteryInfo): Long {
        return when {
            batteryInfo.level >= HIGH_BATTERY_THRESHOLD && batteryInfo.isCharging -> {
                INTERVAL_CHARGING_HIGH_BATTERY + (Math.random() * 4).toLong() // 15-20s
            }
            batteryInfo.level >= HIGH_BATTERY_THRESHOLD -> {
                INTERVAL_HIGH_BATTERY + (Math.random() * 15).toLong() // 30-45s
            }
            batteryInfo.level >= LOW_BATTERY_THRESHOLD -> {
                INTERVAL_MEDIUM_BATTERY + (Math.random() * 30).toLong() // 60-90s
            }
            else -> {
                INTERVAL_LOW_BATTERY // 5 minutes fixes
            }
        }
    }

    /**
     * Détermine si l'émission doit être arrêtée pour préserver la batterie.
     */
    private fun shouldStopForBattery(batteryInfo: BatteryInfo): Boolean {
        return batteryInfo.level < LOW_BATTERY_THRESHOLD && !batteryInfo.isCharging
    }

    /**
     * Vérifie si la session a expiré.
     */
    private fun isSessionExpired(): Boolean {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        return sessionDuration > (MAX_SESSION_DURATION_MINUTES * 60 * 1000)
    }

    /**
     * Obtient les informations sur la batterie.
     */
    private fun getBatteryInfo(): BatteryInfo {
        val batteryIntent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        
        val batteryPercentage = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            50 // Valeur par défaut si impossible de lire
        }
        
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        return BatteryInfo(batteryPercentage, isCharging)
    }

    /**
     * Crée et configure l'AudioTrack optimisé pour l'émission proactive.
     */
    private fun createOptimizedAudioTrack(bufferSize: Int): AudioTrack {
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(DEFAULT_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION) // Plus discret que ALARM
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize * 2) // Short = 2 bytes
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    /**
     * Programme la prochaine session d'émission proactive.
     */
    private fun scheduleNextSession(privacyLevel: SmartAnonymizationSystem.PrivacyLevel) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(false)
            .setRequiresStorageNotLow(true) // Peut émettre même si l'appareil est utilisé
            .build()

        val inputData = Data.Builder()
            .putString(PARAM_PRIVACY_LEVEL, privacyLevel.name)
            .build()

        val nextWorkRequest = OneTimeWorkRequestBuilder<ProactiveBroadcastWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setInitialDelay(1, TimeUnit.MINUTES) // Délai avant la prochaine session
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                nextWorkRequest
            )

        logRepository.logSystem("Prochaine session ProactiveBroadcastWorker programmée")
    }

    /**
     * Nettoie les ressources audio.
     */
    private fun cleanup() {
        isActive = false
        
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                logRepository.logError("Erreur nettoyage AudioTrack proactif: ${e.message}")
            }
        }
        audioTrack = null
        
        logRepository.logSystem("ProactiveBroadcastWorker: Ressources nettoyées")
    }

    /**
     * Crée les données de sortie du worker.
     */
    private fun createOutputData(): Data {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        
        return Data.Builder()
            .putInt("emission_count", emissionCount)
            .putLong("session_duration_ms", sessionDuration)
            .putLong("completion_time", System.currentTimeMillis())
            .putString("status", "completed")
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            2002, // Notification ID différent du BroadcastWorker
            createProactiveNotification()
        )
    }

    private fun createProactiveNotification(): android.app.Notification {
        val channelId = "ecoguard_proactive_broadcast"
        
        // Créer le canal de notification si nécessaire
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "EcoGuard Émission Proactive",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Émission continue de la partition ultrasonique unique"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("EcoGuard - Protection Active")
            .setContentText("Émission proactive de votre signature unique...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Informations sur l'état de la batterie.
     */
    private data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean
    ) {
        val isHighBattery: Boolean get() = level >= HIGH_BATTERY_THRESHOLD
        val isLowBattery: Boolean get() = level < LOW_BATTERY_THRESHOLD
        val isCriticalBattery: Boolean get() = level < 10
    }
}

/**
 * Gestionnaire pour démarrer et arrêter l'émission proactive.
 */
object ProactiveBroadcastManager {
    
    fun startProactiveBroadcast(
        context: Context,
        privacyLevel: SmartAnonymizationSystem.PrivacyLevel = SmartAnonymizationSystem.PrivacyLevel.BALANCED
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(ProactiveBroadcastWorker.PARAM_PRIVACY_LEVEL, privacyLevel.name)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ProactiveBroadcastWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(ProactiveBroadcastWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                ProactiveBroadcastWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }
    
    fun stopProactiveBroadcast(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ProactiveBroadcastWorker.WORK_NAME)
    }
    
    fun isProactiveBroadcastActive(context: Context): Boolean {
        // Cette méthode nécessiterait une implémentation asynchrone dans un vrai projet
        return true // Placeholder
    }
}

