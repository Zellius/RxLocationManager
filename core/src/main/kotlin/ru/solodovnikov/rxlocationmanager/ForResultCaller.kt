package ru.solodovnikov.rxlocationmanager

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle

interface ForResultCaller {
    fun startActivityForResult(data: Intent, requestCode: Int)

    fun startIntentSenderForResult(intent: IntentSender, requestCode: Int,
                                   fillInIntent: Intent?, flagsMask: Int, flagsValues: Int,
                                   extraFlags: Int, options: Bundle?)
}