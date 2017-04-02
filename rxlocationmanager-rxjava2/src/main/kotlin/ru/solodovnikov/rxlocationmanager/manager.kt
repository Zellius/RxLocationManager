package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import java.util.concurrent.TimeoutException

class RxLocationManager internal constructor(locationManager: LocationManager,
                                             private val scheduler: Scheduler = AndroidSchedulers.mainThread()) : BaseRxLocationManager<Single<Location>, Maybe<Location>>(locationManager) {
    constructor(context: Context) : this(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)


    override fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?): Maybe<Location> =
            Maybe.fromCallable { locationManager.getLastKnownLocation(provider) ?: throw ProviderHasNoLastLocationException(provider) }
                    .onErrorComplete { it is ProviderHasNoLastLocationException }
                    .compose {
                        if (howOldCanBe == null) it else it.map {
                            if (!it.isNotOld(howOldCanBe)) {
                                throw ElderLocationException(it)
                            }
                            it

                        }
                    }.compose { applySchedulers(it) }

    override fun baseRequestLocation(provider: String, timeOut: LocationTime?): Single<Location> =
            Single.create(RxLocationListener(locationManager, provider))
                    .compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .compose { applySchedulers(it) }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler).observeOn(scheduler)

    private fun applySchedulers(m: Maybe<Location>) = m.subscribeOn(scheduler).observeOn(scheduler)

    private class RxLocationListener(val locationManager: LocationManager, val provider: String) : SingleOnSubscribe<Location> {

        override fun subscribe(emitter: SingleEmitter<Location>) {
            if (locationManager.isProviderEnabled(provider)) {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        emitter.onSuccess(location)
                    }

                    override fun onProviderDisabled(p: String?) {
                        if (provider == p) {
                            emitter.onError(ProviderDisabledException(provider))
                        }
                    }

                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                    override fun onProviderEnabled(p: String?) {}
                }

                locationManager.requestSingleUpdate(provider, locationListener, null)

                emitter.setCancellable { locationManager.removeUpdates(locationListener) }

            } else {
                emitter.onError(ProviderDisabledException(provider))
            }
        }
    }
}

class LocationRequestBuilder internal constructor(rxLocationManager: RxLocationManager,
                                                  private val scheduler: Scheduler = AndroidSchedulers.mainThread()) : BaseLocationRequestBuilder<Single<Location>, Maybe<Location>, ObservableTransformer<Location, Location>>(rxLocationManager) {
    private val observables: MutableList<Observable<Location>> = arrayListOf()

    constructor(context: Context) : this(RxLocationManager(context))

    override fun baseAddRequestLocation(provider: String, timeOut: LocationTime?,
                                        transformer: ObservableTransformer<Location, Location>?): BaseLocationRequestBuilder<Single<Location>, Maybe<Location>, ObservableTransformer<Location, Location>> {
        val o = rxLocationManager.requestLocation(provider, timeOut)
                .toObservable()
                .onErrorResumeNext(Function {
                    when (it) {
                        is TimeoutException, is ProviderDisabledException -> Observable.empty<Location>()
                        else -> Observable.error<Location>(it)
                    }
                })

        observables.add(if (transformer != null) o.compose(transformer) else o)

        return this
    }

    override fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime?,
                                     transformer: ObservableTransformer<Location, Location>?): BaseLocationRequestBuilder<Single<Location>, Maybe<Location>, ObservableTransformer<Location, Location>> {
        val o = rxLocationManager.getLastLocation(provider, howOldCanBe)
                .toObservable()
                .onErrorResumeNext(Function {
                    when (it) {
                        is ElderLocationException -> Observable.empty<Location>()
                        else -> Observable.error<Location>(it)
                    }
                })

        observables.add(if (transformer != null) o.compose(transformer) else o)

        return this
    }

    override fun create(): Maybe<Location> {

        var result = Observable.empty<Location>()

        observables.forEach {
            result = result.concatWith(it.compose {
                it.onErrorResumeNext(Function {
                    if (returnDefaultLocationOnError) {
                        Observable.empty<Location>()
                    } else {
                        Observable.error<Location>(it)
                    }
                })
            })
        }

        return result.firstElement()
                .compose { if (defaultLocation != null) it.defaultIfEmpty(defaultLocation) else it }
                .observeOn(scheduler)
    }
}

