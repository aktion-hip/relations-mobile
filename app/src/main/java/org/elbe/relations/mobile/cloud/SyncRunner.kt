package org.elbe.relations.mobile.cloud

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "SyncRunner"

/**
 * The state of the data synchronization, observed by the activities to display the progress.
 */
sealed class SyncState {
    object Idle : SyncState()

    /**
     * @param current Int the number of imported entries
     * @param max Int the number of entries to import, 0 while downloading
     * @param cancelable Boolean true if the user can cancel (i.e. while receiving from a peer)
     */
    data class Running(val current: Int, val max: Int, val cancelable: Boolean = false) : SyncState()

    /**
     * Searching for the Relations desktop applications, the user selects the one to connect to.
     *
     * @param computers List<FoundComputer> the computers found so far
     * @param editable Boolean true if the user can enter a connection string (debug builds)
     */
    data class SelectComputer(val computers: List<FoundComputer>, val editable: Boolean) : SyncState()

    /**
     * A computer requests a connection, the user has to compare and confirm the token.
     *
     * @param endpointName String the name of the requesting computer
     * @param token String the authentication token displayed on both devices
     */
    data class ConfirmPeer(val endpointName: String, val token: String) : SyncState()

    data class Done(val message: String) : SyncState()

    data class Failed(val message: String) : SyncState()
}

/**
 * A Relations desktop application found on the local network.
 *
 * @param peerId String the computer's peer ID (base58)
 * @param addresses List<String> the computer's addresses in the order to try, e.g. /ip4/192.168.1.10/tcp/47112
 */
data class FoundComputer(val peerId: String, val addresses: List<String>) {
    /** The multiaddress to display and to dial first, e.g. /ip4/192.168.1.10/tcp/47112/p2p/12D3KooW... */
    val preferred: String
        get() = "${addresses.first()}/p2p/$peerId"
}

/**
 * The user's answers during an interactive synchronization.
 */
sealed class PeerAnswer {
    object Accept : PeerAnswer()
    object Reject : PeerAnswer()
    object Cancel : PeerAnswer()

    /**
     * Connect to a computer.
     *
     * @param address String? the entered connection string (debug builds), dialed as it is
     * @param computer String? the peer ID of the selected computer, all its addresses are tried
     */
    data class Connect(val address: String? = null, val computer: String? = null) : PeerAnswer()
}

/**
 * A synchronization source that interacts with the user while running (e.g. peer-to-peer).
 */
interface InteractiveCloudProvider {

    /**
     * Blocking, must be called from a background thread.
     *
     * @param incremental Boolean true to import the increments, false to import all data
     * @param session SyncSession to report the state and receive the user's answers
     * @return AbstractCloudProvider.SyncResult
     */
    fun synchronize(incremental: Boolean, session: SyncSession): AbstractCloudProvider.SyncResult
}

/**
 * The link between a running synchronization and the UI, which the provider uses instead of holding an activity.
 *
 * @param publish (SyncState) -> Unit sets the synchronization's state
 */
class SyncSession internal constructor(private val publish: (SyncState) -> Unit) {
    private var mImported = 0
    private var mCancelable = false
    private var mListener: ((PeerAnswer) -> Unit)? = null

    @Volatile
    var isCanceled = false
        private set

    /**
     * @param listener (PeerAnswer) -> Unit called (on the UI thread) with each answer of the user,
     * immediately with CANCEL if the user has already canceled
     */
    fun setAnswerListener(listener: (PeerAnswer) -> Unit) {
        synchronized(this) { mListener = listener }
        if (isCanceled) {
            listener(PeerAnswer.Cancel)
        }
    }

    /**
     * @param computers List<FoundComputer> the computers found so far
     * @param editable Boolean true if the user can enter a connection string
     */
    fun selectComputer(computers: List<FoundComputer>, editable: Boolean) {
        mCancelable = true
        publish(SyncState.SelectComputer(computers, editable))
    }

    fun confirmPeer(endpointName: String, token: String) {
        mCancelable = true
        publish(SyncState.ConfirmPeer(endpointName, token))
    }

    /** Connecting to the selected computer, the user can cancel. */
    fun connecting() {
        mCancelable = true
        publish(SyncState.Running(0, 0, true))
    }

    /** Receiving the data, the user can cancel. */
    fun receiving() {
        mCancelable = true
        publish(SyncState.Running(0, 0, true))
    }

