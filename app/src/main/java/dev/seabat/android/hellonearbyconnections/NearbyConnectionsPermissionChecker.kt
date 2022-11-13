package dev.seabat.android.hellonearbyconnections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import dev.seabat.android.hellonearbyconnections.dialog.PermissionCheckDialog

/**
 * Nearby Connection API を使用するために必要な permission をチェック・取得する
 *
 */
class NearbyConnectionsPermissionChecker(referCallback: ActivityReferCallback) {
    // interfaces

    fun interface ActivityReferCallback {
        fun referTo(): AppCompatActivity
    }

    fun interface PermissionCheckListener {
        fun onComplete(result: Result)
    }


    // inner classes

    /**
     * permission リクエスト結果
     */
    enum class Result {
        GRANTED, DENIED
    }


    // objects

    companion object {
        const val TAG = "NEARBY_PERMISSION"
    }


    // properties

    /**
     * Nearby Connection API を動作させるのに必要なパーミッション
     */
    private val NEARBY_CONNECTIONS_PERMISSIONS =
        when {
            Build.VERSION.SDK_INT>= 31 ->
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN)
            else ->
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /**
     * permission チェック結果を受信するリスナー
     */
    private var checkListener: PermissionCheckListener? = null

    private val referCallback:ActivityReferCallback

    /**
     * Activity
     * NOTE: コードを短縮するための読み取り専用プロパティ
     */
    private val activity get() = referCallback.referTo()


    // constructor

    init {
        // WARNING: Component.Activity#registerForActivityResult は LifecycleOwners が
        //          STARTED の前に呼び出す必要がある。よって [referCallback] で参照する Activity の
        //          onCreate 、もししくはそれより早いタイミングで本クラスのインスタンスを生成する必要がある。
        if (referCallback.referTo().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            throw IllegalStateException("referCallback で参照する Activity の onCreate のタイミング以前に本クラスのインスタンスを生成してください。")
        }

        // WARNING: permissin リクエストがブロックされている場合に表示するダイアログは
        //          PermissionCheckDialogListener を implements している Activity が必要。
        try {
            referCallback.referTo() as PermissionCheckDialog.PermissionCheckDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException((referCallback.referTo().toString() +
                    " must implement PermissionCheckDialogListener"))
        }

        this.referCallback = referCallback
    }


    // methods

    /**
     * permission をチェックする
     *
     * NOTE: すでに permission が許可されている場合と、 permission が未設定もしくは拒否されている場合の
     *       チェック結果を返すタイミングを同じにするため、同期的に checkSelfPermission を実行せず、
     *       問答無用でパーミションをリクエストする。
     */
    fun check(checkListener: PermissionCheckListener) {
        this.checkListener = checkListener
        this.requestPermissionLauncher.launch(NEARBY_CONNECTIONS_PERMISSIONS)
    }

    private val requestPermissionLauncher =
        this.activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results: Map<String, Boolean> ->
            // 許可されていないパーミッションの一覧を取得する
            // NOTE: 許可されていないパーミッションを表示できるよう、「許可されている」ではなく
            //       「許可されていない」一覧を取得する。
            NEARBY_CONNECTIONS_PERMISSIONS.filter {
                this.activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.also { unGranted ->
                Log.d(TAG, "ungranted permission after request: $unGranted")
                if (unGranted.isEmpty()) {
                    // すべてのパーミッションが許可されたらnearby connections が可能になる
                    this.checkListener?.onComplete(Result.GRANTED)
                } else {
                    // 「今後表示しない」を選択した場合、または複数回拒否してパーミッションリクエストダイアログが
                    // 表示できなくなった一覧を取得する
                    unGranted.filter {
                            permission -> !this.activity.shouldShowRequestPermissionRationale(permission)
                    }.also { multipleDenied ->
                        Log.d(TAG, "multiple denied permission: $multipleDenied")
                        if (multipleDenied.isEmpty()) {
                            this.showDialogForNeverAskPermission()
                        } else {
                            this.showDialogForDeniedPermission()
                        }
                    }
                    this.checkListener?.onComplete(Result.DENIED)
                }
            }
        }
    /**
     * 設定アプリでの権限付与を誘導するダイアログを表示する
     * TODO: 設定アプリを起動させる
     */
    private fun showDialogForNeverAskPermission() {
        val newFragment = PermissionCheckDialog.newInstance(this.activity.getString(R.string.never_ask_app_permissions))
        newFragment.show(this.activity.supportFragmentManager, "never_ask")
    }

    /**
     * 設定アプリのアプリ再起動を誘導するダイアログを表示する
     * TODO: アプリを再起動させる
     */
    private fun showDialogForDeniedPermission() {
        val newFragment = PermissionCheckDialog.newInstance(this.activity.getString(R.string.denied_app_permissions))
        newFragment.show(this.activity.supportFragmentManager, "denied")
    }
}