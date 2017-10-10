package ru.solodovnikov.rxlocationmanager

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import rx.Emitter
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava1
 */
class RxLocationManager internal constructor(context: Context,
                                             private val scheduler: Scheduler
) : BaseRxLocationManager(context) {

    constructor(context: Context) : this(context, AndroidSchedulers.mainThread())

    private val permissionSubject by lazy { PublishSubject.create<Pair<Array<out String>, IntArray>>() }
    private val resultSubject by lazy { PublishSubject.create<Instrumentation.ActivityResult>() }

    /**
     * Get last location from specific provider
     * Observable will emit [ElderLocationException] if [howOldCanBe] is not null and location time is not valid.
     *
     * @param provider provider name
     * @param howOldCanBe how old a location can be
     * @param behaviors extra behaviors
     * @return observable that emit last known location
     * @see ElderLocationException
     */
    @JvmOverloads
    fun getLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg behaviors: SingleBehavior): Single<Location> =
            Single.fromCallable { locationManager.getLastKnownLocation(provider) }
                    .compose {
                        if (howOldCanBe != null) {
                            it.doOnSuccess {
                                if (it != null && !it.isNotOld(howOldCanBe)) {
                                    throw ElderLocationException(it)
                                }
                            }
                        } else {
                            it
                        }
                    }.applyBehaviors(behaviors, BehaviorParams(provider))
                    .compose(this::applySchedulers)

    /**
     * Try to get current location by specific provider.
     * Observable will emit [TimeoutException] if [timeOut] is not null and timeOut occurs.
     * Observable will emit [ProviderDisabledException] if provider is disabled
     *
     * @param provider provider name
     * @param timeOut  request timeout
     * @param behaviors extra behaviors
     * @return observable that emit current location
     * @see TimeoutException
     * @see ProviderDisabledException
     */
    @JvmOverloads
    fun requestLocation(provider: String,
                        timeOut: LocationTime? = null,
                        vararg behaviors: SingleBehavior): Single<Location> =
            Observable.create<Location>({
                if (locationManager.isProviderEnabled(provider)) {
                    val locationListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            with(it) {
                                onNext(location)
                                onCompleted()
                            }
                        }

                        override fun onProviderDisabled(p: String?) {
                            if (provider == p) {
                                it.onError(ProviderDisabledException(provider))
                            }
                        }

                        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                        override fun onProviderEnabled(p0: String?) {}
                    }

                    locationManager.requestSingleUpdate(provider, locationListener, null)

                    it.setCancellation { locationManager.removeUpdates(locationListener) }

                } else {
                    it.onError(ProviderDisabledException(provider))
                }
            }, Emitter.BackpressureMode.NONE)
                    .toSingle()
                    .compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .applyBehaviors(behaviors, BehaviorParams(provider))
                    .compose(this::applySchedulers)

    /**
     * Register for location updates using a Criteria
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     *
     * @see LocationManager.requestLocationUpdates
     */
    @JvmOverloads
    fun requestLocationUpdates(provider: String,
                               minTime: Long = 0L,
                               minDistance: Float = 0F,
                               vararg behaviors: ObservableBehavior): Observable<Location> =
            Observable.create<Location>({
                if (locationManager.isProviderEnabled(provider)) {
                    val locationListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            it.onNext(location)
                        }

                        override fun onProviderDisabled(p: String) {
                            if (provider == p) {
                                it.onError(ProviderDisabledException(provider))
                            }
                        }

                        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                        override fun onProviderEnabled(p0: String?) {}
                    }

                    locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener)

                    it.setCancellation { locationManager.removeUpdates(locationListener) }

                } else {
                    it.onError(ProviderDisabledException(provider))
                }
            }, Emitter.BackpressureMode.NONE)
                    .applyBehaviors(behaviors, BehaviorParams(provider))
                    .compose(this::applySchedulers)

    /**
     * Returns a list of the names of all known location providers.
     *
     * @see LocationManager.getAllProviders
     */
    fun getAllProviders() = Single.fromCallable { locationManager.allProviders }
            .compose(this::applySchedulers)

    /**
     * Returns a list of the names of location providers.
     *
     * @see LocationManager.getProviders
     */
    @JvmOverloads
    fun getProviders(criteria: Criteria? = null, enabledOnly: Boolean) =
            Single.fromCallable { locationManager.getProviders(criteria, enabledOnly) }
                    .compose(this::applySchedulers)

    /**
     * Returns the name of the provider that best meets the given criteria.
     *
     * @see LocationManager.getBestProvider
     */
    @JvmOverloads
    fun getBestProvider(criteria: Criteria, enabledOnly: Boolean = true) =
            Single.fromCallable { locationManager.getBestProvider(criteria, enabledOnly) }
                    .compose(this::applySchedulers)

    /**
     * Returns the information associated with the location provider of the given name, or null if no provider exists by that name.
     *
     * @param behaviors extra behaviors
     * @see LocationManager.getProvider
     */
    fun getProvider(name: String, vararg behaviors: SingleBehavior) =
            Single.fromCallable { locationManager.getProvider(name) }
                    .applyBehaviors(behaviors, BehaviorParams(name))

    /**
     * Returns the current enabled/disabled status of the given provider.
     *
     * @see LocationManager.isProviderEnabled
     */
    fun isProviderEnabled(provider: String, vararg behaviors: SingleBehavior): Single<Boolean> =
            Single.fromCallable { locationManager.isProviderEnabled(provider) }
                    .applyBehaviors(behaviors, BehaviorParams(provider))

    override fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        permissionSubject.onNext(Pair(permissions, grantResults))
    }

    override fun onActivityResult(resultCode: Int, data: Intent?) {
        resultSubject.onNext(Instrumentation.ActivityResult(resultCode, data))
    }

    internal fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit)
            = permissionSubject.subscribe(onUpdate, {}, {})

    internal fun subscribeToActivityResultUpdate(onUpdate: (Instrumentation.ActivityResult) -> Unit)
            = resultSubject.subscribe(onUpdate, {}, {})

    private fun <T> applySchedulers(s: Single<T>) = s.subscribeOn(scheduler)

    private fun <T> applySchedulers(s: Observable<T>) = s.subscribeOn(scheduler)

    private fun <T> Single<T>.applyBehaviors(behaviors: Array<out SingleBehavior>, params: BehaviorParams) =
            let { behaviors.fold(it, { acc, transformer -> transformer.transform(acc, params) }) }

    private fun <T> Observable<T>.applyBehaviors(behaviors: Array<out ObservableBehavior>, params: BehaviorParams) =
            let { behaviors.fold(it, { acc, transformer -> transformer.transform(acc, params) }) }
}


