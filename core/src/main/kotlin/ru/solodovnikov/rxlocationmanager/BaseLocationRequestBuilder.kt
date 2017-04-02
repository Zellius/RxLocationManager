package ru.solodovnikov.rxlocationmanager

import android.location.Location

abstract class BaseLocationRequestBuilder<out SINGLE, out MAYBE, in TRANSFORMER> internal constructor(protected val rxLocationManager: BaseRxLocationManager<SINGLE, MAYBE>) {
    protected var defaultLocation: Location? = null
    protected var returnDefaultLocationOnError = false

    /**
     * Try to get current location by specific [provider]
     *
     * @param provider    provider name
     * @param timeOut     optional request timeout
     * @param transformer optional extra transformer
     *
     * @return same builder
     */
    @JvmOverloads
    fun addRequestLocation(provider: String, timeOut: LocationTime? = null,
                           transformer: TRANSFORMER? = null) = baseAddRequestLocation(provider, timeOut, transformer)

    protected abstract fun baseAddRequestLocation(provider: String, timeOut: LocationTime? = null,
                                                  transformer: TRANSFORMER? = null): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER>

    /**
     * Get last location from specific [provider]
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @param transformer optional extra transformer
     * @return same builder
     */
    @JvmOverloads
    fun addLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                        transformer: TRANSFORMER? = null) = baseAddLastLocation(provider, howOldCanBe, transformer)

    protected abstract fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                                               transformer: TRANSFORMER? = null): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER>

    /**
     * Set location that will be returned in case of empty observable
     *
     * @param defaultLocation default location
     * @return same builder
     */
    fun setDefaultLocation(defaultLocation: Location?): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER> {
        this.defaultLocation = defaultLocation
        return this
    }

    /**
     * If [returnDefaultLocationOnError] is true, result observable will emit default location if any exception occur
     *
     * @param returnDefaultLocationOnError emit default location if any exception occur?
     * @return same builder
     */
    fun setReturnDefaultLocationOnError(returnDefaultLocationOnError: Boolean): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER> {
        this.returnDefaultLocationOnError = returnDefaultLocationOnError
        return this
    }

    abstract fun create(): MAYBE
}