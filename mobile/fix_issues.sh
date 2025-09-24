#!/bin/bash

# Script de correction des problèmes identifiés dans l'application mobile EcoGuard V2
# Ce script applique les correctifs recommandés dans le rapport de débogage

echo "=== Script de correction des problèmes EcoGuard Mobile ==="
echo "Date: $(date)"
echo ""

# Fonction pour afficher les messages d'information
info() {
    echo -e "\e[1;34m[INFO]\e[0m $1"
}

# Fonction pour afficher les messages de succès
success() {
    echo -e "\e[1;32m[SUCCESS]\e[0m $1"
}

# Fonction pour afficher les messages d'erreur
error() {
    echo -e "\e[1;31m[ERROR]\e[0m $1"
}

# Vérifier que les fichiers existent
info "Vérification des fichiers source..."
if [ ! -f "ProactiveBroadcastWorker.kt" ]; then
    error "Fichier ProactiveBroadcastWorker.kt non trouvé"
    exit 1
fi

if [ ! -f "OptimizedAudioEngine_V2.kt" ]; then
    error "Fichier OptimizedAudioEngine_V2.kt non trouvé"
    exit 1
fi

if [ ! -f "SightingRepository.kt" ]; then
    error "Fichier SightingRepository.kt non trouvé"
    exit 1
fi

success "Tous les fichiers source sont présents"

# Créer des copies de sauvegarde
info "Création des copies de sauvegarde..."
cp ProactiveBroadcastWorker.kt ProactiveBroadcastWorker.kt.bak
cp OptimizedAudioEngine_V2.kt OptimizedAudioEngine_V2.kt.bak
cp SightingRepository.kt SightingRepository.kt.bak
success "Copies de sauvegarde créées"

# Correction 1.1: Fuite de ressources dans AudioTrack
info "Correction de la fuite de ressources dans AudioTrack..."
sed -i 's/override suspend fun doWork(): Result {/override suspend fun doWork(): Result {\n    try {/' ProactiveBroadcastWorker.kt
sed -i 's/return Result.success(createOutputData())/return Result.success(createOutputData())\n    } finally {\n        cleanup()\n    }/' ProactiveBroadcastWorker.kt
success "Correction de la fuite de ressources appliquée"

# Correction 1.2: Calcul incorrect des intervalles adaptatifs
info "Correction des intervalles adaptatifs..."
sed -i 's/private const val INTERVAL_CHARGING_HIGH_BATTERY = 18L/private const val INTERVAL_CHARGING_HIGH_BATTERY = 15L/' ProactiveBroadcastWorker.kt
success "Correction des intervalles adaptatifs appliquée"

# Correction 2.1: Implémentation FFT incomplète
info "Ajout de la dépendance JTransforms dans build.gradle..."
echo "// Ajout de la dépendance JTransforms pour FFT optimisée" >> build_v2.gradle
echo "implementation 'com.github.wendykierp:JTransforms:3.1'" >> build_v2.gradle
success "Dépendance JTransforms ajoutée"

info "Remplacement de l'implémentation FFT..."
cat > fft_replacement.txt << 'EOF'
private fun performFFT(data: DoubleArray): DoubleArray {
    // Utilisation de JTransforms pour une FFT optimisée
    val fft = org.jtransforms.fft.DoubleFFT_1D(data.size.toLong())
    val fftData = DoubleArray(data.size * 2)
    
    // Copier les données d'entrée
    System.arraycopy(data, 0, fftData, 0, data.size)
    
    // Calculer la FFT
    fft.realForwardFull(fftData)
    
    // Calculer le spectre de puissance
    val spectrum = DoubleArray(data.size / 2)
    for (i in spectrum.indices) {
        val real = fftData[2 * i]
        val imag = fftData[2 * i + 1]
        spectrum[i] = sqrt(real * real + imag * imag)
    }
    
    return spectrum
}
EOF

# Remplacer l'implémentation FFT
sed -i '/private fun performFFT/,/^    }/c\    '"$(cat fft_replacement.txt)" OptimizedAudioEngine_V2.kt
rm fft_replacement.txt
success "Implémentation FFT remplacée"

# Correction 3.1: Absence de gestion des conflits d'insertion
info "Ajout de la transaction Room..."
sed -i 's/suspend fun recordSighting(/@Transaction\n    suspend fun recordSighting(/' SightingRepository.kt
success "Transaction Room ajoutée"

# Correction 3.2: Absence d'index sur les champs fréquemment consultés
info "Ajout des index sur les champs fréquemment consultés..."
sed -i 's/@Entity(tableName = "sighting_table")/@Entity(\n    tableName = "sighting_table",\n    indices = [\n        Index(value = ["timestamp"]),\n        Index(value = ["reported"])\n    ]\n)/' SightingRepository.kt
sed -i '1s/^/import androidx.room.Index\n/' SightingRepository.kt
success "Index ajoutés"

# Correction 4.2: Gestion des permissions
info "Ajout de la vérification des permissions..."
cat > permissions_check.txt << 'EOF'
/**
 * Vérifie si les permissions nécessaires sont accordées.
 */
private fun checkRequiredPermissions(): Boolean {
    val audioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    )
    val locationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    return audioPermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
           locationPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
}
EOF

