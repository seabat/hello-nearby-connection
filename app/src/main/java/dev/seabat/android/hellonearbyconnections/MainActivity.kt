package dev.seabat.android.hellonearbyconnections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import com.google.android.gms.nearby.connection.*
import dev.seabat.android.hellonearbyconnections.databinding.ActivityMainBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.nearby.Nearby
import dev.seabat.android.hellonearbyconnection.MainViewModel
import dev.seabat.android.hellonearbyconnection.dialog.PermissionCheckDialog

class MainActivity : AppCompatActivity(), PermissionCheckDialog.PermissionCheckDialogListener {
    companion object {
        const val TAG = "MAIN_ACTIVITY"
    }

    /**
     * Nearby Connection API で通信可能か
     */
    private var nearbyConnectEnabled = false

    /**
     * アプリを動作させるのに必要なパーミッション
     */
    private val NEARBY_CONECTION_PERMISSIONS =
        when {
            Build.VERSION.SDK_INT>= 31 ->
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN)
            else ->
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        this.binding = ActivityMainBinding.inflate(layoutInflater)
        this.connectionsClient = Nearby.getConnectionsClient(this)

        setContentView(this.binding.root)

        this.setupRepository()
        this.setupDisplay()
        this.setupListener()
        this.setupObserver()

        this.viewModel.resetGame() // we are about to start a new game
    }

    private fun setupRepository() {
        this.viewModel.setupNearbyConnections(this.packageName) { this@MainActivity.connectionsClient }
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
                if (this.nearbyConnectEnabled) {
                    this.viewModel.findOpponent()
                } else {
                    //TODO: ダイアログを表示
                }
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
        this.requestPermission()
    }

    @CallSuper
    override fun onStop(){
        this.viewModel.terminateConnection()
        this.viewModel.resetGame()
        super.onStop()
    }

    private fun requestPermission() {
        // 許可されていないパーミッションの一覧を取得する
        NEARBY_CONECTION_PERMISSIONS.filter {
                permission -> checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }.also { unGranted ->
            Log.d(TAG, "ungranted permission: $unGranted")
            if (unGranted.isEmpty()) {
                this.nearbyConnectEnabled = true
            } else {
                this.requestPermissionLauncher.launch(unGranted.toTypedArray())
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results: Map<String, Boolean> ->
            // 許可されていないパーミッションの一覧を取得する
            // NOTE: 許可されていないパーミッションを表示できるよう、「許可されている」ではなく
            //       「許可されていない」一覧を取得する。
            NEARBY_CONECTION_PERMISSIONS.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.also {
                unGranted ->
                Log.d(TAG, "ungranted permission after request: $unGranted")
                if (unGranted.isEmpty()) {
                    // すべてのパーミッションが許可されたらnearby connection が可能になる
                    this.nearbyConnectEnabled = true
                } else {
                    // 「今後表示しない」を選択した場合、または複数回拒否してパーミッションリクエストダイアログが
                    // 表示できなくなった一覧を取得する
                    unGranted.filter {
                            permission -> !shouldShowRequestPermissionRationale(permission)
                    }.also {
                        multipleDenied ->
                        Log.d(TAG, "multiple denied permission: $multipleDenied")
                        if (multipleDenied.isEmpty()) {
                            this.showDialogForNeverAskPermission()
                        } else {
                            this.showDialogForDeniedPermission()
                        }
                    }
                }
            }
        }

    /**
     * 設定アプリでの権限付与を誘導するダイアログを表示する
     * TODO: 設定アプリを起動させる
     */
    private fun showDialogForNeverAskPermission() {
        val newFragment = PermissionCheckDialog.newInstance(getString(R.string.never_ask_app_permissions))
        newFragment.show(supportFragmentManager, "never_ask")
    }

    /**
     * 設定アプリのアプリ再起動を誘導するダイアログを表示する
     * TODO: アプリを再起動させる
     */
    private fun showDialogForDeniedPermission() {
        val newFragment = PermissionCheckDialog.newInstance(getString(R.string.denied_app_permissions))
        newFragment.show(supportFragmentManager, "denied")
    }

    override fun onDialogPositiveClick(dialog: DialogFragment) {
        // DO nothing
    }
}
