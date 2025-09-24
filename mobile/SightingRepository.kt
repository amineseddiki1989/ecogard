import androidx.room.Index
package com.ecoguard.app.repository

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ecoguard.app.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Repository pour gérer les observations (sightings) des partitions ultrasoniques détectées.
 * Chaque observation représente la détection d'une partition d'un autre appareil EcoGuard.
 * Ces données sont stockées localement et utilisées pour générer des rapports lorsqu'un appareil est déclaré volé.
 */
class SightingRepository(
    private val sightingDao: SightingDao,
    private val logRepository: LogRepository
) {

    /**
     * Enregistre ou met à jour une observation de partition.
     * Si la partition a déjà été observée, met à jour la position et le timestamp.
     */
    @Transaction
    suspend fun recordSighting(
        partitionUuid: String,
        latitude: Double,
        longitude: Double,
        confidence: Float
    ) = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Vérifier si une observation existe déjà pour cette partition
            val existingSighting = sightingDao.getSightingByUuid(partitionUuid)
            
            if (existingSighting != null) {
                // Mettre à jour l'observation existante
                val updatedSighting = existingSighting.copy(
                    timestamp = currentTime,
                    latitude = latitude,
                    longitude = longitude,
                    confidence = maxOf(existingSighting.confidence, confidence), // Garder la meilleure confiance
                    reported = false // Réinitialiser le statut de rapport si la position a changé
                )
                sightingDao.updateSighting(updatedSighting)
                
                logRepository.logSystem(
                    "Observation mise à jour",
                    "UUID: ${partitionUuid.take(8)}..., Confiance: ${String.format("%.2f", confidence)}"
                )
            } else {
                // Créer une nouvelle observation
                val newSighting = Sighting(
                    partitionUuid = partitionUuid,
                    timestamp = currentTime,
                    latitude = latitude,
                    longitude = longitude,
                    confidence = confidence,
                    reported = false
                )
                sightingDao.insertSighting(newSighting)
                
                logRepository.logSystem(
                    "Nouvelle observation enregistrée",
                    "UUID: ${partitionUuid.take(8)}..., Position: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
                )
            }
            
        } catch (e: Exception) {
            logRepository.logError("Erreur enregistrement observation: ${e.message}")
            throw e
        }
    }

    /**
     * Récupère la dernière observation pour un UUID de partition donné.
     */
    suspend fun getLatestSightingFor(partitionUuid: String): Sighting? = withContext(Dispatchers.IO) {
        try {
            val sighting = sightingDao.getSightingByUuid(partitionUuid)
            
            if (sighting != null) {
                logRepository.logSystem(
                    "Observation trouvée",
                    "UUID: ${partitionUuid.take(8)}..., Age: ${(System.currentTimeMillis() - sighting.timestamp) / 1000}s"
                )
            }
            
            sighting
        } catch (e: Exception) {
            logRepository.logError("Erreur récupération observation: ${e.message}")
            null
        }
    }

    /**
     * Récupère toutes les observations récentes (dernières 24h par défaut).
     */
    suspend fun getRecentSightings(maxAgeHours: Long = 24): List<Sighting> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
            val sightings = sightingDao.getSightingsAfter(cutoffTime)
            
            logRepository.logSystem(
                "Observations récentes récupérées",
                "Nombre: ${sightings.size}, Période: ${maxAgeHours}h"
            )
            
            sightings
        } catch (e: Exception) {
            logRepository.logError("Erreur récupération observations récentes: ${e.message}")
            emptyList()
        }
    }

    /**
     * Marque une observation comme signalée.
     */
    suspend fun markSightingAsReported(partitionUuid: String) = withContext(Dispatchers.IO) {
        try {
            val sighting = sightingDao.getSightingByUuid(partitionUuid)
            if (sighting != null) {
                val updatedSighting = sighting.copy(reported = true)
                sightingDao.updateSighting(updatedSighting)
                
                logRepository.logSystem(
                    "Observation marquée comme signalée",
                    "UUID: ${partitionUuid.take(8)}..."
                )
            }
        } catch (e: Exception) {
            logRepository.logError("Erreur marquage observation: ${e.message}")
        }
    }

    /**
     * Supprime les observations anciennes pour libérer de l'espace.
     */
    suspend fun cleanupOldSightings(maxAgeHours: Long = 168) = withContext(Dispatchers.IO) { // 7 jours par défaut
        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
            val deletedCount = sightingDao.deleteOldSightings(cutoffTime)
            
            logRepository.logSystem(
                "Nettoyage observations anciennes",
                "Supprimées: $deletedCount, Âge max: ${maxAgeHours}h"
            )
            
            deletedCount
        } catch (e: Exception) {
            logRepository.logError("Erreur nettoyage observations: ${e.message}")
            0
        }
    }

    /**
     * Obtient des statistiques sur les observations.
     */
    suspend fun getSightingStats(): SightingStats = withContext(Dispatchers.IO) {
        try {
            val totalCount = sightingDao.getTotalSightingsCount()
            val recentCount = sightingDao.getRecentSightingsCount(System.currentTimeMillis() - (24 * 60 * 60 * 1000))
            val reportedCount = sightingDao.getReportedSightingsCount()
            val uniquePartitions = sightingDao.getUniquePartitionsCount()
            
            SightingStats(
                totalSightings = totalCount,
                recentSightings = recentCount,
                reportedSightings = reportedCount,
                uniquePartitions = uniquePartitions
            )
        } catch (e: Exception) {
            logRepository.logError("Erreur récupération statistiques: ${e.message}")
            SightingStats(0, 0, 0, 0)
        }
    }

    /**
     * Obtient un flux en temps réel des observations récentes.
     */
    fun getRecentSightingsFlow(maxAgeHours: Long = 24): Flow<List<Sighting>> {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
        return sightingDao.getSightingsAfterFlow(cutoffTime)
    }

    /**
     * Vérifie si une observation existe pour un UUID donné.
     */
    suspend fun hasSightingFor(partitionUuid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sightingDao.getSightingByUuid(partitionUuid) != null
        } catch (e: Exception) {
            logRepository.logError("Erreur vérification observation: ${e.message}")
            false
        }
    }

    /**
     * Obtient les observations non signalées.
     */
    suspend fun getUnreportedSightings(): List<Sighting> = withContext(Dispatchers.IO) {
        try {
            sightingDao.getUnreportedSightings()
        } catch (e: Exception) {
            logRepository.logError("Erreur récupération observations non signalées: ${e.message}")
            emptyList()
        }
    }
}

