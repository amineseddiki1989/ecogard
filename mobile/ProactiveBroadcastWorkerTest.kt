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
