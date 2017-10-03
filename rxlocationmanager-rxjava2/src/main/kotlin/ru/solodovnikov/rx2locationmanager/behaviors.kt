package ru.solodovnikov.rx2locationmanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationProvider
import android.os.Build
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.*
import android.app.Fragment as FragmentSys

data class BehaviorParams(val provider: String? = null)

interface SingleBehavior {
    fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T>
}

interface MaybeBehavior {
    fun <T> transform(upstream: Maybe<T>, params: BehaviorParams): Maybe<T>
}

interface ObservableBehavior {
    fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T>
}

interface CompletableBehavior {
    fun transform(upstream: Completable, params: BehaviorParams): Completable
}

interface Behavior : SingleBehavior, MaybeBehavior, ObservableBehavior, CompletableBehavior


/**
 * Transformer used to request runtime permissions
 *
 * Call [RxLocationManager.onRequestPermissionsResult] inside your [android.app.Activity.onRequestPermissionsResult]
 * to get request permissions results in the transformer.
 */
open class PermissionBehavior(context: Context,
                              private val rxLocationManager: RxLocationManager,
                              callback: BasePermissionBehavior.PermissionCallback
) : BasePermissionBehavior(context, callback), Behavior {

    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            checkPermissions().andThen(upstream)

    override fun <T> transform(upstream: Maybe<T>, params: BehaviorParams): Maybe<T> =
            checkPermissions().andThen(upstream)

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            checkPermissions().andThen(upstream)

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            checkPermissions().andThen(upstream)

    /**
     * Construct [Completable] which check runtime permissions
     */
    protected open fun checkPermissions(): Completable =
            Completable.create { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedPermissions = getDeniedPermissions()

                    if (deniedPermissions.isNotEmpty()) {
                        //wait until user approve permissions or dispose action
                        subscribeToPermissionUpdate {
                            val resultPermissions = it.first
                            val resultPermissionsResults = it.second
                            if (!Arrays.equals(resultPermissions, deniedPermissions) ||
                                    resultPermissionsResults.find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedPermissions.asList()}"))
                            } else {
                                emitter.onComplete()
                            }
                        }.apply { emitter.setCancellable { dispose() } }

                        callback.requestPermissions(deniedPermissions)
                    } else {
                        emitter.onComplete()
                    }
                } else {
                    emitter.onComplete()
                }
            }

    /**
     * Subscribe to request permissions result
     */
    protected fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit): Disposable =
            rxLocationManager.subscribeToPermissionUpdate(onUpdate)
}

/**
 * Transformer used to ignore any described error type.
 *
 * @param errorsToIgnore if empty, then ignore all errors, otherwise just described types.
 */
class IgnoreErrorBehavior(vararg errorsToIgnore: Class<out Throwable>) : Behavior {
    private val toIgnore: Array<out Class<out Throwable>> = errorsToIgnore

    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<T>(it) }
            }

    override fun <T> transform(upstream: Maybe<T>, params: BehaviorParams): Maybe<T> =
            upstream.onErrorResumeNext { t: Throwable ->
                if (toIgnore.isEmpty() || toIgnore.contains(t.javaClass)) {
                    Maybe.empty()
                } else {
                    Maybe.error(t)
                }
            }

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            upstream.onErrorResumeNext { t: Throwable ->
                if (toIgnore.isEmpty() || toIgnore.contains(t.javaClass)) {
                    Observable.empty()
                } else {
                    Observable.error(t)
                }
            }

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    Completable.complete()
                } else {
                    Completable.error(it)
                }
            }
}

class EnableLocationBehavior(private val resolver: Resolver) : Behavior {
    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            resolver.create(params.provider!!).andThen(upstream)

    override fun <T> transform(upstream: Maybe<T>, params: BehaviorParams): Maybe<T> =
            resolver.create(params.provider!!).andThen(upstream)

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            resolver.create(params.provider!!).andThen(upstream)

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            resolver.create(params.provider!!).andThen(upstream)

    companion object {
        @JvmStatic
        fun createActivityBehavior(context: Context,
                                   requestCode: Int,
                                   activityProvider: (() -> Activity)?,
                                   rxLocationManager: RxLocationManager) =
                create(context, requestCode, activityProvider, null, null, rxLocationManager)

        @JvmStatic
        fun createFragmentCompatBehavior(context: Context,
                                         requestCode: Int,
                                         fragmentProvider: (() -> Fragment)?,
                                         rxLocationManager: RxLocationManager) =
                create(context, requestCode, null, fragmentProvider, null, rxLocationManager)

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
        fun createFragmentBehavior(context: Context,
                                   requestCode: Int,
                                   fragmentProvider: (() -> FragmentSys)?,
                                   rxLocationManager: RxLocationManager) =
                create(context, requestCode, null, null, fragmentProvider, rxLocationManager)

        @JvmStatic
        private fun create(context: Context,
                           requestCode: Int,
                           activityProvider: (() -> Activity)?,
                           fragmentCompatProvider: (() -> Fragment)?,
                           fragmentProvider: (() -> FragmentSys)?,
                           rxLocationManager: RxLocationManager): EnableLocationBehavior =
                if (try {
                    Class.forName("com.google.android.gms.location.LocationServices") != null
                } catch (e: Exception) {
                    false
                }) {
                    GoogleResolver(context, requestCode, activityProvider, fragmentCompatProvider, rxLocationManager)
                } else {
                    SettingsResolver(requestCode, activityProvider, fragmentCompatProvider, fragmentProvider, rxLocationManager)
                }.let { EnableLocationBehavior(it) }
    }

