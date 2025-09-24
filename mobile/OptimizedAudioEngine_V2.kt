package com.ecoguard.app.audio

import android.content.Context
import android.location.Location
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat
import com.ecoguard.app.repository.LogRepository
import com.ecoguard.app.repository.SightingRepository
import com.ecoguard.app.location.LocationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Version 2 du moteur audio optimisé pour l'écoute passive et l'enregistrement local.
 * Ce moteur écoute en permanence toutes les partitions EcoGuard et les enregistre localement
 * dans le SightingRepository, sans déclencher de rapports immédiats.
 */
class OptimizedAudioEngine_V2(
    private val context: Context,
    private val logRepository: LogRepository,
    private val sightingRepository: SightingRepository,
    private val locationProvider: LocationProvider
) {
    
    data class OptimizedConfig(
        val sampleRate: Int = 48000,
        val bufferSize: Int = 4096,
        val fftSize: Int = 2048,
        val overlapRatio: Float = 0.5f,
        val frequencyResolution: Float = 23.4375f, // 48000 / 2048
        val detectionThreshold: Float = 0.6f,
        val minConfidenceForRecording: Float = 0.7f
    )

    private val config = OptimizedConfig()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    
    private val _engineState = MutableStateFlow(AudioEngineState.STOPPED)
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()
    
    private val _detectionStats = MutableStateFlow(PassiveDetectionStats())
    val detectionStats: StateFlow<PassiveDetectionStats> = _detectionStats.asStateFlow()

    enum class AudioEngineState { 
        STOPPED, 
        STARTING, 
        LISTENING, 
        ANALYZING, 
        RECORDING_SIGHTING,
        ERROR 
    }

    /**
     * Démarre le moteur audio pour l'écoute passive.
     */
    suspend fun startPassiveListening() {
        if (_engineState.value != AudioEngineState.STOPPED) return

        try {
            _engineState.value = AudioEngineState.STARTING
            
            initializeAudioComponents()
            startPassiveDetectionLoop()
            
            _engineState.value = AudioEngineState.LISTENING
            logRepository.logSystem("Moteur audio passif démarré - Écoute de toutes les partitions EcoGuard")
            
        } catch (e: Exception) {
            _engineState.value = AudioEngineState.ERROR
            logRepository.logError("Erreur moteur audio passif: ${e.message}")
        }
    }

    /**
     * Arrête le moteur audio passif.
     */
    suspend fun stopPassiveListening() {
        _engineState.value = AudioEngineState.STOPPED
        isListening = false
        scope.coroutineContext.cancelChildren()
        
        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            release()
        }
        audioRecord = null
        
        logRepository.logSystem("Moteur audio passif arrêté")
    }

    private fun initializeAudioComponents() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        val bufferSize = maxOf(config.bufferSize, minBufferSize)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun startPassiveDetectionLoop() {
        scope.launch {
            isListening = true
            audioRecord?.startRecording()
            
            val buffer = ShortArray(config.bufferSize)
            val fftBuffer = DoubleArray(config.fftSize)
            val overlapBuffer = DoubleArray((config.fftSize * config.overlapRatio).toInt())
            
            var totalSamplesProcessed = 0L
            var detectionAttempts = 0
            var successfulDetections = 0
            
            while (isListening && _engineState.value != AudioEngineState.STOPPED) {
                try {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        totalSamplesProcessed += bytesRead
                        detectionAttempts++
                        
                        _engineState.value = AudioEngineState.ANALYZING
                        
                        val detectionResult = processAudioForAllPartitions(buffer, bytesRead, fftBuffer, overlapBuffer)
                        
                        if (detectionResult != null) {
                            successfulDetections++
                            _engineState.value = AudioEngineState.RECORDING_SIGHTING
                            
                            // Enregistrer l'observation localement
                            recordSightingLocally(detectionResult)
                        }
                        
                        _engineState.value = AudioEngineState.LISTENING
                        
                        // Mettre à jour les statistiques
                        updateDetectionStats(totalSamplesProcessed, detectionAttempts, successfulDetections)
                    }
                    
                    delay(10) // Pause courte pour éviter la surcharge CPU
                    
                } catch (e: Exception) {
                    logRepository.logError("Erreur traitement audio passif: ${e.message}")
                    delay(1000) // Pause plus longue en cas d'erreur
                }
            }
        }
    }

    private suspend fun processAudioForAllPartitions(
        buffer: ShortArray, 
        length: Int,
        fftBuffer: DoubleArray,
        overlapBuffer: DoubleArray
    ): PartitionDetectionResult? {
        try {
            // 1. Convertir en float et appliquer une fenêtre
            val floatBuffer = FloatArray(length) { buffer[it].toFloat() / Short.MAX_VALUE }
            val windowedBuffer = applyHammingWindow(floatBuffer)
            
            // 2. Préparer pour FFT (avec overlap)
            prepareFFTBuffer(windowedBuffer, fftBuffer, overlapBuffer)
            
            // 3. Effectuer FFT
            val spectrum = performFFT(fftBuffer)
            
            // 4. Analyser le spectre pour détecter toute partition EcoGuard valide
            return analyzeSpectrumForAnyPartition(spectrum)
            
        } catch (e: Exception) {
            logRepository.logError("Erreur analyse FFT passive: ${e.message}")
            return null
        }
    }

    private fun analyzeSpectrumForAnyPartition(spectrum: DoubleArray): PartitionDetectionResult? {
        // Rechercher des signaux FSK dans la plage de fréquences EcoGuard
        val minBin = (PartitionProtocol.MIN_FREQUENCY * spectrum.size / (config.sampleRate / 2)).toInt()
        val maxBin = (PartitionProtocol.MAX_FREQUENCY * spectrum.size / (config.sampleRate / 2)).toInt()
        
        var bestDetection: PartitionDetectionResult? = null
        var bestScore = 0.0
        
        // Balayer la plage de fréquences pour détecter des paires mark/space
        for (centerBin in minBin..maxBin step 10) {
            val detectionResult = analyzeFrequencyPair(spectrum, centerBin)
            
            if (detectionResult != null && detectionResult.confidence > config.minConfidenceForRecording) {
                val score = detectionResult.confidence * detectionResult.snr
                
                if (score > bestScore) {
                    bestScore = score
                    bestDetection = detectionResult
                }
            }
        }
        
        return bestDetection
    }

    private fun analyzeFrequencyPair(spectrum: DoubleArray, centerBin: Int): PartitionDetectionResult? {
        // Estimer les fréquences mark et space autour du centre
        val bandwidthBins = (PartitionProtocol.STANDARD_BANDWIDTH * spectrum.size / (config.sampleRate / 2)).toInt()
        val markBin = centerBin + bandwidthBins / 2
        val spaceBin = centerBin - bandwidthBins / 2
        
        if (markBin >= spectrum.size || spaceBin < 0) return null
        
        // Analyser l'énergie aux fréquences estimées
        val markEnergy = getEnergyAroundBin(spectrum, markBin, 2)
        val spaceEnergy = getEnergyAroundBin(spectrum, spaceBin, 2)
        val backgroundNoise = estimateBackgroundNoise(spectrum, markBin, spaceBin)
        
        // Calculer le SNR
        val markSNR = if (backgroundNoise > 0) markEnergy / backgroundNoise else 0.0
        val spaceSNR = if (backgroundNoise > 0) spaceEnergy / backgroundNoise else 0.0
        val maxSNR = maxOf(markSNR, spaceSNR)
        
        // Vérifier si c'est un signal FSK valide
        if (maxSNR > config.detectionThreshold) {
            // Calculer les fréquences réelles
            val markFreq = (markBin * config.sampleRate / 2) / spectrum.size
            val spaceFreq = (spaceBin * config.sampleRate / 2) / spectrum.size
            
            // Générer un UUID estimé basé sur les fréquences (approximation)
            val estimatedUUID = generateEstimatedUUID(markFreq, spaceFreq)
            
            val confidence = calculateDetectionConfidence(markSNR, spaceSNR, backgroundNoise)
            
            return PartitionDetectionResult(
                estimatedUUID = estimatedUUID,
                markFrequency = markFreq,
                spaceFrequency = spaceFreq,
                markEnergy = markEnergy,
                spaceEnergy = spaceEnergy,
                snr = maxSNR.toFloat(),
                confidence = confidence,
                timestamp = System.currentTimeMillis()
            )
        }
        
        return null
    }

    private fun generateEstimatedUUID(markFreq: Int, spaceFreq: Int): String {
        // Générer un UUID basé sur les fréquences détectées
        // Dans une vraie implémentation, ceci pourrait être plus sophistiqué
        val freqHash = (markFreq * 1000 + spaceFreq).toString()
        return "estimated-${freqHash.hashCode().toString().replace("-", "")}"
    }

    private suspend fun recordSightingLocally(detection: PartitionDetectionResult) {
        try {
            // Obtenir la position actuelle
            val location = locationProvider.getCurrentLocation()
            
            if (location != null) {
                // Enregistrer l'observation dans le repository local
                sightingRepository.recordSighting(
                    partitionUuid = detection.estimatedUUID,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    confidence = detection.confidence
                )
                
                logRepository.logSystem(
                    "Observation enregistrée localement",
                    "UUID estimé: ${detection.estimatedUUID.take(12)}..., Confiance: ${String.format("%.2f", detection.confidence)}, SNR: ${String.format("%.1f", detection.snr)}"
                )
            } else {
                logRepository.logSystem(
                    "Observation détectée mais position indisponible",
                    "UUID estimé: ${detection.estimatedUUID.take(12)}..."
                )
            }
            
        } catch (e: Exception) {
            logRepository.logError("Erreur enregistrement observation: ${e.message}")
        }
    }

    private fun applyHammingWindow(data: FloatArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val window = 0.54 - 0.46 * cos(2.0 * PI * i / (data.size - 1))
            windowed[i] = (data[i] * window).toFloat()
        }
        return windowed
    }

    private fun prepareFFTBuffer(
        audioData: FloatArray, 
        fftBuffer: DoubleArray, 
        overlapBuffer: DoubleArray
    ) {
        // Copier l'overlap du buffer précédent
        System.arraycopy(overlapBuffer, 0, fftBuffer, 0, overlapBuffer.size)
        
        // Ajouter les nouvelles données
        val newDataSize = minOf(audioData.size, fftBuffer.size - overlapBuffer.size)
        for (i in 0 until newDataSize) {
            fftBuffer[overlapBuffer.size + i] = audioData[i].toDouble()
        }
        
        // Sauvegarder pour le prochain overlap
        val overlapStart = fftBuffer.size - overlapBuffer.size
        System.arraycopy(fftBuffer, overlapStart, overlapBuffer, 0, overlapBuffer.size)
    }

    private fun performFFT(data: DoubleArray): DoubleArray {
        // Simulation d'une FFT - dans une vraie implémentation, utiliser une bibliothèque FFT
        val spectrum = DoubleArray(data.size / 2)
        
        for (k in spectrum.indices) {
            var real = 0.0
            var imag = 0.0
            
            for (n in data.indices) {
                val angle = -2.0 * PI * k * n / data.size
                real += data[n] * cos(angle)
                imag += data[n] * sin(angle)
            }
            
            spectrum[k] = sqrt(real * real + imag * imag)
        }
        
        return spectrum
    }

    private fun getEnergyAroundBin(spectrum: DoubleArray, centerBin: Int, radius: Int): Double {
        val startBin = maxOf(0, centerBin - radius)
        val endBin = minOf(spectrum.size - 1, centerBin + radius)
        
        var energy = 0.0
        for (i in startBin..endBin) {
            energy += spectrum[i]
        }
        
        return energy / (endBin - startBin + 1)
    }

    private fun estimateBackgroundNoise(spectrum: DoubleArray, excludeBin1: Int, excludeBin2: Int): Double {
        var noiseSum = 0.0
        var noiseCount = 0
        
        for (i in spectrum.indices) {
            if (abs(i - excludeBin1) > 5 && abs(i - excludeBin2) > 5) {
                noiseSum += spectrum[i]
                noiseCount++
            }
        }
        
        return if (noiseCount > 0) noiseSum / noiseCount else 1.0
    }

    private fun calculateDetectionConfidence(markSNR: Double, spaceSNR: Double, noise: Double): Float {
        val snrDifference = abs(markSNR - spaceSNR)
        val maxSNR = maxOf(markSNR, spaceSNR)
        
        // Confiance basée sur le SNR et la différence entre mark et space
        val snrScore = (maxSNR / 10.0).coerceAtMost(1.0)
        val differenceScore = (snrDifference / 5.0).coerceAtMost(1.0)
        val noiseScore = (1.0 / (1.0 + noise)).coerceAtMost(1.0)
        
        return ((snrScore * 0.5 + differenceScore * 0.3 + noiseScore * 0.2)).toFloat()
    }

    private fun updateDetectionStats(totalSamples: Long, attempts: Int, successes: Int) {
        val currentStats = _detectionStats.value
        
        _detectionStats.value = currentStats.copy(
            totalSamplesProcessed = totalSamples,
            detectionAttempts = attempts,
            successfulDetections = successes,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Obtient les statistiques de détection passive.
     */
    fun getDetectionStats(): PassiveDetectionStats = _detectionStats.value

    /**
     * Nettoie périodiquement les anciennes observations.
     */
    suspend fun performMaintenance() {
        try {
            val deletedCount = sightingRepository.cleanupOldSightings(maxAgeHours = 168) // 7 jours
            
            if (deletedCount > 0) {
                logRepository.logSystem(
                    "Maintenance observations terminée",
                    "Supprimées: $deletedCount observations anciennes"
                )
            }
            
        } catch (e: Exception) {
            logRepository.logError("Erreur maintenance observations: ${e.message}")
        }
    }

    /**
     * Résultat de détection d'une partition.
     */
    data class PartitionDetectionResult(
        val estimatedUUID: String,
        val markFrequency: Int,
        val spaceFrequency: Int,
        val markEnergy: Double,
        val spaceEnergy: Double,
        val snr: Float,
        val confidence: Float,
        val timestamp: Long
    ) {
        val centerFrequency: Int get() = (markFrequency + spaceFrequency) / 2
        val bandwidth: Int get() = abs(markFrequency - spaceFrequency)
        val isHighQuality: Boolean get() = confidence > 0.8f && snr > 3.0f
    }

    /**
     * Statistiques de détection passive.
     */
    data class PassiveDetectionStats(
        val totalSamplesProcessed: Long = 0,
        val detectionAttempts: Int = 0,
        val successfulDetections: Int = 0,
        val lastUpdateTime: Long = System.currentTimeMillis()
    ) {
        val detectionRate: Float get() = if (detectionAttempts > 0) successfulDetections.toFloat() / detectionAttempts else 0f
        val samplesPerSecond: Float get() = if (lastUpdateTime > 0) totalSamplesProcessed.toFloat() / ((lastUpdateTime - 0) / 1000f) else 0f
        val averageDetectionsPerMinute: Float get() = if (lastUpdateTime > 0) successfulDetections * 60000f / (lastUpdateTime - 0) else 0f
    }
}

