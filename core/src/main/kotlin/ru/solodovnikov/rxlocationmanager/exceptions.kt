package ru.solodovnikov.rxlocationmanager

import android.location.Location

/**
 * Throws when [provider] is not enabled on a device
 */
class ProviderDisabledException(val provider: String) : Throwable("The $provider provider is disabled")

/**
 * Throws when [location] is not valid by time
 */
class ElderLocationException(val location: Location) : Throwable("The location is too old")

/**
 * Throws when there is no such provider on the device
 */
class ProviderNotAvailableException(provider: String) : Throwable("There is no such provider: $provider")

/**
 * Throws when null emitted
 */
internal class NullEmittedException : Throwable()

/**
 * Used by library to indicate which Throwable should be ignored
 */
internal class IgnorableException : Throwable()