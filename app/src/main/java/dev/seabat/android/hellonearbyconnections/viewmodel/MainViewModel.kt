package dev.seabat.android.hellonearbyconnections.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import dev.seabat.android.hellonearbyconnections.model.game.CodenameGenerator
import dev.seabat.android.hellonearbyconnections.model.game.GameChoiceEnum
import dev.seabat.android.hellonearbyconnections.model.neaby.NearbyConnections
import dev.seabat.android.hellonearbyconnections.model.neaby.NearbyConnectionsPermissionChecker

class MainViewModel : ViewModel(), NearbyConnections.PlayMatchListener {
    // objects

    companion object {
        const val TAG = "NEARBY_VIEWMODEL"
    }


    // properties

    /*
    The following variables are for tracking our own data
    */
    private val _myName = CodenameGenerator.generate()
    val myName get() = _myName
    private var _myScore = 0
    val myScore get() = _myScore
    private var _myChoice: GameChoiceEnum? = null
    val myChoice get() = _myChoice

    /*
     The following variables are convenient ways of tracking the data of the opponent that we
     choose to play against.
    */
    private var _opponentName: String? = null
    val opponentName get() = _opponentName
    private var _opponentEndpointId: String? = null
    val opponentEndpointId get() = _opponentEndpointId
    private var _opponentScore = 0
    val opponentScore get() = _opponentScore
    private var _opponentChoice: GameChoiceEnum? = null
    val opponentChoice get() = _opponentChoice

