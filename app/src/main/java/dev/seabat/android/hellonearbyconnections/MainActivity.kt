package dev.seabat.android.hellonearbyconnections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.CallSuper
import com.google.android.gms.nearby.connection.*
import dev.seabat.android.hellonearbyconnections.databinding.ActivityMainBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dev.seabat.android.hellonearbyconnection.MainViewModel

class MainActivity : AppCompatActivity() {
    /**
     * The request code for verifying our call to [requestPermissions]. Recall that calling
     * [requestPermissions] leads to a callback to [onRequestPermissionsResult]
     */
    private val REQUEST_CODE_REQUIRED_PERMISSIONS = 1

    /**
     * This is for wiring and interacting with the UI views.
     */
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        this.viewModel.setupNearbyConnections(this)
        this.binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(this.binding.root)

        this.setupDisplay()
        this.setupListener()
        this.setupObserver()

        this.viewModel.resetGame() // we are about to start a new game
    }

    private fun setupDisplay() {
        this.binding.also {
            it.myName.text = this.viewModel.myNameText.value
        }
    }

    private fun setupObserver() {
        this.viewModel.also {
            it.disconnectVisibility.observe(this, Observer { visibility ->
                if (visibility) {
                    this.binding.disconnect.visibility = View.VISIBLE
                } else {
                    this.binding.disconnect.visibility = View.GONE
                }
            })
            it.findOpponentVisibility.observe(this, Observer { visibility ->
                if (visibility) {
                    this.binding.findOpponent.visibility = View.VISIBLE
                } else {
                    this.binding.findOpponent.visibility = View.GONE
                }
            })
            it.rockEnabled.observe(this, Observer { enabled ->
                this.binding.rock.isEnabled = enabled
            })
            it.scissorsEnabled.observe(this, Observer {  enabled ->
                this.binding.scissors.isEnabled = enabled
            })
            it.paperEnabled.observe(this, Observer { enabled ->
                this.binding.paper.isEnabled = enabled
            })
            it.opponentNameText.observe(this, Observer { text ->
                this.binding.opponentName.text = text
            })
            it.statusText.observe(this, Observer { text ->
                this.binding.status.text = text
            })
            it.scoreText.observe(this, Observer { text ->
                this.binding.score.text = text
            })
        }
    }

    private fun setupListener() {
        this.binding.also {
            it.findOpponent.setOnClickListener {
                this.viewModel.findOpponent()
            }
            it.disconnect.setOnClickListener {
                this.viewModel.disconnect()
            }

            // wire the controller buttons
            it.rock.setOnClickListener {
                this.viewModel.sendGameChoice(GameChoiceEnum.ROCK)
            }
            it.paper.setOnClickListener {
                this.viewModel.sendGameChoice(GameChoiceEnum.PAPER)
            }
            it.scissors.setOnClickListener {
                this.viewModel.sendGameChoice(GameChoiceEnum.SCISSORS)
            }
        }
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
        this.viewModel.terminateConnection()
        this.viewModel.resetGame()
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
}