/**
 * Entité Room représentant une observation de partition.
 */
@Entity(
    tableName = "sighting_table",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["reported"])
    ]
)
data class Sighting(
    @PrimaryKey val partitionUuid: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val confidence: Float,
    val reported: Boolean = false
) {
    val age: Long get() = System.currentTimeMillis() - timestamp
    val ageHours: Long get() = age / (1000 * 60 * 60)
    val isRecent: Boolean get() = ageHours < 24
    val isVeryRecent: Boolean get() = age < (5 * 60 * 1000) // 5 minutes
}

/**
 * DAO pour les opérations sur les observations.
 */
@Dao
interface SightingDao {
    
    @Query("SELECT * FROM sighting_table WHERE partitionUuid = :uuid LIMIT 1")
    suspend fun getSightingByUuid(uuid: String): Sighting?

    @Query("SELECT * FROM sighting_table WHERE timestamp > :cutoffTime ORDER BY timestamp DESC")
    suspend fun getSightingsAfter(cutoffTime: Long): List<Sighting>

    @Query("SELECT * FROM sighting_table WHERE timestamp > :cutoffTime ORDER BY timestamp DESC")
    fun getSightingsAfterFlow(cutoffTime: Long): Flow<List<Sighting>>

    @Query("SELECT * FROM sighting_table WHERE reported = 0 ORDER BY timestamp DESC")
    suspend fun getUnreportedSightings(): List<Sighting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSighting(sighting: Sighting)

    @Update
    suspend fun updateSighting(sighting: Sighting)

    @Query("DELETE FROM sighting_table WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSightings(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM sighting_table")
    suspend fun getTotalSightingsCount(): Int

    @Query("SELECT COUNT(*) FROM sighting_table WHERE timestamp > :cutoffTime")
    suspend fun getRecentSightingsCount(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM sighting_table WHERE reported = 1")
    suspend fun getReportedSightingsCount(): Int

    @Query("SELECT COUNT(DISTINCT partitionUuid) FROM sighting_table")
    suspend fun getUniquePartitionsCount(): Int
}

/**
 * Base de données Room pour les observations.
 */
@Database(
    entities = [Sighting::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SightingConverters::class)
abstract class SightingDatabase : RoomDatabase() {
    abstract fun sightingDao(): SightingDao

    companion object {
        @Volatile
        private var INSTANCE: SightingDatabase? = null

        fun getDatabase(context: Context): SightingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SightingDatabase::class.java,
                    "sighting_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration future si nécessaire
            }
        }
    }
}

/**
 * Convertisseurs de types pour Room.
 */
class SightingConverters {
    // Actuellement pas de convertisseurs nécessaires, mais préparé pour l'avenir
}

/**
 * Statistiques sur les observations.
 */
data class SightingStats(
    val totalSightings: Int,
    val recentSightings: Int,
    val reportedSightings: Int,
    val uniquePartitions: Int
) {
    val reportingRate: Float get() = if (totalSightings > 0) reportedSightings.toFloat() / totalSightings else 0f
    val averageSightingsPerPartition: Float get() = if (uniquePartitions > 0) totalSightings.toFloat() / uniquePartitions else 0f
}

