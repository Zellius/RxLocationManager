package ru.solodovnikov.rxlocationmanager.kotlin

import android.content.Context
import android.location.LocationManager

class RxLocationManager internal constructor(private val locationManager: LocationManager)
{
    constructor(context: Context) : this(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
}
