package dev.seabat.android.hellonearbyconnections.model.neaby

import android.util.Log
import com.google.android.gms.nearby.connection.*
import dev.seabat.android.hellonearbyconnections.model.game.GameChoiceEnum
import dev.seabat.android.hellonearbyconnections.viewmodel.MainViewModel
import java.util.*

class NearbyConnections(builder: Builder) {
    // interfaces

    interface PlayMatchListener {
        fun onReceiveOpponentChoice(biteArray: ByteArray)
        fun onJudgeIfPossible()
        fun onReceiveOpponentName(opponentName: String)
        fun onConnectWithOpponent(endpointId: String)
        fun onDisconnectWithOpponent()
    }


    // objects

    companion object {
        const val TAG = "NEARBY_CONNECTIONS"
    }


    // properties

    /**
     * [Nearby Connections API][ConnectionsClient]
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
     * Strategy for telling the Nearby Connections API how we want to discover and connect to
     * other nearby devices. A star shaped strategy means we want to discover multiple devices but
     * only connect to and communicate with one at a time.
     */
    private val STRATEGY = Strategy.P2P_STAR

    /** callback for receiving payloads */
    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "onPayloadReceived called [endpoint: " + endpointId + "]")
            payload.asBytes()?.let {
                this@NearbyConnections.playMatchListener.onReceiveOpponentChoice(it)
            }
        }

        /**
         * ペイロードの送受信状態の更新イベント
         * NOTE: 送信・受信の両方で発火する
         */
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d(TAG, "onPayloadTransferUpdate called [status:" + update.status + "]")
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
            Log.d(TAG, "onConnectionInitiated called [endpoint: " + endpointId + ", digits: " + info.authenticationDigits + "]")
            // Accepting a connection means you want to receive messages. Hence, the API expects
            // that you attach a PayloadCall to the acceptance
            this@NearbyConnections.connectionsClient.acceptConnection(endpointId, this@NearbyConnections.payloadCallback)
            this@NearbyConnections.playMatchListener.onReceiveOpponentName(info.endpointName)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "onConnectionResult called [endpoint: " + endpointId + ", status: " + result.status + "]")
            if (result.status.isSuccess) {
                this@NearbyConnections.connectionsClient.stopAdvertising()
                this@NearbyConnections.connectionsClient.stopDiscovery()
                this@NearbyConnections.playMatchListener.onConnectWithOpponent(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "onDisconnected called [endpoint: " + endpointId + "]")
            this@NearbyConnections.playMatchListener.onDisconnectWithOpponent()
        }
    }

    // Callbacks for finding other devices
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "onEndpointFound called [endpoint: " + endpointId + ", serviceId:" + info.serviceId + "]")
            this@NearbyConnections.connectionsClient.requestConnection(
                this@NearbyConnections.endPointName,
                endpointId,
                this@NearbyConnections.connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
        }
    }


    // classes

    /**
     * NearbyConnectClient の Builder クラス
     */
    class Builder(val listener: PlayMatchListener, val connectionsClient: ConnectionsClient) {
        var endPointName: String = UUID.randomUUID().toString()
        var serviceId: String = "service-id"
        fun setEndPointName(endPointName: String): Builder {
            this.endPointName = endPointName
            return this
        }
        fun setServiceId(serviceId: String): Builder{
            this.serviceId = serviceId
            return this
        }
        fun create(): NearbyConnections {
            return NearbyConnections(this)
        }
    }


    // methods

    fun startDiscovery(){
        Log.d(TAG, "startDiscovery called [" + this.serviceId + "]")
        val options = DiscoveryOptions.Builder().setStrategy(this.STRATEGY).build()
        this.connectionsClient.startDiscovery(this.serviceId, this.endpointDiscoveryCallback,options)
    }

    fun startAdvertising() {
        Log.d(TAG, "startAdvertising called [" + this.endPointName + " " + this.serviceId + "]")
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
        Log.d(MainViewModel.TAG, "sendGameChoice called")
        this.connectionsClient.sendPayload(
            opponentEndpointId,
            Payload.fromBytes(choice.name.toByteArray(Charsets.UTF_8))
        )
    }
}