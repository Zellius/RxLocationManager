package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import rx.Emitter
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import java.util.concurrent.TimeoutException

class RxLocationManager internal constructor(locationManager: LocationManager,
                                             private val scheduler: Scheduler = AndroidSchedulers.mainThread()) : BaseRxLocationManager<Single<Location>, Single<Location>>(locationManager) {
    constructor(context: Context) : this(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    override fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?): Single<Location> =
            Single.fromCallable { locationManager.getLastKnownLocation(provider) ?: throw ProviderHasNoLastLocationException(provider) }
                    .compose {
                        if (howOldCanBe == null) it else it.map {
                            if (it != null && !it.isNotOld(howOldCanBe)) {
                                throw ElderLocationException(it)
                            }
                            it
                        }
                    }.compose { applySchedulers(it) }

    override fun baseRequestLocation(provider: String, timeOut: LocationTime?): Single<Location> =
            Observable.create(RxLocationListener(locationManager, provider), Emitter.BackpressureMode.NONE)
                    .toSingle()
                    .compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .compose { applySchedulers(it) }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler).observeOn(scheduler)

    private class RxLocationListener(val locationManager: LocationManager, val provider: String) : Action1<Emitter<Location>> {

        override fun call(emitter: Emitter<Location>) {
            if (locationManager.isProviderEnabled(provider)) {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        with(emitter) {
                            onNext(location)
                            onCompleted()
                        }
                    }

                    override fun onProviderDisabled(p: String?) {
                        if (provider == p) {
                            emitter.onError(ProviderDisabledException(provider))
                        }
                    }

                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                    override fun onProviderEnabled(p0: String?) {}
                }

                locationManager.requestSingleUpdate(provider, locationListener, null)

                emitter.setCancellation { locationManager.removeUpdates(locationListener) }

            } else {
                emitter.onError(ProviderDisabledException(provider))
            }
        }
    }
}

class LocationRequestBuilder internal constructor(rxLocationManager: RxLocationManager,
                                                  private val scheduler: Scheduler = AndroidSchedulers.mainThread()) : BaseLocationRequestBuilder<Single<Location>, Single<Location>, Observable.Transformer<Location, Location>>(rxLocationManager) {
    private val observables: MutableList<Observable<Location?>> = arrayListOf()

    constructor(context: Context) : this(RxLocationManager(context))

    override fun baseAddRequestLocation(provider: String, timeOut: LocationTime?,
                                        transformer: Observable.Transformer<Location, Location>?): BaseLocationRequestBuilder<Single<Location>, Single<Location>, Observable.Transformer<Location, Location>> {
        val o = rxLocationManager.requestLocation(provider, timeOut)
                .toObservable()
                .onErrorResumeNext {
                    when (it) {
                        is TimeoutException, is ProviderDisabledException -> Observable.empty<Location>()
                        else -> Observable.error<Location>(it)
                    }
                }

        observables.add(if (transformer != null) o.compose(transformer) else o)

        return this
    }

    override fun baseAddLastLocation(provider: String, howOldCanBe: LocationTime?,
                                     transformer: Observable.Transformer<Location, Location>?): BaseLocationRequestBuilder<Single<Location>, Single<Location>, Observable.Transformer<Location, Location>> {
        val o = rxLocationManager.getLastLocation(provider, howOldCanBe)
                .toObservable()
                .onErrorResumeNext {
                    when (it) {
                        is ElderLocationException, is ProviderHasNoLastLocationException -> Observable.empty<Location>()
                        else -> Observable.error<Location>(it)
                    }
                }

        observables.add(if (transformer != null) o.compose(transformer) else o)

        return this
    }

    override fun create(): Single<Location> {

        var result = Observable.empty<Location>()

        observables.forEach {
            result = result.concatWith(it.compose {
                it.onErrorResumeNext {
                    if (returnDefaultLocationOnError) {
                        Observable.empty<Location>()
                    } else {
                        Observable.error<Location>(it)
                    }
                }
            })
        }

        return result.firstOrDefault(defaultLocation).toSingle().observeOn(scheduler)
    }
}
