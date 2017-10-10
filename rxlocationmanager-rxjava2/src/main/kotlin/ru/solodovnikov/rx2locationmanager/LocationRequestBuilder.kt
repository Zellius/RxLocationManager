package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.Location
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.functions.Function
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseLocationRequestBuilder] based on rxJava2
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
     * @param behaviors extra behaviors
     *
     * @return same builder
     */
    @JvmOverloads
    fun addRequestLocation(provider: String,
                           timeOut: LocationTime? = null,
                           vararg behaviors: SingleBehavior): LocationRequestBuilder =
            rxLocationManager.requestLocation(provider, timeOut, *behaviors)
                    .toMaybe()
                    .toObservable()
                    .onErrorResumeNext(Function {
                        when (it) {
                            is TimeoutException,
                            is ProviderDisabledException,
                            is IgnorableException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    })
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
     * @param behaviors extra behaviors
     *
     * @return same builder
     */
    @JvmOverloads
    fun addLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg behaviors: MaybeBehavior): LocationRequestBuilder =
            rxLocationManager.getLastLocation(provider, howOldCanBe, *behaviors)
                    .toObservable()
                    .onErrorResumeNext(Function {
                        when (it) {
                            is ElderLocationException,
                            is IgnorableException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    })
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
     * @return It will emit [defaultLocation] if it not null and final observable is empty.
     */
    fun create(): Maybe<Location> =
            resultObservable.firstElement()
                    .compose { if (defaultLocation != null) it.defaultIfEmpty(defaultLocation) else it }
}