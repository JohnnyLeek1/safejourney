package com.gismo.safejourney

import android.app.AlertDialog
import android.app.Dialog
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TimePicker
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment


class TimeSelectDialogFragment(val isWalking: Boolean) : DialogFragment() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.time_select_dialog, null)
        val timePicker = view.findViewById<TimePicker>(R.id.time_picker)

        val c: Calendar = Calendar.getInstance()
        timePicker.hour = c.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = c.get(Calendar.MINUTE)

        val radio = view.findViewById<RadioGroup>(R.id.walkBikeRadioGroup)
        if (isWalking) radio.check(R.id.walkRadio) else radio.check(R.id.bikeRadio)

        return AlertDialog.Builder(requireContext())
            .setPositiveButton("Ok") { _,_ -> }
            .setView(view)
            .create()
    }

    companion object {
        const val TAG = "TimeSelectDialogFragment"
    }
}