package dev.seabat.android.hellonearbyconnections

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.CallSuper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dev.seabat.android.hellonearbyconnections.databinding.ActivityMainBinding
import java.util.Random
import kotlin.text.Charsets.UTF_8

class MainActivity : AppCompatActivity() {
    /**
     * Enum class for defining the winning rules for Rock-Paper-Scissors. Each player will make a
     * choice, then the beats function in this class will be used to determine whom to reward the
     * point to.
     */
    private enum class GameChoice {
        ROCK, PAPER, SCISSORS;

        fun beats(other: GameChoice): Boolean =
            (this == ROCK && other == SCISSORS)
                    || (this == SCISSORS && other == PAPER)
                    || (this == PAPER && other == ROCK)
    }

    /**
     * Instead of having each player enter a name, in this sample we will conveniently generate
     * random human readable names for players.
     */
    internal object CodenameGenerator {
        private val COLORS = arrayOf(
            "Red", "Orange", "Yellow", "Green", "Blue", "Indigo", "Violet", "Purple", "Lavender"
        )
        private val TREATS = arrayOf(
            "Cupcake", "Donut", "Eclair", "Froyo", "Gingerbread", "Honeycomb",
            "Ice Cream Sandwich", "Jellybean", "Kit Kat", "Lollipop", "Marshmallow", "Nougat",
            "Oreo", "Pie"
        )
        private val generator = Random()

        /** Generate a random Android agent codename  */
        fun generate(): String {
            val color = COLORS[generator.nextInt(COLORS.size)]
            val treat = TREATS[generator.nextInt(TREATS.size)]
            return "$color $treat"
        }
    }

    /**
     * Strategy for telling the Nearby Connections API how we want to discover and connect to
     * other nearby devices. A star shaped strategy means we want to discover multiple devices but
     * only connect to and communicate with one at a time.
     */
    private val STRATEGY = Strategy.P2P_STAR

    /**
     * Our handle to the [Nearby Connections API][ConnectionsClient].
     */
    private lateinit var connectionsClient: ConnectionsClient

    /**
     * The request code for verifying our call to [requestPermissions]. Recall that calling
     * [requestPermissions] leads to a callback to [onRequestPermissionsResult]
     */
    private val REQUEST_CODE_REQUIRED_PERMISSIONS = 1

    /*
    The following variables are convenient ways of tracking the data of the opponent that we
    choose to play against.
    */
    private var opponentName: String? = null
    private var opponentEndpointId: String? = null
    private var opponentScore = 0
    private var opponentChoice: GameChoice? = null

    /*
    The following variables are for tracking our own data
    */
    private var myCodeName: String = CodenameGenerator.generate()
    private var myScore = 0
    private var myChoice: GameChoice? = null

    /**
     * This is for wiring and interacting with the UI views.
     */
    private lateinit var binding: ActivityMainBinding

