package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava2
 */
class RxLocationManager internal constructor(context: Context,
                                             private val scheduler: Scheduler
) : BaseRxLocationManager<Single<Location>,
        Maybe<Location>,
        Observable<Location>,
        SingleTransformer<Location, Location>,
        MaybeTransformer<Location, Location>>(context) {
    constructor(context: Context) : this(context, AndroidSchedulers.mainThread())

    private val permissionSubject by lazy { PublishSubject.create<Pair<Array<out String>, IntArray>>() }

    /**
     * @return Result [Maybe] will not emit any value if location is null.
     * Or it will be emit [ElderLocationException] if [howOldCanBe] not null and location is too old
     */
    override fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?, transformers: Array<out MaybeTransformer<Location, Location>>): Maybe<Location> =
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
                    }.let { transformers.fold(it, { acc, transformer -> acc.compose(transformer) }) ?: it }
                    .compose { applySchedulers(it) }


    /**
     * @return Result [Single] can throw [ProviderDisabledException] or [TimeoutException] if [timeOut] not null
     */
    override fun baseRequestLocation(provider: String, timeOut: LocationTime?, transformers: Array<out SingleTransformer<Location, Location>>): Single<Location> =
            Single.create(SingleOnSubscribe<Location> {
                if (locationManager.isProviderEnabled(provider)) {
                    val locationListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
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
                    .let {
                        transformers.fold(it, { acc, transformer -> acc.compose(transformer) }) ?: it
                    }
                    .compose { applySchedulers(it) }


    override fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        permissionSubject.onNext(Pair(permissions, grantResults))
    }

    internal fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit)
            = permissionSubject.subscribe(onUpdate, {}, {})

    override fun baseRequestLocationUpdates(provider: String, minTime: Long, minDistance: Float): Observable<Location> {
        Observable.create(ObservableOnSubscribe<Location> {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    it.onNext(location)
                }

                override fun onProviderDisabled(p: String?) {
                }

                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                override fun onProviderEnabled(p: String?) {}
            }

            locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener)

            it.setCancellable { locationManager.removeUpdates(locationListener) }
        })
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler)

    private fun applySchedulers(m: Maybe<Location>) = m.subscribeOn(scheduler)
}

/**
 * Implementation of [BaseLocationRequestBuilder] based on rxJava2
 * @param rxLocationManager manager used in the builder. Used for request runtime permissions.
 */
class LocationRequestBuilder(rxLocationManager: RxLocationManager
) : BaseLocationRequestBuilder<Single<Location>,
        Maybe<Location>,
        Observable<Location>,
        SingleTransformer<Location, Location>,
        MaybeTransformer<Location, Location>,
        LocationRequestBuilder>(rxLocationManager) {
    /**
     * Use this constructor if you do not need request runtime permissions
     */
    constructor(context: Context) : this(RxLocationManager(context))

    private var resultObservable = Observable.empty<Location>()

    override fun baseAddRequestLocation(provider: String,
                                        timeOut: LocationTime?,
                                        transformers: Array<out SingleTransformer<Location, Location>>): LocationRequestBuilder =
            rxLocationManager.requestLocation(provider, timeOut, *transformers)
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

    override fun baseAddLastLocation(provider: String,
                                     howOldCanBe: LocationTime?,
                                     transformers: Array<out MaybeTransformer<Location, Location>>): LocationRequestBuilder =
            rxLocationManager.getLastLocation(provider, howOldCanBe, *transformers)
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
     * Construct final observable.
     *
     * @return It will emit [defaultLocation] if it not null and final observable is empty.
     */
    override fun create(): Maybe<Location> =
            resultObservable.firstElement()
                    .compose { if (defaultLocation != null) it.defaultIfEmpty(defaultLocation) else it }
}

