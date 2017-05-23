package ru.solodovnikov.rxlocationmanager

import android.location.Location

/**
 * Abstract class used just to implement rxJava1 and rxJava2
 */
abstract class BaseLocationRequestBuilder<out SINGLE, out MAYBE, in TRANSFORMER, out BUILDER : BaseLocationRequestBuilder<SINGLE, MAYBE, TRANSFORMER, BUILDER>> internal constructor(protected val rxLocationManager: BaseRxLocationManager<SINGLE, MAYBE>) {
    protected var defaultLocation: Location? = null
        private set

    /**
     * Try to get current location by specific [provider].
     * It will ignore any library exceptions (e.g [ProviderDisabledException]).
     * But will fall if any other exception will occur. This can be changed via [transformer].
     *
     * @param provider    provider name
     * @param timeOut     request timeout
     * @param transformer extra transformer
     *
     * @return same builder
     * @see baseAddRequestLocation
     */
    @JvmOverloads
    fun addRequestLocation(provider: String,
                           timeOut: LocationTime? = null,
                           transformer: TRANSFORMER? = null): BUILDER =
            baseAddRequestLocation(provider, timeOut, transformer)

    /**
     * Get last location from specific [provider].
     * It will ignore any library exceptions (e.g [ElderLocationException]).
     * But will fall if any other exception will occur. This can be changed via [transformer].
     *
     * @param provider    provider name
     * @param howOldCanBe how old a location can be
     * @param transformer extra transformer
     *
     * @return same builder
     * @see baseAddLastLocation
     */
    @JvmOverloads
    fun addLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        transformer: TRANSFORMER? = null): BUILDER =
            baseAddLastLocation(provider, howOldCanBe, transformer)


    /**
     * Set location that will be returned in case of empty observable
     *
     * @param defaultLocation default location
     * @return same builder
     */
    @Suppress("UNCHECKED_CAST")
    fun setDefaultLocation(defaultLocation: Location?): BUILDER =
            (this as BUILDER).also {
                this.defaultLocation = defaultLocation
            }

    protected abstract fun baseAddRequestLocation(provider: String, timeOut: LocationTime? = null,
                                                  transformer: TRANSFORMER? = null): BUILDER

    protected abstract fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                                               transformer: TRANSFORMER? = null): BUILDER

    /**
     * Construct final observable.
     */
    abstract fun create(): MAYBE
}