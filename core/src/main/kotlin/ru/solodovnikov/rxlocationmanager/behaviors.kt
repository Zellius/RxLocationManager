package ru.solodovnikov.rxlocationmanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class BehaviorParams(val provider: String? = null)

/**
 * Base transformer to check and request runtime permissions
 * @param context any [Context] of your application
 * @param caller used to request permissions from [android.app.Activity]
 */
abstract class BasePermissionBehavior(context: Context,
                                      protected val caller: PermissionCaller) {
    protected val context: Context = context.applicationContext

    /**
     * @return array of denied permissions of empty array if there is no denied permissions
     */
    protected fun getDeniedPermissions() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val manifestPermissions = context.packageManager
                        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                        .requestedPermissions

                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        .filter {
                            manifestPermissions.contains(it) &&
                                    context.checkSelfPermission(it) == PackageManager.PERMISSION_DENIED
                        }.toTypedArray()
            } else {
                emptyArray()
            }
}