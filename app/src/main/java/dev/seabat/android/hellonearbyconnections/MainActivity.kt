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
                opponentChoice = GameChoice.valueOf(String(it, UTF_8))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Determines the winner and updates game state/UI after both players have chosen.
            // Feel free to refactor and extract this code into a different method
            if (update.status == PayloadTransferUpdate.Status.SUCCESS
                && myChoice != null && opponentChoice != null) {
                val mc = myChoice!!
                val oc = opponentChoice!!
                when {
                    mc.beats(oc) -> { // Win!
                        binding.status.text = "${mc.name} beats ${oc.name}"
                        myScore++
                    }
                    mc == oc -> { // Tie
                        binding.status.text = "You both chose ${mc.name}"
                    }
                    else -> { // Loss
                        binding.status.text = "${mc.name} loses to ${oc.name}"
                        opponentScore++
                    }
                }
                binding.score.text = "${myScore} : ${opponentScore}"
                myChoice = null
                opponentChoice = null
                setGameControllerEnabled(true)
            }
        }
    }

    // Callbacks for connections to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Accepting a connection means you want to receive messages. Hence, the API expects
            // that you attach a PayloadCall to the acceptance
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            opponentName = "Opponent\n(${info.endpointName})"
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                opponentEndpointId = endpointId
                binding.opponentName.text = opponentName
                binding.status.text = "Connected"
                setGameControllerEnabled(true) // we can start playing
            }
        }

        override fun onDisconnected(endpointId: String) {
            resetGame()
        }
    }

    // Callbacks for finding other devices
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(myCodeName, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        connectionsClient = Nearby.getConnectionsClient(this)

        binding.myName.text = "You\n(${myCodeName})"
        binding.findOpponent.setOnClickListener {
            startAdvertising()
            startDiscovery()
            binding.status.text = "Searching for opponents..."
            // "find opponents" is the opposite of "disconnect" so they don't both need to be
            // visible at the same time
            binding.findOpponent.visibility = View.GONE
            binding.disconnect.visibility = View.VISIBLE
        }
        // wire the controller buttons
        binding.apply {
            rock.setOnClickListener { sendGameChoice(GameChoice.ROCK) }
            paper.setOnClickListener { sendGameChoice(GameChoice.PAPER) }
            scissors.setOnClickListener { sendGameChoice(GameChoice.SCISSORS) }
        }
        binding.disconnect.setOnClickListener {
            opponentEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
            resetGame()
        }

        resetGame() // we are about to start a new game
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
            myCodeName,
            packageName,
            connectionLifecycleCallback,
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
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
        }
        resetGame()
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
            recreate()
        }
    }

    /** Sends the user's selection of rock, paper, or scissors to the opponent. */
    private fun sendGameChoice(choice: GameChoice) {
        myChoice = choice
        connectionsClient.sendPayload(
            opponentEndpointId!!,
            Payload.fromBytes(choice.name.toByteArray(UTF_8))
        )
        binding.status.text = "You chose ${choice.name}"
        // For fair play, we will disable the game controller so that users don't change their
        // choice in the middle of a game.
        setGameControllerEnabled(false)
    }

    /**
     * Enables/Disables the rock, paper and scissors buttons. Disabling the game controller
     * prevents users from changing their minds after making a choice.
     */
    private fun setGameControllerEnabled(state: Boolean) {
        binding.apply {
            rock.isEnabled = state
            paper.isEnabled = state
            scissors.isEnabled = state
        }
    }

    /** Wipes all game state and updates the UI accordingly. */
    private fun resetGame() {
        // reset data
        opponentEndpointId = null
        opponentName = null
        opponentChoice = null
        opponentScore = 0
        myChoice = null
        myScore = 0
        // reset state of views
        binding.disconnect.visibility = View.GONE
        binding.findOpponent.visibility = View.VISIBLE
        setGameControllerEnabled(false)
        binding.opponentName.text="opponent\n(none yet)"
        binding.status.text ="..."
        binding.score.text = ":"
    }

    private fun startDiscovery(){
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(packageName, endpointDiscoveryCallback,options)
    }
}