    abstract class Resolver(protected val rxLocationManager: RxLocationManager) {
        abstract internal fun create(provider: String): Completable

        internal fun checkProvider(provider: String) =
                rxLocationManager.getProvider(provider)
                        .switchIfEmpty { Maybe.error<LocationProvider>(ProviderNotAvailableException(provider)) }
                        .flatMapSingle { rxLocationManager.isProviderEnabled(it.name) }
    }

    class SettingsResolver internal constructor(private val requestCode: Int,
                                                private val activityProvider: (() -> Activity)?,
                                                private val fragmentCompatProvider: (() -> Fragment)?,
                                                private val fragmentProvider: (() -> FragmentSys)?,
                                                rxLocationManager: RxLocationManager) : Resolver(rxLocationManager) {
        override fun create(provider: String): Completable =
                checkProvider(provider).flatMapCompletable { isProviderEnabled ->
                    Completable.create { emitter ->
                        if (!isProviderEnabled) {
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).also {
                                when {
                                    activityProvider != null ->
                                        activityProvider.invoke().startActivityForResult(it, requestCode)
                                    fragmentCompatProvider != null ->
                                        fragmentCompatProvider.invoke().startActivityForResult(it, requestCode)
                                    fragmentProvider != null ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                            fragmentProvider.invoke().startActivityForResult(it, requestCode)
                                        } else {
                                            emitter.onError(IllegalStateException("Build version is < 11"))
                                        }
                                    else ->
                                        emitter.onError(IllegalArgumentException(
                                                "ActivityProvider and FragmentProvider cannot be null"))
                                }
                            }
                        } else {
                            emitter.onComplete()
                        }
                    }
                }

        companion object {
            @JvmStatic
            fun getActivityResolver(requestCode: Int,
                                    activityProvider: () -> Activity,
                                    rxLocationManager: RxLocationManager) =
                    SettingsResolver(requestCode, activityProvider, null, null, rxLocationManager)

            @JvmStatic
            fun getFragmentCompatResolver(requestCode: Int,
                                          fragmentProvider: () -> Fragment,
                                          rxLocationManager: RxLocationManager) =
                    SettingsResolver(requestCode, null, fragmentProvider, null, rxLocationManager)

            @JvmStatic
            @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
            fun getFragmentResolver(requestCode: Int,
                                    fragmentProvider: () -> FragmentSys,
                                    rxLocationManager: RxLocationManager) =
                    SettingsResolver(requestCode, null, null, fragmentProvider, rxLocationManager)
        }
    }

    class GoogleResolver internal constructor(context: Context,
                                              private val requestCode: Int,
                                              private val activityProvider: (() -> Activity)?,
                                              private val fragmentProvider: (() -> Fragment)?,
                                              rxLocationManager: RxLocationManager) : Resolver(rxLocationManager) {
        private val context = context.applicationContext

        override fun create(provider: String): Completable =
                Completable.create { emitter ->
                    LocationServices.getSettingsClient(context)
                            .checkLocationSettings(LocationSettingsRequest.Builder()
                                    .addLocationRequest(LocationRequest.create()).build())
                            .addOnFailureListener {
                                val apiException = it as? ApiException ?: throw it
                                when (apiException.statusCode) {
                                    CommonStatusCodes.RESOLUTION_REQUIRED -> {
                                        val e = it as? ResolvableApiException ?: throw it
                                        when {
                                            activityProvider != null ->
                                                e.startResolutionForResult(activityProvider.invoke(), requestCode)
                                            fragmentProvider != null ->
                                                fragmentProvider.invoke()
                                                        .startIntentSenderForResult(e.resolution.intentSender,
                                                                requestCode, null, 0, 0, 0, null)
                                            else ->
                                                emitter.onError(IllegalArgumentException(
                                                        "ActivityProvider and FragmentProvider cannot be null"))
                                        }
                                    }
                                    else -> {
                                        emitter.onError(it)
                                    }
                                }
                            }.addOnSuccessListener { emitter.onComplete() }
                }

        companion object {
            @JvmStatic
            fun getActivityResolver(context: Context,
                                    requestCode: Int,
                                    activityProvider: () -> Activity,
                                    rxLocationManager: RxLocationManager) =
                    GoogleResolver(context, requestCode, activityProvider, null, rxLocationManager)

            @JvmStatic
            fun getFragmentResolver(context: Context,
                                    requestCode: Int,
                                    fragmentProvider: () -> Fragment,
                                    rxLocationManager: RxLocationManager) =
                    GoogleResolver(context, requestCode, null, fragmentProvider, rxLocationManager)
        }
    }
}