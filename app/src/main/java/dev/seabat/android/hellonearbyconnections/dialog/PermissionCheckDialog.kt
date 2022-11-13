package dev.seabat.android.hellonearbyconnection.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import dev.seabat.android.hellonearbyconnections.R

class PermissionCheckDialog : DialogFragment() {
    // objects

    companion object {
        const val MESSAGE = "message"
        fun newInstance(message: String): PermissionCheckDialog {
            val fragment = PermissionCheckDialog()
            val args = Bundle()
            args.putString(MESSAGE, message)
            fragment.arguments = args
            return fragment
        }
    }


    // interfaces

    interface PermissionCheckDialogListener {
        fun onDialogPositiveClick(dialog: DialogFragment)
    }


    // properties

    private lateinit var listener: PermissionCheckDialogListener


    // methods

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as PermissionCheckDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException((context.toString() +
                    " must implement PermissionCheckDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val message = requireArguments().getString(MESSAGE, "")

        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage(message)
                .setPositiveButton(
                    R.string.ok,
                    DialogInterface.OnClickListener { dialog, id ->
                        this.listener.onDialogPositiveClick(this)
                    })
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}