package ru.solodovnikov.rxlocationmanager

import android.location.Location
import android.os.Build
import android.os.SystemClock

/**
 * Check is location not old
 * @param howOldCanBe how old the location can be
 * @return true if location is not so old as [howOldCanBe]
 */
fun Location.isNotOld(howOldCanBe: LocationTime): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        return SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos < howOldCanBe.timeUnit.toNanos(howOldCanBe.time)
    } else {
        return System.currentTimeMillis() - time < howOldCanBe.timeUnit.toMillis(howOldCanBe.time)
    }
}