package ru.solodovnikov.rxlocationmanager.kotlin

import android.location.Location

class ProviderDisabledException(val provider: String) : Throwable("The $provider provider is disabled")

class ElderLocationException(val location: Location) : Throwable("The location is too old")