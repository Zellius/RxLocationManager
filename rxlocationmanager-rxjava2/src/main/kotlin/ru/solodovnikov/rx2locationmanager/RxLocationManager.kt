package ru.solodovnikov.rx2locationmanager

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava2
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
                        vararg behaviors: MaybeBehavior): Maybe<Location> =
            Maybe.fromCallable { locationManager.getLastKnownLocation(provider) ?: throw NullEmittedException() }
                    .onErrorComplete { it is NullEmittedException }
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
            Single.create(SingleOnSubscribe<Location> { emitter ->
                if (locationManager.isProviderEnabled(provider)) {
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            emitter.onSuccess(location)
                        }

                        override fun onProviderDisabled(p: String?) {
                            if (provider == p) {
                                emitter.onError(ProviderDisabledException(provider))
                            }
                        }

                        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                        override fun onProviderEnabled(p: String?) {}
                    }.also {
                        emitter.setCancellable { locationManager.removeUpdates(it) }

                        locationManager.requestSingleUpdate(provider, it, null)
                    }
                } else {
                    emitter.onError(ProviderDisabledException(provider))
                }
            }).compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .applyBehaviors(behaviors, BehaviorParams(provider))
                    .compose(this::applySchedulers)

    /**
     * Register for location updates using a Criteria
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     */
    @JvmOverloads
    fun requestLocationUpdates(provider: String,
                               minTime: Long = 0L,
                               minDistance: Float = 0F,
                               vararg behaviors: ObservableBehavior): Observable<Location> =
            Observable.create<Location> { emitter ->
                if (locationManager.isProviderEnabled(provider)) {
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            emitter.onNext(location)
                        }

                        override fun onProviderDisabled(p: String) {
                            if (provider == p) {
                                emitter.onError(ProviderDisabledException(p))
                            }
                        }

                        override fun onStatusChanged(provider: String, p1: Int, p2: Bundle?) {}

                        override fun onProviderEnabled(provider: String) {}
                    }.also {
                        emitter.setCancellable { locationManager.removeUpdates(it) }

                        locationManager.requestLocationUpdates(provider, minTime, minDistance, it)
                    }
                } else {
                    emitter.onError(ProviderDisabledException(provider))
                }
            }.applyBehaviors(behaviors, BehaviorParams(provider)).compose(this::applySchedulers)

    /**
     * Adds a GPS status listener
     *
     * @see LocationManager.addGpsStatusListener
     */
    @Suppress("DEPRECATION")
    fun addGpsStatusListener(vararg behaviors: ObservableBehavior): Observable<Int> =
            Observable.create<Int> { emitter ->
                GpsStatus.Listener { event -> emitter.onNext(event) }.also {
                    emitter.setCancellable { locationManager.removeGpsStatusListener(it) }
                    if (!locationManager.addGpsStatusListener(it)) {
                        emitter.onError(ListenerNotRegisteredException())
                    }
                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)


    /**
     * Registers a GNSS status callback.
     *
     * @see LocationManager.registerGnssStatusCallback
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun addGnssStatusListener(vararg behaviors: ObservableBehavior): Observable<GnssStatusResponse> =
            Observable.create<GnssStatusResponse> { emitter ->
                object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        super.onSatelliteStatusChanged(status)
                        emitter.onNext(GnssStatusResponse.GnssSatelliteStatusChanged(status))
                    }

                    override fun onStopped() {
                        super.onStopped()
                        emitter.onNext(GnssStatusResponse.GnssStopped())
                    }

                    override fun onStarted() {
                        super.onStarted()
                        emitter.onNext(GnssStatusResponse.GnssStarted())
                    }

                    override fun onFirstFix(ttffMillis: Int) {
                        super.onFirstFix(ttffMillis)
                        emitter.onNext(GnssStatusResponse.GnssFirstFix(ttffMillis))
                    }
                }.also {
                    emitter.setCancellable { locationManager.unregisterGnssStatusCallback(it) }

                    if (!locationManager.registerGnssStatusCallback(it)) {
                        emitter.onError(ListenerNotRegisteredException())
                    }
                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)

    /**
     * Adds an NMEA listener.
     *
     * @see LocationManager.addNmeaListener
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun addNmeaListenerN(vararg behaviors: ObservableBehavior): Observable<NmeaMessage> =
            Observable.create<NmeaMessage> { e ->
                OnNmeaMessageListener { message, timestamp -> e.onNext(NmeaMessage(message, timestamp)) }.also {
                    e.setCancellable { locationManager.removeNmeaListener(it) }

                    if (!locationManager.addNmeaListener(it)) {
                        e.onError(ListenerNotRegisteredException())
                    }
                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)

    /**
     * Adds an NMEA listener.
     *
     * @see LocationManager.addNmeaListener
     */
    @Suppress("DEPRECATION")
    fun addNmeaListener(vararg behaviors: ObservableBehavior): Observable<NmeaMessage> =
            Observable.create<NmeaMessage> { e ->
                GpsStatus.NmeaListener { timestamp, nmea -> e.onNext(NmeaMessage(nmea, timestamp)) }.also {
                    e.setCancellable { locationManager.removeNmeaListener(it) }

                    if (!locationManager.addNmeaListener(it)) {
                        e.onError(ListenerNotRegisteredException())
                    }

                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)

    /**
     * Registers a GPS Measurement callback.
     *
     * @see LocationManager.registerGnssMeasurementsCallback
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun registerGnssMeasurementsCallback(vararg behaviors: ObservableBehavior): Observable<GnssMeasurementsResponse> =
            Observable.create<GnssMeasurementsResponse> { emitter ->
                object : GnssMeasurementsEvent.Callback() {
                    override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
                        super.onGnssMeasurementsReceived(eventArgs)
                        emitter.onNext(GnssMeasurementsResponse.GnssMeasurementsReceived(eventArgs))
                    }

                    override fun onStatusChanged(status: Int) {
                        super.onStatusChanged(status)
                        emitter.onNext(GnssMeasurementsResponse.StatusChanged(status))
                    }
                }.also {
                    emitter.setCancellable { locationManager.unregisterGnssMeasurementsCallback(it) }

                    if (!locationManager.registerGnssMeasurementsCallback(it)) {
                        emitter.onError(ListenerNotRegisteredException())
                    }
                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)

    /**
     * Registers a GNSS Navigation Message callback.
     *
     * @see LocationManager.registerGnssNavigationMessageCallback
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun registerGnssNavigationMessageCallback(vararg behaviors: ObservableBehavior): Observable<GnssNavigationResponse> =
            Observable.create<GnssNavigationResponse> { emitter ->
                object : GnssNavigationMessage.Callback() {
                    override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                        super.onGnssNavigationMessageReceived(event)
                        emitter.onNext(GnssNavigationResponse.GnssNavigationMessageReceived(event))
                    }

                    override fun onStatusChanged(status: Int) {
                        super.onStatusChanged(status)
                        emitter.onNext(GnssNavigationResponse.StatusChanged(status))
                    }
                }.also {
                    emitter.setCancellable { locationManager.unregisterGnssNavigationMessageCallback(it) }

                    if (!locationManager.registerGnssNavigationMessageCallback(it)) {
                        emitter.onError(ListenerNotRegisteredException())
                    }
                }
            }.applyBehaviors(behaviors, BehaviorParams()).compose(this::applySchedulers)

    /**
     * Retrieves information about the current status of the GPS engine.
     *
     * @see LocationManager.getGpsStatus
     */
    @JvmOverloads
    @Suppress("DEPRECATION")
    fun getGpsStatus(status: GpsStatus? = null, vararg behaviors: SingleBehavior) =
            Single.fromCallable { locationManager.getGpsStatus(status) }
                    .applyBehaviors(behaviors, BehaviorParams())
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
    fun getProviders(criteria: Criteria? = null, enabledOnly: Boolean): Single<List<String>> =
            Single.fromCallable { locationManager.getProviders(criteria, enabledOnly) }
                    .compose(this::applySchedulers)

    /**
     * Returns the name of the provider that best meets the given criteria.
     *
     * @see LocationManager.getBestProvider
     */
    @JvmOverloads
    fun getBestProvider(criteria: Criteria, enabledOnly: Boolean = true): Single<String> =
            Single.fromCallable { locationManager.getBestProvider(criteria, enabledOnly) }
                    .compose(this::applySchedulers)

    /**
     * Returns the information associated with the location provider of the given name, or null if no provider exists by that name.
     *
     * @param behaviors extra behaviors
     * @see LocationManager.getProvider
     */
    fun getProvider(name: String, vararg behaviors: MaybeBehavior) =
            Maybe.fromCallable { locationManager.getProvider(name) ?: throw NullEmittedException() }
                    .onErrorComplete { it is NullEmittedException }
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

    private fun <T> Single<T>.applyBehaviors(behaviors: Array<out SingleBehavior>, params: BehaviorParams) =
            let { behaviors.fold(it, { acc, transformer -> transformer.transform(acc, params) }) }

    private fun <T> Maybe<T>.applyBehaviors(behaviors: Array<out MaybeBehavior>, params: BehaviorParams) =
            let { behaviors.fold(it, { acc, transformer -> transformer.transform(acc, params) }) }

    private fun <T> Observable<T>.applyBehaviors(behaviors: Array<out ObservableBehavior>, params: BehaviorParams) =
            let { behaviors.fold(it, { acc, transformer -> transformer.transform(acc, params) }) }

    private fun <T> applySchedulers(s: Single<T>) = s.subscribeOn(scheduler)

    private fun <T> applySchedulers(m: Maybe<T>) = m.subscribeOn(scheduler)

    private fun <T> applySchedulers(m: Observable<T>) = m.subscribeOn(scheduler)
}

