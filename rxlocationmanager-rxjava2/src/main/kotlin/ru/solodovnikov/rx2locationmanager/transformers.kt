package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import io.reactivex.*
import io.reactivex.disposables.Disposable
import java.util.*

/**
 * Transformer used to request runtime permissions
 *
 * Call [RxLocationManager.onRequestPermissionsResult] inside your [android.app.Activity.onRequestPermissionsResult]
 * to get request permissions results in the transformer.
 */
open class PermissionTransformer(context: Context,
                                 private val rxLocationManager: RxLocationManager,
                                 callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer(context, callback),
        SingleTransformer<Location, Location>,
        MaybeTransformer<Location, Location> {

    override fun apply(upstream: Single<Location>): SingleSource<Location> =
            checkPermissions().andThen(upstream)

    override fun apply(upstream: Maybe<Location>): MaybeSource<Location> =
            checkPermissions().andThen(upstream)

    /**
     * Construct [Completable] which check runtime permissions
     */
    protected open fun checkPermissions(): Completable =
            Completable.create { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedPermissions = getDeniedPermissions()

                    if (deniedPermissions.isNotEmpty()) {
                        callback.requestPermissions(deniedPermissions)
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
class IgnoreErrorTransformer(vararg errorsToIgnore: Class<out Throwable>
) : SingleTransformer<Location, Location>, MaybeTransformer<Location, Location> {
    private val ignoreError: (Throwable) -> Throwable = {
        if (errorsToIgnore.isEmpty() || errorsToIgnore.contains(it.javaClass)) {
            IgnorableException()
        } else {
            it
        }
    }

    override fun apply(upstream: Single<Location>): SingleSource<Location> =
            upstream.onErrorResumeNext { Single.error<Location>(ignoreError(it)) }

    override fun apply(upstream: Maybe<Location>): MaybeSource<Location> =
            upstream.onErrorResumeNext { t: Throwable -> Maybe.error<Location>(ignoreError(t)) }
}