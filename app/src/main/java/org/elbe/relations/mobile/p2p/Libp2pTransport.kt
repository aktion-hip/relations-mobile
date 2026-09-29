package org.elbe.relations.mobile.p2p

import android.content.Context
import android.net.wifi.WifiManager
import java.io.File
import java.net.Inet4Address

/** The peer-to-peer identity in the app's files directory, excluded from backups. */
const val P2P_IDENTITY_FILE = "p2p_identity.key"

/**
 * The Android PeerTransport: a Libp2pHost that holds a multicast lock while listening, so that mDNS works.
 *
 * @param context Context the application context
 * @param address Inet4Address the WiFi address to listen on, see LocalAddress
 */
class Libp2pTransport private constructor(context: Context, private val host: Libp2pHost) : PeerTransport by host {

    constructor(context: Context, address: Inet4Address) :
            this(context, Libp2pHost(File(context.applicationContext.filesDir, P2P_IDENTITY_FILE), address))

    private val mLock: WifiManager.MulticastLock =
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .createMulticastLock("relations-p2p").apply { setReferenceCounted(false) }

    override fun start(port: Int): String {
        mLock.acquire()
        try {
            return host.start(port)
        } catch (e: Exception) {
            mLock.release()
            throw e
        }
    }

    override fun stopListening() {
        host.stopListening()
        release()
    }

    override fun stop() {
        host.stop()
        release()
    }

    private fun release() {
        if (mLock.isHeld) {
            mLock.release()
        }
    }
}
