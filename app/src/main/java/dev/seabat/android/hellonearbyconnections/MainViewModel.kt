package dev.seabat.android.hellonearbyconnection

import android.app.Activity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import dev.seabat.android.hellonearbyconnections.CodenameGenerator
import dev.seabat.android.hellonearbyconnections.GameChoiceEnum
import dev.seabat.android.hellonearbyconnections.NearbyConnections

class MainViewModel : ViewModel(), NearbyConnections.PlayMatchListener {

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

    fun setupNearbyConnections(activity: Activity) {
        this.nearbyConnections = NearbyConnections.Builder(activity, this, this.myName).create()
    }

    fun findOpponent() {
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
                this._statusText.value = "${mc.name} beats ${oc.name}"
                this._myScore++
            }
            mc == oc -> { // Tie
                this._statusText.value = "You both chose ${mc.name}"
            }
            else -> { // Loss
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