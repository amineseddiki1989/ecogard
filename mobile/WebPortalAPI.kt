package com.ecoguard.app.api

import com.ecoguard.app.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * API client pour le portail web EcoGuard.
 * Gère l'authentification, la liste des appareils, et les déclarations de vol/récupération.
 * Cette classe simule l'API côté serveur qui serait utilisée par le portail web.
 */
class WebPortalAPI(
    private val baseUrl: String = "https://api.ecoguard.com/v2",
    private val logRepository: LogRepository
) {

    companion object {
        private const val API_VERSION = "2.0"
        private const val TIMEOUT_MS = 30000
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    /**
     * Authentifie un utilisateur avec email et mot de passe.
     */
    suspend fun authenticateUser(email: String, password: String): AuthenticationResult = withContext(Dispatchers.IO) {
        try {
            val hashedPassword = hashPassword(password)
            
            val requestBody = JSONObject().apply {
                put("email", email)
                put("password_hash", hashedPassword)
                put("api_version", API_VERSION)
                put("timestamp", System.currentTimeMillis())
            }

            val response = makeSecureRequest(
                endpoint = "/auth/login",
                method = "POST",
                body = requestBody
            )

            if (response.success) {
                val responseData = JSONObject(response.data ?: "{}")
                val sessionToken = responseData.optString("session_token")
                val userId = responseData.optString("user_id")
                val expiresAt = responseData.optLong("expires_at", 0L)

                logRepository.logSystem(
                    "Authentification réussie",
                    "User ID: ${userId.take(8)}..., Session expire: ${expiresAt}"
                )

                AuthenticationResult(
                    success = true,
                    sessionToken = sessionToken,
                    userId = userId,
                    expiresAt = expiresAt
                )
            } else {
                logRepository.logError("Échec authentification: ${response.errorMessage}")
                AuthenticationResult(
                    success = false,
                    errorMessage = response.errorMessage
                )
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur authentification: ${e.message}")
            AuthenticationResult(
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Récupère la liste des appareils associés à un utilisateur.
     */
    suspend fun getUserDevices(sessionToken: String): DeviceListResult = withContext(Dispatchers.IO) {
        try {
            val response = makeAuthenticatedRequest(
                endpoint = "/devices/list",
                method = "GET",
                sessionToken = sessionToken
            )

            if (response.success) {
                val responseData = JSONObject(response.data ?: "{}")
                val devicesArray = responseData.optJSONArray("devices")
                val devices = mutableListOf<UserDevice>()

                devicesArray?.let { array ->
                    for (i in 0 until array.length()) {
                        val deviceJson = array.getJSONObject(i)
                        devices.add(parseDeviceFromJson(deviceJson))
                    }
                }

                logRepository.logSystem(
                    "Liste appareils récupérée",
                    "Nombre: ${devices.size}"
                )

                DeviceListResult(
                    success = true,
                    devices = devices
                )
            } else {
                logRepository.logError("Échec récupération appareils: ${response.errorMessage}")
                DeviceListResult(
                    success = false,
                    errorMessage = response.errorMessage
                )
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur récupération appareils: ${e.message}")
            DeviceListResult(
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Déclare un appareil comme volé.
     */
    suspend fun reportDeviceStolen(
        sessionToken: String,
        deviceId: String,
        partitionUuid: String,
        stolenLocation: Location? = null,
        stolenTimestamp: Long = System.currentTimeMillis(),
        additionalInfo: String? = null
    ): StolenReportResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("device_id", deviceId)
                put("partition_uuid", partitionUuid)
                put("status", "stolen")
                put("stolen_timestamp", stolenTimestamp)
                put("report_timestamp", System.currentTimeMillis())
                
                stolenLocation?.let { location ->
                    put("last_known_location", JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy)
                        put("timestamp", location.timestamp)
                    })
                }
                
                additionalInfo?.let { put("additional_info", it) }
            }

            val response = makeAuthenticatedRequest(
                endpoint = "/devices/report-stolen",
                method = "POST",
                sessionToken = sessionToken,
                body = requestBody
            )

            if (response.success) {
                val responseData = JSONObject(response.data ?: "{}")
                val alertId = responseData.optString("alert_id")
                val notificationsSent = responseData.optInt("notifications_sent", 0)

                logRepository.logSystem(
                    "Appareil déclaré volé",
                    "Device: ${deviceId.take(8)}..., Alert ID: $alertId, Notifications: $notificationsSent"
                )

                // Déclencher l'envoi des notifications push à la communauté
                triggerCommunityAlert(partitionUuid, alertId)

                StolenReportResult(
                    success = true,
                    alertId = alertId,
                    notificationsSent = notificationsSent
                )
            } else {
                logRepository.logError("Échec déclaration vol: ${response.errorMessage}")
                StolenReportResult(
                    success = false,
                    errorMessage = response.errorMessage
                )
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur déclaration vol: ${e.message}")
            StolenReportResult(
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Marque un appareil comme récupéré.
     */
    suspend fun reportDeviceRecovered(
        sessionToken: String,
        deviceId: String,
        partitionUuid: String,
        recoveredLocation: Location? = null,
        recoveredTimestamp: Long = System.currentTimeMillis(),
        additionalInfo: String? = null
    ): RecoveryReportResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("device_id", deviceId)
                put("partition_uuid", partitionUuid)
                put("status", "recovered")
                put("recovered_timestamp", recoveredTimestamp)
                put("report_timestamp", System.currentTimeMillis())
                
                recoveredLocation?.let { location ->
                    put("recovery_location", JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy)
                        put("timestamp", location.timestamp)
                    })
                }
                
                additionalInfo?.let { put("additional_info", it) }
            }

            val response = makeAuthenticatedRequest(
                endpoint = "/devices/report-recovered",
                method = "POST",
                sessionToken = sessionToken,
                body = requestBody
            )

            if (response.success) {
                val responseData = JSONObject(response.data ?: "{}")
                val recoveryId = responseData.optString("recovery_id")

                logRepository.logSystem(
                    "Appareil marqué comme récupéré",
                    "Device: ${deviceId.take(8)}..., Recovery ID: $recoveryId"
                )

                // Arrêter les alertes pour cet appareil
                stopCommunityAlert(partitionUuid)

                RecoveryReportResult(
                    success = true,
                    recoveryId = recoveryId
                )
            } else {
                logRepository.logError("Échec déclaration récupération: ${response.errorMessage}")
                RecoveryReportResult(
                    success = false,
                    errorMessage = response.errorMessage
                )
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur déclaration récupération: ${e.message}")
            RecoveryReportResult(
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Déclenche l'envoi d'alertes à la communauté via FCM.
     */
    private suspend fun triggerCommunityAlert(partitionUuid: String, alertId: String) {
        try {
            val alertPayload = JSONObject().apply {
                put("type", "stolen_device_alert")
                put("stolen_uuid", partitionUuid)
                put("alert_id", alertId)
                put("timestamp", System.currentTimeMillis())
                put("priority", "high")
            }

            val response = makeSecureRequest(
                endpoint = "/notifications/community-alert",
                method = "POST",
                body = alertPayload
            )

            if (response.success) {
                logRepository.logSystem(
                    "Alerte communauté déclenchée",
                    "UUID: ${partitionUuid.take(8)}..., Alert: $alertId"
                )
            } else {
                logRepository.logError("Échec alerte communauté: ${response.errorMessage}")
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur alerte communauté: ${e.message}")
        }
    }

    /**
     * Arrête les alertes pour un appareil récupéré.
     */
    private suspend fun stopCommunityAlert(partitionUuid: String) {
        try {
            val stopPayload = JSONObject().apply {
                put("type", "stop_alert")
                put("partition_uuid", partitionUuid)
                put("timestamp", System.currentTimeMillis())
            }

            val response = makeSecureRequest(
                endpoint = "/notifications/stop-alert",
                method = "POST",
                body = stopPayload
            )

            if (response.success) {
                logRepository.logSystem(
                    "Alerte communauté arrêtée",
                    "UUID: ${partitionUuid.take(8)}..."
                )
            }

        } catch (e: Exception) {
            logRepository.logError("Erreur arrêt alerte: ${e.message}")
        }
    }

    private suspend fun makeSecureRequest(
        endpoint: String,
        method: String,
        body: JSONObject? = null
    ): APIResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "EcoGuard-WebPortal/2.0")
                setRequestProperty("X-API-Version", API_VERSION)
                
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }

            val responseCode = connection.responseCode
            val responseData = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText()
            }

            APIResponse(
                success = responseCode in 200..299,
                httpCode = responseCode,
                data = responseData,
                errorMessage = if (responseCode !in 200..299) "HTTP $responseCode" else null
            )

        } catch (e: Exception) {
            APIResponse(
                success = false,
                errorMessage = e.message
            )
        }
    }

    private suspend fun makeAuthenticatedRequest(
        endpoint: String,
        method: String,
        sessionToken: String,
        body: JSONObject? = null
    ): APIResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $sessionToken")
                setRequestProperty("User-Agent", "EcoGuard-WebPortal/2.0")
                setRequestProperty("X-API-Version", API_VERSION)
                
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }

            val responseCode = connection.responseCode
            val responseData = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText()
            }

            APIResponse(
                success = responseCode in 200..299,
                httpCode = responseCode,
                data = responseData,
                errorMessage = if (responseCode !in 200..299) "HTTP $responseCode" else null
            )

        } catch (e: Exception) {
            APIResponse(
                success = false,
                errorMessage = e.message
            )
        }
    }

    private fun parseDeviceFromJson(json: JSONObject): UserDevice {
        return UserDevice(
            deviceId = json.optString("device_id"),
            partitionUuid = json.optString("partition_uuid"),
            deviceName = json.optString("device_name", "Appareil EcoGuard"),
            deviceModel = json.optString("device_model", "Inconnu"),
            registrationDate = json.optLong("registration_date", 0L),
            lastSeen = json.optLong("last_seen", 0L),
            status = DeviceStatus.valueOf(json.optString("status", "ACTIVE").uppercase()),
            lastKnownLocation = json.optJSONObject("last_known_location")?.let { locationJson ->
                Location(
                    latitude = locationJson.optDouble("latitude", 0.0),
                    longitude = locationJson.optDouble("longitude", 0.0),
                    accuracy = locationJson.optDouble("accuracy", 0.0),
                    timestamp = locationJson.optLong("timestamp", 0L)
                )
            }
        )
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Résultat d'authentification.
 */
data class AuthenticationResult(
    val success: Boolean,
    val sessionToken: String? = null,
    val userId: String? = null,
    val expiresAt: Long = 0L,
    val errorMessage: String? = null
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val remainingTimeMs: Long get() = maxOf(0L, expiresAt - System.currentTimeMillis())
}

/**
 * Résultat de récupération de la liste des appareils.
 */
data class DeviceListResult(
    val success: Boolean,
    val devices: List<UserDevice> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Résultat de déclaration de vol.
 */
data class StolenReportResult(
    val success: Boolean,
    val alertId: String? = null,
    val notificationsSent: Int = 0,
    val errorMessage: String? = null
)

/**
 * Résultat de déclaration de récupération.
 */
data class RecoveryReportResult(
    val success: Boolean,
    val recoveryId: String? = null,
    val errorMessage: String? = null
)

/**
 * Appareil utilisateur.
 */
data class UserDevice(
    val deviceId: String,
    val partitionUuid: String,
    val deviceName: String,
    val deviceModel: String,
    val registrationDate: Long,
    val lastSeen: Long,
    val status: DeviceStatus,
    val lastKnownLocation: Location? = null
) {
    val isRecentlyActive: Boolean get() = (System.currentTimeMillis() - lastSeen) < (24 * 60 * 60 * 1000)
    val daysSinceLastSeen: Long get() = (System.currentTimeMillis() - lastSeen) / (24 * 60 * 60 * 1000)
}

/**
 * Statut d'un appareil.
 */
enum class DeviceStatus {
    ACTIVE,
    STOLEN,
    RECOVERED,
    INACTIVE,
    SUSPENDED
}

/**
 * Localisation.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val timestamp: Long
) {
    val age: Long get() = System.currentTimeMillis() - timestamp
    val ageHours: Long get() = age / (1000 * 60 * 60)
    val isRecent: Boolean get() = ageHours < 24
}

/**
 * Réponse API générique.
 */
data class APIResponse(
    val success: Boolean,
    val httpCode: Int? = null,
    val data: String? = null,
    val errorMessage: String? = null
) {
    val isRetryable: Boolean get() = httpCode in 500..599 || httpCode == 429
}

