package ru.solodovnikov.rxlocationmanager

import android.location.Location

abstract class BaseLocationRequestBuilder<out SINGLE, out MAYBE, in TRANSFORMER_SINGLE, in TRANSFORMER_MAYBE> internal constructor(protected val rxLocationManager: BaseRxLocationManager<SINGLE, MAYBE>) {
    protected var defaultLocation: Location? = null
        private set

    /**
     * Try to get current location by specific [provider].
     * It will ignore any library exceptions (e.g [ProviderDisabledException]).
     * But will fall if any other exception will occur. This can be changed via [transformer].
     *
     * @param provider    provider name
     * @param timeOut     optional request timeout
     * @param transformer optional extra transformer
     *
     * @return same builder
     * @see baseAddRequestLocation
     */
    @JvmOverloads
    fun addRequestLocation(provider: String, timeOut: LocationTime? = null,
                           transformer: TRANSFORMER_SINGLE? = null) = baseAddRequestLocation(provider, timeOut, transformer)

    /**
     * Get last location from specific [provider].
     * It will ignore any library exceptions (e.g [ElderLocationException]).
     * But will fall if any other exception will occur. This can be changed via [transformer].
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @param transformer optional extra transformer
     *
     * @return same builder
     * @see baseAddLastLocation
     */
    @JvmOverloads
    fun addLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                        transformer: TRANSFORMER_MAYBE? = null) = baseAddLastLocation(provider, howOldCanBe, transformer)

    /**
     * Set location that will be returned in case of empty observable
     *
     * @param defaultLocation default location
     * @return same builder
     */
    fun setDefaultLocation(defaultLocation: Location?) = also {
        this.defaultLocation = defaultLocation
    }

    protected abstract fun baseAddRequestLocation(provider: String, timeOut: LocationTime? = null,
                                                  transformer: TRANSFORMER_SINGLE? = null): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER_SINGLE, TRANSFORMER_MAYBE>

    protected abstract fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                                               transformer: TRANSFORMER_MAYBE? = null): BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER_SINGLE, TRANSFORMER_MAYBE>

    /**
     * Construct final observable.
     */
    abstract fun create(): MAYBE
}