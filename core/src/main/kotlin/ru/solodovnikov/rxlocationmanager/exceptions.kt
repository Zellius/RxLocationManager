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
 * Throws when location is disabled
 */
class LocationDisabledException : Throwable("Location is disabled on the device")

/**
 * Used by library to indicate which Throwable should be ignored
 */
class IgnorableException : Throwable()

/**
 * Throwed when listener is not registered
 */
class ListenerNotRegisteredException : Throwable()

/**
 * Throws when null emitted
 */
internal class NullEmittedException : Throwable()