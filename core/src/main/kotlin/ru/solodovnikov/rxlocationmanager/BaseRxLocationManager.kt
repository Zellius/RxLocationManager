package ru.solodovnikov.rxlocationmanager

import android.location.LocationManager
import java.util.concurrent.TimeoutException


abstract class BaseRxLocationManager<out SINGLE, out MAYBE> internal constructor(protected val locationManager: LocationManager) {
    /**
     * Get last location from specific provider
     * Observable will emit [ElderLocationException] if [howOldCanBe] is not null and location time is not valid.
     *
     * @param provider provider name
     * @param howOldCanBe how old a location can be
     * @return observable that emit last known location. May emit null
     * @see ElderLocationException
     * @see ProviderHasNoLastLocationException
     */
    @JvmOverloads
    fun getLastLocation(provider: String, howOldCanBe: LocationTime? = null) = baseGetLastLocation(provider, howOldCanBe)

    protected abstract fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?): MAYBE

    /**
     * Try to get current location by specific provider.
     * Observable will emit [TimeoutException] in case of timeOut if [timeOut] object is not null.
     * Observable will emit [ProviderDisabledException] if provider is disabled
     *
     * @param provider provider name
     * @param timeOut  optional request timeout
     * @return observable that emit current location
     * @see TimeoutException
     * @see ProviderDisabledException
     */
    @JvmOverloads
    fun requestLocation(provider: String, timeOut: LocationTime? = null) = baseRequestLocation(provider, timeOut)

    protected abstract fun baseRequestLocation(provider: String, timeOut: LocationTime?): SINGLE
}