# Ajouter la vérification des permissions dans OptimizedAudioEngine_V2.kt
sed -i '/class OptimizedAudioEngine_V2/,/^}/s/suspend fun startPassiveListening() {/suspend fun startPassiveListening() {\n        if (!checkRequiredPermissions()) {\n            logRepository.logError("Permissions nécessaires non accordées")\n            _engineState.value = AudioEngineState.ERROR\n            return\n        }/' OptimizedAudioEngine_V2.kt

# Ajouter la fonction de vérification des permissions
sed -i "/enum class AudioEngineState/i\\$(cat permissions_check.txt)" OptimizedAudioEngine_V2.kt

# Ajouter les imports nécessaires
sed -i '1s/^/import androidx.core.content.ContextCompat\n/' OptimizedAudioEngine_V2.kt
rm permissions_check.txt
success "Vérification des permissions ajoutée"

# Correction 5.2: Optimisation de la consommation de batterie
info "Optimisation des contraintes WorkManager..."
sed -i 's/setRequiresDeviceIdle(false)/setRequiresDeviceIdle(false)\n            .setRequiresStorageNotLow(true)/' ProactiveBroadcastWorker.kt
success "Contraintes WorkManager optimisées"

# Création d'un fichier de tests unitaires
info "Création d'un fichier de tests unitaires..."
cat > ProactiveBroadcastWorkerTest.kt << 'EOF'
package com.ecoguard.app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ecoguard.app.anonymization.SmartAnonymizationSystem
import com.ecoguard.app.audio.SmartFSKGenerator
import com.ecoguard.app.repository.LogRepository
import com.ecoguard.app.storage.SecurePartitionStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProactiveBroadcastWorkerTest {

    private lateinit var context: Context
    private lateinit var fskGenerator: SmartFSKGenerator
    private lateinit var logRepository: LogRepository
    private lateinit var secureStorage: SecurePartitionStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Mocks
        fskGenerator = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        secureStorage = mockk(relaxed = true)
        
        // Koin setup
        stopKoin()
        startKoin {
            modules(module {
                single { fskGenerator }
                single { logRepository }
                single { secureStorage }
            })
        }
    }

    @Test
    fun testWorkerSuccess() = runBlocking {
        // Arrange
        val partitionParams = mockk<com.ecoguard.app.audio.PartitionParameters>()
        every { partitionParams.isValid() } returns true
        every { partitionParams.ownerUUID } returns "test-uuid-123456789"
        every { secureStorage.loadPartitionParameters() } returns partitionParams
        
        val generationResult = mockk<SmartFSKGenerator.GenerationResult>()
        every { generationResult.audioData } returns ShortArray(1000)
        every { generationResult.estimatedDuration } returns 1.5f
        
        coEvery { 
            fskGenerator.generateSecurePacketAudio(
                any(), 
                any<SmartAnonymizationSystem.PrivacyLevel>(),
                any()
            ) 
        } returns generationResult

        // Act
        val worker = TestListenableWorkerBuilder<ProactiveBroadcastWorker>(
            context = context,
            inputData = workDataOf(
                ProactiveBroadcastWorker.PARAM_PRIVACY_LEVEL to SmartAnonymizationSystem.PrivacyLevel.BALANCED.name
            )
        ).build()
        
        val result = worker.doWork()
        
        // Assert
        assertEquals(ListenableWorker.Result.Success::class.java, result::class.java)
    }

    @Test
    fun testWorkerFailure_InvalidPartition() = runBlocking {
        // Arrange
        every { secureStorage.loadPartitionParameters() } returns null
        
        // Act
        val worker = TestListenableWorkerBuilder<ProactiveBroadcastWorker>(
            context = context
        ).build()
        
        val result = worker.doWork()
        
        // Assert
        assertEquals(ListenableWorker.Result.Failure::class.java, result::class.java)
    }
}
EOF
success "Fichier de tests unitaires créé"

# Mise à jour du README
info "Mise à jour du README avec les corrections..."
cat >> README.md << 'EOF'

## Corrections Appliquées

Suite au rapport de débogage, les corrections suivantes ont été appliquées :

1. **Correction des fuites de ressources** dans ProactiveBroadcastWorker
2. **Optimisation des intervalles adaptatifs** pour une meilleure gestion de la batterie
3. **Remplacement de l'implémentation FFT** par JTransforms pour de meilleures performances
4. **Ajout de transactions Room** pour garantir l'atomicité des opérations de base de données
5. **Ajout d'index** sur les champs fréquemment consultés pour améliorer les performances des requêtes
6. **Vérification des permissions** avant d'utiliser les fonctionnalités sensibles
7. **Optimisation des contraintes WorkManager** pour une meilleure gestion de la batterie
8. **Ajout de tests unitaires** pour valider le bon fonctionnement des composants

Ces corrections améliorent la stabilité, les performances et la fiabilité de l'application EcoGuard V2.
EOF
success "README mis à jour"

echo ""
echo "=== Résumé des corrections ==="
echo ""
echo "1. Correction de la fuite de ressources dans AudioTrack"
echo "2. Correction des intervalles adaptatifs"
echo "3. Remplacement de l'implémentation FFT par JTransforms"
echo "4. Ajout de transactions Room"
echo "5. Ajout d'index sur les champs fréquemment consultés"
echo "6. Ajout de la vérification des permissions"
echo "7. Optimisation des contraintes WorkManager"
echo "8. Création d'un fichier de tests unitaires"
echo "9. Mise à jour du README"
echo ""
success "Toutes les corrections ont été appliquées avec succès!"
echo ""
echo "Pour revenir aux fichiers originaux, utilisez les fichiers .bak créés."
