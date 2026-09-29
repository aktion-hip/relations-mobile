package org.elbe.relations.mobile.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRunnerTest {
    private val scope = TestScope(StandardTestDispatcher())
    private val runner = SyncRunner(scope) { }

    private class FakeProvider(private val result: () -> AbstractCloudProvider.SyncResult) : CloudProvider {
        val calls = mutableListOf<Boolean>()

        override fun synchronize(incremental: Boolean, progress: (Int, Int) -> Unit): AbstractCloudProvider.SyncResult {
            calls.add(incremental)
            progress(1, 2)
            progress(1, 2)
            return result()
        }
    }

    @Test
    fun testSuccess() {
        val provider = FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }
        assertEquals(SyncState.Idle, runner.state.value)
        assertTrue(runner.start(provider, true, "error"))
        assertEquals(SyncState.Running(0, 0), runner.state.value)
        scope.advanceUntilIdle()
        assertEquals(SyncState.Done("ok"), runner.state.value)
        assertEquals(listOf(true), provider.calls)

        runner.acknowledge()
        assertEquals(SyncState.Idle, runner.state.value)
    }

    @Test
    fun testProviderReportsFailure() {
        assertTrue(runner.start(FakeProvider { AbstractCloudProvider.SyncResult(false, "no increment") }, true, "error"))
        scope.advanceUntilIdle()
        assertEquals(SyncState.Failed("no increment"), runner.state.value)
    }

    @Test
    fun testExceptionIsReportedAsFailure() {
        assertTrue(runner.start(FakeProvider { throw java.io.IOException("network down") }, false, "error"))
        scope.advanceUntilIdle()
        assertEquals(SyncState.Failed("error"), runner.state.value)
    }

    @Test
    fun testProgress() {
        val states = mutableListOf<SyncState>()
        val gate = CompletableDeferred<Unit>()
        val provider = object : CloudProvider {
            override fun synchronize(incremental: Boolean, progress: (Int, Int) -> Unit): AbstractCloudProvider.SyncResult {
                progress(1, 0) // the import has not started yet
                progress(1, 3)
                states.add(runner.state.value)
                progress(1, 3)
                states.add(runner.state.value)
                runBlocking { gate.await() }
                return AbstractCloudProvider.SyncResult(true, "ok")
            }
        }
        gate.complete(Unit)
        runner.start(provider, false, "error")
        scope.advanceUntilIdle()
        assertEquals(listOf(SyncState.Running(1, 3), SyncState.Running(2, 3)), states)
    }

    @Test
    fun testSingleFlight() {
        val first = FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }
        val second = FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }
        assertTrue(runner.start(first, false, "error"))
        // the first synchronization is still running (not yet dispatched)
        assertFalse(runner.start(second, false, "error"))
        scope.advanceUntilIdle()
        assertEquals(1, first.calls.size)
        assertEquals(0, second.calls.size)
        // a finished synchronization does not block the next one
        assertTrue(runner.start(second, false, "error"))
        scope.advanceUntilIdle()
        assertEquals(1, second.calls.size)
    }

    /**
     * Waits for the peer, asks for confirmation and receives, controlled by the user's answers.
     */
    private class FakeInteractiveProvider : InteractiveCloudProvider {
        val answers = java.util.concurrent.LinkedBlockingQueue<PeerAnswer>()
        val states = mutableListOf<SyncState>()
        lateinit var session: SyncSession
        val started = java.util.concurrent.CountDownLatch(1)

        override fun synchronize(incremental: Boolean, session: SyncSession): AbstractCloudProvider.SyncResult {
            this.session = session
            session.setAnswerListener { answers.add(it) }
            session.waitingForPeer("/ip4/192.168.1.23/tcp/47112/p2p/12D3KooWTest")
            started.countDown()
            var answer = answers.take()
            if (answer == PeerAnswer.CANCEL) {
                return AbstractCloudProvider.SyncResult(false, "canceled")
            }
            session.confirmPeer("PC", "1234")
            answer = answers.take()
            if (answer != PeerAnswer.ACCEPT) {
                return AbstractCloudProvider.SyncResult(false, "rejected")
            }
            session.receiving()
            answer = answers.take()
            if (answer == PeerAnswer.CANCEL) {
                return AbstractCloudProvider.SyncResult(false, "canceled")
            }
            session.importing()
            return AbstractCloudProvider.SyncResult(true, "ok")
        }
    }

    /** Runs the interactive provider on a real thread, the test drives it with the runner's answers. */
    private val threadRunner = SyncRunner(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)) { }

    private fun awaitState(expected: (SyncState) -> Boolean): SyncState = runBlocking {
        kotlinx.coroutines.withTimeout(5000) {
            threadRunner.state.first { expected(it) }
        }
    }

    @Test
    fun testCancelWhileWaiting() {
        val provider = FakeInteractiveProvider()
        assertTrue(threadRunner.start(provider, false, "error"))
        assertEquals(SyncState.WaitingForPeer("/ip4/192.168.1.23/tcp/47112/p2p/12D3KooWTest"),
                awaitState { it is SyncState.WaitingForPeer })
        threadRunner.cancel()
        assertEquals(SyncState.Failed("canceled"), awaitState { it is SyncState.Failed })
    }

    @Test
    fun testConfirmAccept() {
        val provider = FakeInteractiveProvider()
        assertTrue(threadRunner.start(provider, true, "error"))
        awaitState { it is SyncState.WaitingForPeer }
        // the provider continues on any answer other than CANCEL, the fake uses ACCEPT to proceed
        threadRunner.answerPeer(true) // ignored: not in ConfirmPeer state
        provider.answers.add(PeerAnswer.ACCEPT)
        assertEquals(SyncState.ConfirmPeer("PC", "1234"), awaitState { it is SyncState.ConfirmPeer })
        threadRunner.answerPeer(true)
        awaitState { it == SyncState.Running(0, 0, true) }
        provider.answers.add(PeerAnswer.ACCEPT)
        assertEquals(SyncState.Done("ok"), awaitState { it is SyncState.Done })
    }

    @Test
    fun testConfirmReject() {
        val provider = FakeInteractiveProvider()
        assertTrue(threadRunner.start(provider, false, "error"))
        awaitState { it is SyncState.WaitingForPeer }
        provider.answers.add(PeerAnswer.ACCEPT)
        awaitState { it is SyncState.ConfirmPeer }
        threadRunner.answerPeer(false)
        assertEquals(SyncState.Failed("rejected"), awaitState { it is SyncState.Failed })
    }

    @Test
    fun testCancelWhileReceiving() {
        val provider = FakeInteractiveProvider()
        assertTrue(threadRunner.start(provider, false, "error"))
        awaitState { it is SyncState.WaitingForPeer }
        provider.answers.add(PeerAnswer.ACCEPT)
        awaitState { it is SyncState.ConfirmPeer }
        threadRunner.answerPeer(true)
        awaitState { it == SyncState.Running(0, 0, true) }
        threadRunner.cancel()
        assertEquals(SyncState.Failed("canceled"), awaitState { it is SyncState.Failed })
    }

    @Test
    fun testCancelIgnoredWhileImporting() {
        val gate = CompletableDeferred<Unit>()
        val provider = object : InteractiveCloudProvider {
            override fun synchronize(incremental: Boolean, session: SyncSession): AbstractCloudProvider.SyncResult {
                session.setAnswerListener { }
                session.importing()
                runBlocking { gate.await() }
                return AbstractCloudProvider.SyncResult(!session.isCanceled, "done")
            }
        }
        assertTrue(threadRunner.start(provider, false, "error"))
        awaitState { it == SyncState.Running(0, 0) }
        threadRunner.cancel()
        gate.complete(Unit)
        assertEquals(SyncState.Done("done"), awaitState { it is SyncState.Done })
    }

    @Test
    fun testNoSecondStartWhileWaitingOrConfirming() {
        val provider = FakeInteractiveProvider()
        assertTrue(threadRunner.start(provider, false, "error"))
        awaitState { it is SyncState.WaitingForPeer }
        assertFalse(threadRunner.start(FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }, false, "error"))
        assertFalse(threadRunner.start(FakeInteractiveProvider(), false, "error"))
        provider.answers.add(PeerAnswer.ACCEPT)
        awaitState { it is SyncState.ConfirmPeer }
        assertFalse(threadRunner.start(FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }, false, "error"))
        threadRunner.cancel()
        awaitState { it is SyncState.Failed }
    }

    @Test
    fun testCancelWithoutSyncIsNoOp() {
        runner.cancel()
        runner.answerPeer(true)
        assertEquals(SyncState.Idle, runner.state.value)
        // a cloud synchronization cannot be canceled
        assertTrue(runner.start(FakeProvider { AbstractCloudProvider.SyncResult(true, "ok") }, false, "error"))
        runner.cancel()
        scope.advanceUntilIdle()
        assertEquals(SyncState.Done("ok"), runner.state.value)
    }

}
