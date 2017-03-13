package ru.solodovnikov.rxlocationmanager

/**
 * Throwed when Android [provider] has no last location
 */
class ProviderHasNoLastLocationException(val provider: String) : Throwable("The $provider has no last location")
