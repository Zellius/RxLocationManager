package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.util.*

abstract class BasePermissionTransformerImpl<RX>(context: Context,
                                                 callback: BasePermissionTransformer.PermissionCallback,
                                                 private val permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>
) : BasePermissionTransformer<RX>(context, callback) {

    protected fun checkPermissions(): Completable =
            Completable.create { emitter ->
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

internal class PermissionRxSingleTransformer(context: Context,
                                             callback: BasePermissionTransformer.PermissionCallback,
                                             permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>
) : BasePermissionTransformerImpl<Single<Location>>(context, callback, permissionResult) {
    override fun transform(rx: Single<Location>): Single<Location> = checkPermissions().andThen(rx)
}

internal class PermissionRxMaybeTransformer(context: Context,
                                            callback: BasePermissionTransformer.PermissionCallback,
                                            permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>
) : BasePermissionTransformerImpl<Maybe<Location>>(context, callback, permissionResult) {
    override fun transform(rx: Maybe<Location>): Maybe<Location> = checkPermissions().andThen(rx)
}