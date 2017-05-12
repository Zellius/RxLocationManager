package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava2
 */
class RxLocationManager internal constructor(context: Context,
                                             private val scheduler: Scheduler) : BaseRxLocationManager<Single<Location>, Maybe<Location>>(context) {
    constructor(context: Context) : this(context, AndroidSchedulers.mainThread())

    /**
     * @return Result [Maybe] will not emit any value if location is null.
     * Or it will be emit [ElderLocationException] if [howOldCanBe] not null and location is too old
     */
    override fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?): Maybe<Location> =
            Maybe.fromCallable { locationManager.getLastKnownLocation(provider) ?: throw ProviderHasNoLastLocationException(provider) }
                    .onErrorComplete { it is ProviderHasNoLastLocationException }
                    .compose {
                        if (howOldCanBe != null) {
                            it.doOnSuccess {
                                if (!it.isNotOld(howOldCanBe)) {
                                    throw ElderLocationException(it)
                                }
                            }
                        } else {
                            it
                        }
                    }.compose { applySchedulers(it) }

    /**
     * @return Result [Single] can throw [ProviderDisabledException] or [TimeoutException] if [timeOut] not null
     */
    override fun baseRequestLocation(provider: String, timeOut: LocationTime?): Single<Location> {
        return Single.create(SingleOnSubscribe<Location> {
            if (locationManager.isProviderEnabled(provider)) {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        it.onSuccess(location)
                    }

                    override fun onProviderDisabled(p: String?) {
                        if (provider == p) {
                            it.onError(ProviderDisabledException(provider))
                        }
                    }

                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                    override fun onProviderEnabled(p: String?) {}
                }

                locationManager.requestSingleUpdate(provider, locationListener, null)

                it.setCancellable { locationManager.removeUpdates(locationListener) }

            } else {
                it.onError(ProviderDisabledException(provider))
            }
        }).compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                .compose { applySchedulers(it) }
    }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler)

    private fun applySchedulers(m: Maybe<Location>) = m.subscribeOn(scheduler)
}

/**
 * Implementation of [BaseLocationRequestBuilder] based on rxJava2
 */
class LocationRequestBuilder internal constructor(rxLocationManager: RxLocationManager) : BaseLocationRequestBuilder<Single<Location>, Maybe<Location>, SingleTransformer<Location, Location>, MaybeTransformer<Location, Location>>(rxLocationManager) {
    constructor(context: Context) : this(RxLocationManager(context))

    private var resultObservable = Observable.empty<Location>()

    override fun baseAddRequestLocation(provider: String,
                                        timeOut: LocationTime?,
                                        transformer: SingleTransformer<Location, Location>?) =
            rxLocationManager.requestLocation(provider, timeOut)
                    .compose { if (transformer != null) it.compose(transformer) else it }
                    .toObservable()
                    .onErrorResumeNext(Function {
                        when (it) {
                            is TimeoutException, is ProviderDisabledException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    })
                    .let {
                        resultObservable = resultObservable.concatWith(it)
                        this
                    }

    override fun baseAddLastLocation(provider: String,
                                     howOldCanBe: LocationTime?,
                                     transformer: MaybeTransformer<Location, Location>?) =
            rxLocationManager.getLastLocation(provider, howOldCanBe)
                    .compose { if (transformer != null) it.compose(transformer) else it }
                    .toObservable()
                    .onErrorResumeNext(Function {
                        when (it) {
                            is ElderLocationException -> Observable.empty<Location>()
                            else -> Observable.error<Location>(it)
                        }
                    })
                    .let {
                        resultObservable = resultObservable.concatWith(it)
                        this
                    }

    override fun create(): Maybe<Location> =
            resultObservable.firstElement()
                    .compose { if (defaultLocation != null) it.defaultIfEmpty(defaultLocation) else it }
}

