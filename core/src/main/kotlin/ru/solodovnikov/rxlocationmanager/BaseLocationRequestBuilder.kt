package ru.solodovnikov.rxlocationmanager

import android.location.Location

/**
 * Abstract class used just to implement rxJava1 and rxJava2
 */
abstract class BaseLocationRequestBuilder<SINGLE, MAYBE, SINGLE_TRANSFORMER, MAYBE_TRANSFORMER, out BUILDER : BaseLocationRequestBuilder<SINGLE, MAYBE, SINGLE_TRANSFORMER, MAYBE_TRANSFORMER, BUILDER>>
(protected val rxLocationManager: BaseRxLocationManager<SINGLE, MAYBE, SINGLE_TRANSFORMER, MAYBE_TRANSFORMER>) {
    protected var defaultLocation: Location? = null
        private set

    /**
     * Try to get current location by specific [provider].
     * It will ignore any library exceptions (e.g [ProviderDisabledException]).
     * But will fall if any other exception will occur. This can be changed via [transformers].
     *
     * @param provider    provider name
     * @param timeOut     request timeout
     * @param transformers extra transformers
     *
     * @return same builder
     * @see baseAddRequestLocation
     */
    @JvmOverloads
    fun addRequestLocation(provider: String,
                           timeOut: LocationTime? = null,
                           vararg transformers: SINGLE_TRANSFORMER): BUILDER =
            baseAddRequestLocation(provider, timeOut, transformers)

    /**
     * Get last location from specific [provider].
     * It will ignore any library exceptions (e.g [ElderLocationException]).
     * But will fall if any other exception will occur. This can be changed via [transformers].
     *
     * @param provider    provider name
     * @param howOldCanBe how old a location can be
     * @param transformers extra transformers
     *
     * @return same builder
     * @see baseAddLastLocation
     */
    @JvmOverloads
    fun addLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg transformers: MAYBE_TRANSFORMER): BUILDER =
            baseAddLastLocation(provider, howOldCanBe, transformers)


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
                                                  transformers: Array<out SINGLE_TRANSFORMER>): BUILDER

    protected abstract fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime? = null,
                                               transformers: Array<out MAYBE_TRANSFORMER>): BUILDER

    /**
     * Construct final observable.
     */
    abstract fun create(): MAYBE
}