    /** callback for receiving payloads */
    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                this@MainActivity.opponentChoice = GameChoice.valueOf(String(it, UTF_8))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Determines the winner and updates game state/UI after both players have chosen.
            // Feel free to refactor and extract this code into a different method
            if (update.status == PayloadTransferUpdate.Status.SUCCESS
                && this@MainActivity.myChoice != null && this@MainActivity.opponentChoice != null) {
                val mc = this@MainActivity.myChoice!!
                val oc = this@MainActivity.opponentChoice!!
                when {
                    mc.beats(oc) -> { // Win!
                        this@MainActivity.binding.status.text = "${mc.name} beats ${oc.name}"
                        this@MainActivity.myScore++
                    }
                    mc == oc -> { // Tie
                        this@MainActivity.binding.status.text = "You both chose ${mc.name}"
                    }
                    else -> { // Loss
                        this@MainActivity.binding.status.text = "${mc.name} loses to ${oc.name}"
                        this@MainActivity.opponentScore++
                    }
                }
                this@MainActivity.binding.score.text = "${this@MainActivity.myScore} : ${this@MainActivity.opponentScore}"
                this@MainActivity.myChoice = null
                this@MainActivity.opponentChoice = null
                setGameControllerEnabled(true)
            }
        }
    }

    // Callbacks for connections to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Accepting a connection means you want to receive messages. Hence, the API expects
            // that you attach a PayloadCall to the acceptance
            this@MainActivity.connectionsClient.acceptConnection(endpointId, this@MainActivity.payloadCallback)
            this@MainActivity.opponentName = "Opponent\n(${info.endpointName})"
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                this@MainActivity.connectionsClient.stopAdvertising()
                this@MainActivity.connectionsClient.stopDiscovery()
                this@MainActivity.opponentEndpointId = endpointId
                this@MainActivity.binding.opponentName.text = this@MainActivity.opponentName
                this@MainActivity.binding.status.text = "Connected"
                setGameControllerEnabled(true) // we can start playing
            }
        }

        override fun onDisconnected(endpointId: String) {
            this@MainActivity.resetGame()
        }
    }

    // Callbacks for finding other devices
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            this@MainActivity.connectionsClient.requestConnection(this@MainActivity.myCodeName, endpointId, this@MainActivity.connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(this.binding.root)
        this.connectionsClient = Nearby.getConnectionsClient(this)

        this.binding.myName.text = "You\n(${this.myCodeName})"
        this.binding.findOpponent.setOnClickListener {
            this.startAdvertising()
            this.startDiscovery()
            this.binding.status.text = "Searching for opponents..."
            // "find opponents" is the opposite of "disconnect" so they don't both need to be
            // visible at the same time
            this.binding.findOpponent.visibility = View.GONE
            this.binding.disconnect.visibility = View.VISIBLE
        }
        // wire the controller buttons
        this.binding.apply {
            this.rock.setOnClickListener { sendGameChoice(GameChoice.ROCK) }
            this.paper.setOnClickListener { sendGameChoice(GameChoice.PAPER) }
            this.scissors.setOnClickListener { sendGameChoice(GameChoice.SCISSORS) }
        }
        this.binding.disconnect.setOnClickListener {
            this.opponentEndpointId?.let { this.connectionsClient.disconnectFromEndpoint(it) }
            this.resetGame()
        }

        this.resetGame() // we are about to start a new game
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(this.STRATEGY).build()
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        this.connectionsClient.startAdvertising(
            this.myCodeName,
            packageName,
            this.connectionLifecycleCallback,
            options
        )
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_REQUIRED_PERMISSIONS
            )
        }
    }

    @CallSuper
    override fun onStop(){
        this.connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
        }
        this.resetGame()
        super.onStop()
    }

    @CallSuper
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val errMsg = "Cannot start without required permissions"
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            grantResults.forEach {
                if (it == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }
            this.recreate()
        }
    }

    /** Sends the user's selection of rock, paper, or scissors to the opponent. */
    private fun sendGameChoice(choice: GameChoice) {
        this.myChoice = choice
        this.connectionsClient.sendPayload(
            this.opponentEndpointId!!,
            Payload.fromBytes(choice.name.toByteArray(UTF_8))
        )
        this.binding.status.text = "You chose ${choice.name}"
        // For fair play, we will disable the game controller so that users don't change their
        // choice in the middle of a game.
        this.setGameControllerEnabled(false)
    }

    /**
     * Enables/Disables the rock, paper and scissors buttons. Disabling the game controller
     * prevents users from changing their minds after making a choice.
     */
    private fun setGameControllerEnabled(state: Boolean) {
        this.binding.apply {
            this.rock.isEnabled = state
            this.paper.isEnabled = state
            this.scissors.isEnabled = state
        }
    }

    /** Wipes all game state and updates the UI accordingly. */
    private fun resetGame() {
        // reset data
        this.opponentEndpointId = null
        this.opponentName = null
        this.opponentChoice = null
        this.opponentScore = 0
        this.myChoice = null
        this.myScore = 0
        // reset state of views
        this.binding.disconnect.visibility = View.GONE
        this.binding.findOpponent.visibility = View.VISIBLE
        this.setGameControllerEnabled(false)
        this.binding.opponentName.text="opponent\n(none yet)"
        this.binding.status.text ="..."
        this.binding.score.text = ":"
    }

    private fun startDiscovery(){
        val options = DiscoveryOptions.Builder().setStrategy(this.STRATEGY).build()
        this.connectionsClient.startDiscovery(packageName, this.endpointDiscoveryCallback,options)
    }
}