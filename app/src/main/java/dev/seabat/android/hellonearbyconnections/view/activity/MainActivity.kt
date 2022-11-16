package dev.seabat.android.hellonearbyconnections.view.activity

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import com.google.android.gms.nearby.connection.*
import dev.seabat.android.hellonearbyconnections.databinding.ActivityMainBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.nearby.Nearby
import dev.seabat.android.hellonearbyconnections.model.game.GameChoiceEnum
import dev.seabat.android.hellonearbyconnections.viewmodel.MainViewModel
import dev.seabat.android.hellonearbyconnections.model.neaby.NearbyConnectionsPermissionChecker
import dev.seabat.android.hellonearbyconnections.view.dialog.PermissionCheckDialog

class MainActivity : AppCompatActivity(), PermissionCheckDialog.PermissionCheckDialogListener {
    // objects

    companion object {
        const val TAG = "MAIN_ACTIVITY"
    }


    // properties

    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var permissionChecker: NearbyConnectionsPermissionChecker


    // methods

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        this.binding = ActivityMainBinding.inflate(layoutInflater)
        this.permissionChecker = NearbyConnectionsPermissionChecker{ this@MainActivity }

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
                this.viewModel.requestNearbyConnectionPermission(permissionChecker)
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
    }

    @CallSuper
    override fun onStop(){
        this.viewModel.terminateConnection()
        this.viewModel.resetGame()
        super.onStop()
    }

    override fun onDialogPositiveClick(dialog: DialogFragment) {
        // DO nothing
    }
}
