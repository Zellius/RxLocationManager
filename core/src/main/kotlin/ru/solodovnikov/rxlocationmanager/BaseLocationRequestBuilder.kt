package ru.solodovnikov.rxlocationmanager

import android.location.Location

abstract class BaseLocationRequestBuilder<out RxLocationManager : BaseRxLocationManager>
(protected val rxLocationManager: RxLocationManager) {
    protected var defaultLocation: Location? = null
}