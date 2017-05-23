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
 * Throws when Android [provider] has no last location
 */
class ProviderHasNoLastLocationException(val provider: String) : Throwable("The $provider has no last location")