    /**
     * Text 自分の名前
     */
    private val _myNameText: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also { it.value = "You\n(${this.myName})" }
    }
    val myNameText: LiveData<String>
        get() = _myNameText

    /**
     * Text 対戦相手の名前
     */
    private val _opponentNameText: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also { it.value = "Opponent (codeName)" }
    }
    val opponentNameText: LiveData<String>
        get() = _opponentNameText

    /**
     * Text 状態
     */
    private val _statusText: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also { it.value = "Searching for opponents..." }
    }
    val statusText: LiveData<String>
        get() = _statusText

    /**
     * Text スコア
     */
    private val _scoreText: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also { it.value = ":" }
    }
    val scoreText: LiveData<String>
        get() = _scoreText

    /**
     *  Button グー
     */
    private val _rockEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().also { it.value = false }
    }
    val rockEnabled: LiveData<Boolean>
        get() = _rockEnabled

    /**
     *  Button チョキ
     */
    private val _scissorsEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().also { it.value = false }
    }
    val scissorsEnabled: LiveData<Boolean>
        get() = _scissorsEnabled

    /**
     *  Button パー
     */
    private val _paperEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().also { it.value = false }
    }
    val paperEnabled: LiveData<Boolean>
        get() = _paperEnabled

    /**
     *  Button 切断 有効・無効
     */
    private val _disconnectVisibility: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().also { it.value = false }
    }
    val disconnectVisibility: LiveData<Boolean>
        get() = _disconnectVisibility

    /**
     *  Button 探索 有効・無効
     */
    private val _findOpponentVisibility: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().also { it.value = false }
    }
    val findOpponentVisibility: LiveData<Boolean>
        get() = _findOpponentVisibility

    /**
     * Nearby Connection API を操作するクラス
     */
    private var nearbyConnections: NearbyConnections? = null


    // methods

    fun setupNearbyConnections(serviceId: String, getter: NearbyConnections.ConnectionsClientReferCallback) {
        this.nearbyConnections =
            NearbyConnections.Builder(this, getter, serviceId, this.myName).create()
    }

    /**
     * Nearby Connection API を使用するために必要なパーミッションをリクエストする
     */
    fun requestNearbyConnectionPermission(checker: NearbyConnectionsPermissionChecker) {
        checker.check { result ->
            if(result == NearbyConnectionsPermissionChecker.Result.GRANTED){
                this.findOpponent()
            }
        }
    }

    /**
     * Nearby Connection で相手に接続する
     * NOTE: Nearby Connection の接続開始時は毎回パーミッションをチェックを実施し、
     *       パーミッションチェック後に接続を開始する
     */
    private fun findOpponent() {
        this.nearbyConnections?.startDiscovery()
        this.nearbyConnections?.startAdvertising()
        this._statusText.value = "Searching for opponents..."
        // "find opponents" is the opposite of "disconnect" so they don't both need to be
        // visible at the same time
        this._findOpponentVisibility.value = false
        this._disconnectVisibility.value = true
    }

    fun disconnect() {
        this.opponentEndpointId?.let {
            this.nearbyConnections?.disconnectFromEndpoint(it)
        }
        this.resetGame()
    }

    fun terminateConnection() {
        this.nearbyConnections?.terminateConnection()
    }

    /** Sends the user's selection of rock, paper, or scissors to the opponent. */
    fun sendGameChoice(choice: GameChoiceEnum) {
        this.opponentEndpointId?.let {
            this._myChoice = choice
            this.nearbyConnections?.sendGameChoice(choice, this.opponentEndpointId!!)
            this._statusText.value = "You chose ${choice.name}"
            // For fair play, we will disable the game controller so that users don't change their
            // choice in the middle of a game.
            this.setGameControllerEnabled(false)
        }
    }

    /**
     * Enables/Disables the rock, paper and scissors buttons. Disabling the game controller
     * prevents users from changing their minds after making a choice.
     */
    private fun setGameControllerEnabled(state: Boolean) {
        this._rockEnabled.value = state
        this._paperEnabled.value = state
        this._scissorsEnabled.value = state
    }

    /** Wipes all game state and updates the UI accordingly. */
    fun resetGame() {
        // reset data
        this._opponentEndpointId = null
        this._opponentName = null
        this._opponentChoice = null
        this._opponentScore = 0
        this._myChoice = null
        this._myScore = 0
        // reset state of views
        this._disconnectVisibility.value = false
        this._findOpponentVisibility.value = true
        this.setGameControllerEnabled(false)
        this._opponentNameText.value = "opponent\n(none yet)"
        this._statusText.value = "..."
        this._scoreText.value = ":"
    }

    override fun onReceiveOpponentChoice(biteArray: ByteArray) {
        this._opponentChoice = GameChoiceEnum.valueOf(String(biteArray, Charsets.UTF_8))
    }

    /**
     * 自分と相手の選択(グー・チョキ・パー)が揃っている場合はジャッジする
     */
    override fun onJudgeIfPossible(){
        if (this.myChoice == null || this.opponentChoice == null) {
            return
        }

        val mc = this.myChoice!!
        val oc = this.opponentChoice!!
        when {
            mc.beats(oc) -> { // Win!
                Log.d(TAG, "Win")
                this._statusText.value = "${mc.name} beats ${oc.name}"
                this._myScore++
            }
            mc == oc -> { // Tie
                Log.d(TAG, "Tie")
                this._statusText.value = "You both chose ${mc.name}"
            }
            else -> { // Loss
                Log.d(TAG, "Lose")
                this._statusText.value = "${mc.name} loses to ${oc.name}"
                this._opponentScore++
            }
        }
        this._scoreText.value = "${this.myScore} : ${this.opponentScore}"
        this._myChoice = null
        this._opponentChoice = null
        setGameControllerEnabled(true)
    }

    override fun onReceiveOpponentName(opponentName: String) {
        this._opponentName = "Opponent\n(${opponentName})"
    }

    override fun onConnectWithOpponent(endpointId: String) {
        this._opponentEndpointId = endpointId
        this._opponentNameText.value = this.opponentName
        this._statusText.value = "Connected"
        setGameControllerEnabled(true) // we can start playing
    }

    override fun onDisconnectWithOpponent() {
        this.resetGame()
    }
}