@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.achievements

import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.AnnotatedData
import com.google.android.gms.games.achievement.Achievement
import com.google.android.gms.games.achievement.AchievementBuffer
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import kotlin.test.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

class AchievementsAdapterTest {
    @Test
    fun standardAchievementLoadsAndUnlocksWithoutIncrementalGetters() = runTest {
        val sdk = mock(AchievementsClient::class.java)
        val standard = mock(Achievement::class.java)
        `when`(standard.achievementId).thenReturn("standard")
        `when`(standard.name).thenReturn("Standard")
        `when`(standard.description).thenReturn("Description")
        `when`(standard.type).thenReturn(Achievement.TYPE_STANDARD)
        `when`(standard.state).thenReturn(Achievement.STATE_REVEALED)
        `when`(standard.totalSteps).thenThrow(IllegalStateException("Not incremental"))
        `when`(standard.currentSteps).thenThrow(IllegalStateException("Not incremental"))
        val buffer = mock(AchievementBuffer::class.java)
        `when`(buffer.count).thenReturn(1)
        `when`(buffer.get(0)).thenReturn(standard)
        val loaded = DeferredTask(AnnotatedData<AchievementBuffer>(buffer, false), immediate = true)
        `when`(sdk.load(true)).thenReturn(loaded.task)
        val unlock = DeferredTask<Void?>(null)
        `when`(sdk.unlockImmediate("standard")).thenReturn(unlock.task)
        val client = AndroidAchievementsClient(sdk, { error("Unexpected UI") }, AchievementIdMappings.Empty)
        val achievement = client.loadAchievements(true).getOrThrow().single()
        assertNull(achievement.steps)
        assertEquals(0, achievement.completionPercent)
        verify(buffer).release()
        val request = async { client.reportProgress(AchievementId("standard"), AchievementProgress.Unlocked) }
        runCurrent()
        assertFalse(request.isCompleted)
        unlock.finish()
        request.await().getOrThrow()
        verify(sdk).unlockImmediate("standard")
        verify(sdk, never()).load(false) // Reuses metadata loaded above.
        verify(standard, never()).getTotalSteps()
        verify(standard, never()).getCurrentSteps()
    }

    @Test
    fun incrementalUpdateUsesConfiguredStepsAndPropagatesAcknowledgementFailure() = runTest {
        val sdk = mock(AchievementsClient::class.java)
        val achievement = mock(Achievement::class.java)
        `when`(achievement.achievementId).thenReturn("incremental")
        `when`(achievement.type).thenReturn(Achievement.TYPE_INCREMENTAL)
        `when`(achievement.totalSteps).thenReturn(17)
        val buffer = mock(AchievementBuffer::class.java)
        `when`(buffer.count).thenReturn(1)
        `when`(buffer.get(0)).thenReturn(achievement)
        val loaded = DeferredTask(AnnotatedData<AchievementBuffer>(buffer, false), immediate = true)
        `when`(sdk.load(false)).thenReturn(loaded.task)
        val update = DeferredTask(false)
        `when`(sdk.setStepsImmediate("incremental", 8)).thenReturn(update.task)
        val client = AndroidAchievementsClient(sdk, {}, AchievementIdMappings.Empty)
        val request = async { client.reportProgress(AchievementId("incremental"), AchievementProgress.Percent(50)) }
        runCurrent()
        assertFalse(request.isCompleted)
        val failure = IllegalStateException("Offline")
        update.finish(failure)
        val mapped = assertIs<GameServicesException.ProviderFailure>(request.await().exceptionOrNull())
        assertTrue(generateSequence(mapped.cause) { it.cause }.any { it === failure })
        verify(buffer).release()
    }
}

private class DeferredTask<T>(value: T, immediate: Boolean = false) {
    @Suppress("UNCHECKED_CAST")
    val task = mock(Task::class.java) as Task<T>
    private var listener: OnCompleteListener<T>? = null
    init {
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(value)
        doAnswer {
            listener = it.getArgument(0)
            if (immediate) finish()
            task
        }.`when`(task).addOnCompleteListener(any<OnCompleteListener<T>>())
    }
    fun finish(error: Exception? = null) {
        `when`(task.isSuccessful).thenReturn(error == null)
        `when`(task.exception).thenReturn(error)
        requireNotNull(listener).onComplete(task)
    }
}
