package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import rx.Completable
import rx.Single
import rx.subjects.PublishSubject
import java.util.*

class PermissionTransformer(context: Context,
                            callback: BasePermissionTransformer.PermissionCallback,
                            private val permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>
) : BasePermissionTransformer<Single<Location>>(context, callback) {
    override fun transform(rx: Single<Location>): Single<Location> =
            checkPermissions().andThen(rx)

    protected fun checkPermissions(): Completable =
            Completable.fromEmitter { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedP = getDeniedPermissions()

                    if (deniedP.isNotEmpty()) {
                        callback.requestPermissions(deniedP)
                        //wait until user approve permissions or dispose action
                        permissionResult.subscribe {
                            val resultPermissions = it.first
                            val resultPermissionsResults = it.second
                            if (!Arrays.equals(resultPermissions, deniedP) || resultPermissionsResults.find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedP.asList()}"))
                            } else {
                                emitter.onCompleted()
                            }
                        }.apply { emitter.setCancellation { unsubscribe() } }
                    } else {
                        emitter.onCompleted()
                    }
                } else {
                    emitter.onCompleted()
                }
            }
}