package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import io.reactivex.*
import java.util.*

/**
 * Transformer used to request runtime permissions
 */
class PermissionTransformer(context: Context,
                            private val rxLocationManager: RxLocationManager,
                            callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer(context, callback), SingleTransformer<Location, Location>, MaybeTransformer<Location, Location> {

    override fun apply(upstream: Single<Location>): SingleSource<Location> =
            checkPermissions().andThen(upstream)

    override fun apply(upstream: Maybe<Location>): MaybeSource<Location> =
            checkPermissions().andThen(upstream)

    protected fun checkPermissions(): Completable =
            Completable.create { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedP = getDeniedPermissions()

                    if (deniedP.isNotEmpty()) {
                        callback.requestPermissions(deniedP)
                        //wait until user approve permissions or dispose action
                        rxLocationManager.subscribeToPermissionUpdate {
                            val resultPermissions = it.first
                            val resultPermissionsResults = it.second
                            if (!Arrays.equals(resultPermissions, deniedP) || resultPermissionsResults.find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedP.asList()}"))
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
}

/**
 * Transformer Used it to ignore any described error type.
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