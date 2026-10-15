package dev.ikram.sandbox.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.ikram.sandbox.core.data.work.AutoDeleteWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The scheduled cleanup reaches the app's own graph and finishes, without the permissions WorkManager asks for. */
@RunWith(AndroidJUnit4::class)
class AutoDeleteWorkerTest {
    @Test
    fun runsToSuccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<AutoDeleteWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
