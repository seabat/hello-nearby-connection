package dev.seabat.android.hellonearbyconnections

import android.app.Activity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyConnections(builder: Builder) {
    interface PlayMatchListener {
        fun onReceiveOpponentChoice(biteArray: ByteArray)
        fun onJudgeIfPossible()
        fun onReceiveOpponentName(opponentName: String)
        fun onConnectWithOpponent(endpointId: String)
        fun onDisconnectWithOpponent()
    }

    /**
     * Our handle to the [Nearby Connections API][ConnectionsClient].
     */
    private val connectionsClient: ConnectionsClient = builder.connectionsClient

    private val playMatchListener: PlayMatchListener = builder.listener

    /**
     * advertise / discover する際の ID
     */
    private val serviceId: String = builder.serviceId

    /**
     * エンドポイント名。人が認識できる名前。
     */
    val endPointName: String =builder.endPointName



    /**
     * NearbyConnectClient の Builer クラス
     */
    class Builder(val activity: Activity, val listener: PlayMatchListener, val endPointName: String) {
        val serviceId: String = activity.packageName
        lateinit var connectionsClient: ConnectionsClient
        fun create(): NearbyConnections {
            connectionsClient = Nearby.getConnectionsClient(this.activity)
            return NearbyConnections(this)
        }
    }

    /**
     * Strategy for telling the Nearby Connections API how we want to discover and connect to
     * other nearby devices. A star shaped strategy means we want to discover multiple devices but
     * only connect to and communicate with one at a time.
     */
    private val STRATEGY = Strategy.P2P_STAR

    /** callback for receiving payloads */
    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                this@NearbyConnections.playMatchListener.onReceiveOpponentChoice(it)
            }
        }

        /**
         * ペイロードの送受信状態の更新イベント
         * NOTE: 送信・受信の両方で発火する
         */
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Determines the winner and updates game state/UI after both players have chosen.
            // Feel free to refactor and extract this code into a different method
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                this@NearbyConnections.playMatchListener.onJudgeIfPossible()
            }
        }
    }

    // Callbacks for connections to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Accepting a connection means you want to receive messages. Hence, the API expects
            // that you attach a PayloadCall to the acceptance
            this@NearbyConnections.connectionsClient.acceptConnection(endpointId, this@NearbyConnections.payloadCallback)
            this@NearbyConnections.playMatchListener.onReceiveOpponentName(info.endpointName)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                this@NearbyConnections.connectionsClient.stopAdvertising()
                this@NearbyConnections.connectionsClient.stopDiscovery()
                this@NearbyConnections.playMatchListener.onConnectWithOpponent(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            this@NearbyConnections.playMatchListener.onDisconnectWithOpponent()
        }
    }

    // Callbacks for finding other devices
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            this@NearbyConnections.connectionsClient.requestConnection(
                this@NearbyConnections.endPointName,
                endpointId,
                this@NearbyConnections.connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
        }
    }

    fun startDiscovery(){
        val options = DiscoveryOptions.Builder().setStrategy(this.STRATEGY).build()
        this.connectionsClient.startDiscovery(this.serviceId, this.endpointDiscoveryCallback,options)
    }

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(this.STRATEGY).build()
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        this.connectionsClient.startAdvertising(
            this.endPointName,
            this.serviceId,
            this.connectionLifecycleCallback,
            options
        )
    }

    fun disconnectFromEndpoint(opponentId: String) {
       this.connectionsClient.disconnectFromEndpoint(opponentId)
    }

    fun terminateConnection() {
        this.connectionsClient.also {
            it.stopAdvertising()
            it.stopDiscovery()
            it.stopAllEndpoints()
        }
    }

    /** Sends the user's selection of rock, paper, or scissors to the opponent. */
    fun sendGameChoice(choice: GameChoiceEnum, opponentEndpointId: String) {
        this.connectionsClient.sendPayload(
            opponentEndpointId,
            Payload.fromBytes(choice.name.toByteArray(Charsets.UTF_8))
        )
    }
}