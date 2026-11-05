package org.elbe.relations.mobile.p2p

import android.content.Context
import java.io.File

/** The peer-to-peer identity in the app's files directory, excluded from backups. */
const val P2P_IDENTITY_FILE = "p2p_identity.key"

/**
 * The Android PeerTransport: a Libp2pHost with the identity in the app's files directory.
 *
 * @param context Context the application context
 */
class Libp2pTransport(context: Context) :
        PeerTransport by Libp2pHost(File(context.applicationContext.filesDir, P2P_IDENTITY_FILE))
