package ru.solodovnikov.rxlocationmanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Base transformer to check and request runtime permissions
 * @param context any [Context] of your application
 * @param callback used to request permissions from [android.app.Activity]
 */
abstract class BasePermissionTransformer(context: Context,
                                         protected val callback: BasePermissionTransformer.PermissionCallback) {
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

    /**
     * Used by [BasePermissionTransformer] to request permissions from [android.app.Activity]
     */
    interface PermissionCallback {
        /**
         * Called to request permissions
         * @see android.app.Activity.requestPermissions
         * @see android.app.Fragment.requestPermissions
         */
        fun requestPermissions(permissions: Array<String>)
    }
}