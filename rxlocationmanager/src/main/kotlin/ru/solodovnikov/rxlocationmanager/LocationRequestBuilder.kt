package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import rx.Observable
import rx.Single
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseLocationRequestBuilder] based on rxJava1
 * @param rxLocationManager manager used in the builder. Used for request runtime permissions.
 */
class LocationRequestBuilder(rxLocationManager: RxLocationManager
) : BaseLocationRequestBuilder<RxLocationManager>(rxLocationManager) {
    /**
     * Use this constructor if you do not need request runtime permissions
     */
    constructor(context: Context) : this(RxLocationManager(context))

    private var resultObservable = Observable.empty<Location>()

    /**
     * Try to get current location by specific [provider].
     * It will ignore any library exceptions (e.g [ProviderDisabledException]).
     * But will fall if any other exception will occur. This can be changed via [behaviors].
     *
     * @param provider    provider name
     * @param timeOut     request timeout
     * @param isNullValid if true, then this request can emit null value
     * @param behaviors extra behaviors
     *
     * @return same builder
     * @see baseAddRequestLocation
     */
    @JvmOverloads
    fun addRequestLocation(provider: String,
                           timeOut: LocationTime? = null,
                           isNullValid: Boolean = false,
                           vararg behaviors: SingleBehavior): LocationRequestBuilder =
            rxLocationManager.requestLocation(provider, timeOut, *arrayOf<SingleBehavior>(ThrowProviderDisabledBehavior(rxLocationManager)).plus(behaviors))
                    .toObservable()
                    .onErrorResumeNext {
                        when (it) {
                            is TimeoutException, is ProviderDisabledException, is IgnorableException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    }
                    .flatMap { if (it == null && !isNullValid) Observable.empty() else Observable.just(it) }
                    .let {
                        resultObservable = resultObservable.concatWith(it)
                        this
                    }

    /**
     * Get last location from specific [provider].
     * It will ignore any library exceptions (e.g [ElderLocationException]).
     * But will fall if any other exception will occur. This can be changed via [behaviors].
     *
     * @param provider    provider name
     * @param howOldCanBe how old a location can be
     * @param isNullValid if true, then this request can emit null value
     * @param behaviors extra behaviors
     *
     * @return same builder
     * @see baseAddLastLocation
     */
    @JvmOverloads
    fun addLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        isNullValid: Boolean = false,
                        vararg behaviors: SingleBehavior): LocationRequestBuilder =
            rxLocationManager.getLastLocation(provider, howOldCanBe, *behaviors)
                    .toObservable()
                    .onErrorResumeNext {
                        when (it) {
                            is ElderLocationException, is IgnorableException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    }
                    .flatMap { if (it == null && !isNullValid) Observable.empty() else Observable.just(it) }
                    .let {
                        resultObservable = resultObservable.concatWith(it)
                        this
                    }


    /**
     * Set location that will be returned in case of empty observable
     *
     * @param defaultLocation default location
     * @return same builder
     */
    @Suppress("UNCHECKED_CAST")
    fun setDefaultLocation(defaultLocation: Location?): LocationRequestBuilder =
            also {
                this.defaultLocation = defaultLocation
            }

    /**
     * Construct final observable.
     *
     * @return It will emit [defaultLocation] if final observable is empty.
     */
    fun create(): Single<Location> =
            resultObservable.firstOrDefault(defaultLocation)
                    .toSingle()
}