    /** Importing the data, the user cannot cancel anymore. */
    fun importing() {
        mCancelable = false
        publish(SyncState.Running(0, 0))
    }

    /**
     * @param current Int ignored, each call counts as one imported entry
     * @param max Int the number of entries to import, 0 while downloading
     */
    fun progress(@Suppress("UNUSED_PARAMETER") current: Int, max: Int) {
        if (max > 0) {
            mImported += 1
            publish(SyncState.Running(mImported, max, mCancelable))
        }
    }

    internal fun answer(answer: PeerAnswer) {
        if (answer == PeerAnswer.Cancel) {
            if (!mCancelable) {
                return
            }
            isCanceled = true
        }
        synchronized(this) { mListener }?.invoke(answer)
    }
}

/**
 * Runs the data synchronization in a scope that is independent of the activities,
 * i.e. the synchronization survives configuration changes like device rotation.
 *
 * @param scope CoroutineScope the scope to run the synchronization in
 * @param logError (Exception) -> Unit logs a failed synchronization
 */
class SyncRunner(private val scope: CoroutineScope,
                 private val logError: (Exception) -> Unit = { e -> Log.e(TAG, "Data synchronization failed.", e) }) {
    private val mState = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = mState.asStateFlow()
    @Volatile
    private var mSession: SyncSession? = null

    /**
     * Starts the synchronization unless one is already running.
     *
     * @param provider CloudProvider the provider that downloads and imports the data
     * @param incremental Boolean true for an incremental synchronization
     * @param errorMessage String the message to display if the synchronization fails without a message
     * @return Boolean true if the synchronization has been started
     */
    @Synchronized
    fun start(provider: CloudProvider, incremental: Boolean, errorMessage: String): Boolean =
            run(errorMessage) { session -> provider.synchronize(incremental) { current, max -> session.progress(current, max) } }

    /**
     * Starts the interactive synchronization unless one is already running.
     *
     * @param provider InteractiveCloudProvider the provider that receives and imports the data
     * @param incremental Boolean true for an incremental synchronization
     * @param errorMessage String the message to display if the synchronization fails without a message
     * @return Boolean true if the synchronization has been started
     */
    @Synchronized
    fun start(provider: InteractiveCloudProvider, incremental: Boolean, errorMessage: String): Boolean =
            run(errorMessage) { session -> provider.synchronize(incremental, session) }

    private fun run(errorMessage: String, synchronize: (SyncSession) -> AbstractCloudProvider.SyncResult): Boolean {
        if (isActive(mState.value)) {
            return false
        }
        mState.value = SyncState.Running(0, 0)
        val session = SyncSession { mState.value = it }
        mSession = session
        scope.launch {
            mState.value = try {
                val result = synchronize(session)
                if (result.value) SyncState.Done(result.message) else SyncState.Failed(result.message)
            } catch (e: Exception) {
                logError(e)
                SyncState.Failed(errorMessage)
            } finally {
                mSession = null
            }
        }
        return true
    }

    private fun isActive(state: SyncState): Boolean =
            state is SyncState.Running || state is SyncState.SelectComputer || state is SyncState.ConfirmPeer

    /**
     * Cancels an interactive synchronization while searching for, confirming or receiving from the peer, no-op otherwise.
     */
    fun cancel() {
        mSession?.answer(PeerAnswer.Cancel)
    }

    /**
     * Connects to a computer while in the SelectComputer state, no-op otherwise.
     *
     * @param address String? the entered connection string (debug builds)
     * @param computer String? the peer ID of the selected computer
     */
    fun connectTo(address: String? = null, computer: String? = null) {
        if (mState.value is SyncState.SelectComputer) {
            mSession?.answer(PeerAnswer.Connect(address, computer))
        }
    }

    /**
     * @param accept Boolean the user's answer to the ConfirmPeer state
     */
    fun answerPeer(accept: Boolean) {
        if (mState.value is SyncState.ConfirmPeer) {
            mSession?.answer(if (accept) PeerAnswer.Accept else PeerAnswer.Reject)
        }
    }

    /**
     * Resets a finished synchronization (Done or Failed) to Idle after its result has been displayed.
     */
    @Synchronized
    fun acknowledge() {
        if (mState.value is SyncState.Done || mState.value is SyncState.Failed) {
            mState.value = SyncState.Idle
        }
    }

    companion object {
        /** The application wide instance. */
        val instance: SyncRunner by lazy { SyncRunner(CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    }
}
