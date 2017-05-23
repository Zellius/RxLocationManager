package ru.solodovnikov.rxlocationmanager

import java.util.concurrent.TimeUnit

/**
 * Used to describe time in the library functions
 */
data class LocationTime(val time: Long, val timeUnit: TimeUnit) {
    /**
     * Factory methods
     */
    companion object {
        /**
         * @return one day [LocationTime]
         */
        @JvmStatic
        fun oneDay() = LocationTime(1L, TimeUnit.DAYS)

        /**
         * @return one hour [LocationTime]
         */
        @JvmStatic
        fun oneHour() = LocationTime(1L, TimeUnit.HOURS)
    }
}