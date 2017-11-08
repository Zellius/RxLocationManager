package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.*
import android.os.Build
import android.os.Bundle
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.JELLY_BEAN))
class RxLocationManagerTest {
    private val networkProvider = LocationManager.NETWORK_PROVIDER

    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager: RxLocationManager
        get() = RxLocationManager(context, Schedulers.trampoline())

    val defaultLocationRequestBuilder: LocationRequestBuilder
        get() = LocationRequestBuilder(defaultRxLocationManager)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(context.getSystemService(eq(Context.LOCATION_SERVICE)))
                .thenReturn(locationManager)
    }

    /**
     * Test that getLastLocation works fine
     */
    @Test
    fun getLastKnownLocation_success() {
        val expectedLocation = buildFakeLocation()

        whenever(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(expectedLocation)

        defaultRxLocationManager.getLastLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that getLastLocation will throw [ElderLocationException] if location is old
     */
    @Test
    fun getLastKnownLocation_old() {
        whenever(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(buildFakeLocation()
                        .apply { time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1) })

        defaultRxLocationManager.getLastLocation(networkProvider, LocationTime(30, TimeUnit.MINUTES))
                .test()
                .awaitTerminalEvent()
                .assertError(ElderLocationException::class.java)
    }

    /**
     * Test that getLastLocation will emit [Location] if it is not old
     */
    @Test
    fun getLastKnownLocation_notOld() {
        val expectedLocation = buildFakeLocation()

        whenever(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(expectedLocation)

        defaultRxLocationManager.getLastLocation(networkProvider, LocationTime(30, TimeUnit.MINUTES))
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that getLastLocation emit null if [LocationManager] return null
     */
    @Test
    fun getLastKnownLocation_noLocation() {
        whenever(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(null)

        defaultRxLocationManager.getLastLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(null)
    }

    /**
     * Test that [LocationManager] will be unsubscribed after unsubscribe
     */
    @Test
    fun requestLocation_unsubscribe() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.requestLocation(networkProvider)
                .subscribe()
                .also { disposable ->
                    argumentCaptor<LocationListener>().apply {
                        verify(locationManager).requestSingleUpdate(eq(networkProvider), capture(), isNull())
                        assertNotNull(firstValue)
                        disposable.unsubscribe()
                        verify(locationManager, times(1)).removeUpdates(eq(firstValue))
                    }
                }
    }

    @Test
    fun requestLocation_success() {
        val expectedLocation = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        //answer
        doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onLocationChanged(expectedLocation)
            return@doAnswer null
        }.whenever(locationManager).requestSingleUpdate(eq(networkProvider), any(), isNull())

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that request location wait until provider will be enabled
     */
    @Test
    fun requestLocation_providerDisabled() {
        //set provider disabled
        setIsProviderEnabled(isEnabled = false)
        val fakeLocation = buildFakeLocation()

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .also { o ->
                    o.assertNoTerminalEvent()

                    argumentCaptor<LocationListener>().apply {
                        verify(locationManager).requestSingleUpdate(eq(networkProvider), capture(), isNull())
                        firstValue.onLocationChanged(fakeLocation)
                    }

                    o.awaitTerminalEvent(1, TimeUnit.SECONDS)
                    o.assertNoErrors()
                            .assertCompleted()
                            .assertValue(fakeLocation)
                }
    }

    /**
     * Test that ThrowProviderDisabledBehavior will throw [ProviderDisabledException] if provider disabled
     */
    @Test
    fun requestLocation_providerDisabledWithBehavior() {
        setIsProviderEnabled(isEnabled = false)

        defaultRxLocationManager.requestLocation(networkProvider, behaviors = ThrowProviderDisabledBehavior(defaultRxLocationManager))
                .test()
                .awaitTerminalEvent()
                .assertError(ProviderDisabledException::class.java)
    }

    /**
     * Test that request location throw [TimeoutException]
     */
    @Test
    fun requestLocation_timeOutError() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.requestLocation(networkProvider, LocationTime(10, TimeUnit.MILLISECONDS))
                .test()
                .awaitTerminalEvent()
                .assertError(TimeoutException::class.java)
    }

    /**
     * Test raw implementation
     */
    @Test
    fun requestLocationRaw_success() {
        val provider = networkProvider
        val status = 0
        val extras = mock<Bundle>()
        val location = buildFakeLocation(provider)

        doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onProviderDisabled(provider)
            locationListener.onProviderEnabled(provider)
            locationListener.onStatusChanged(provider, status, extras)

            locationListener.onLocationChanged(location)
            locationListener.onLocationChanged(location)
            locationListener.onLocationChanged(location)
            return@doAnswer null
        }.whenever(locationManager).requestSingleUpdate(eq(provider), any(), isNull())

        defaultRxLocationManager.requestLocationRaw(provider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValueCount(4)
                .onNextEvents.also {
            (it[0] as BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent).also {
                assertTrue { !it.enabled && it.provider == provider }
            }
            (it[1] as BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent).also {
                assertTrue { it.enabled && it.provider == provider }
            }
            (it[2] as BaseRxLocationManager.LocationEvent.StatusChangedEvent).also {
                assertTrue { it.provider == provider && it.status == status && it.extras == extras }
            }
            (it[3] as BaseRxLocationManager.LocationEvent.LocationChangedEvent).also {
                assertTrue { it.location == location }
            }
        }
    }

    /**
     * * Request location - TimeOut
     * * Last Location - null
     *
     * Will return default location
     */
    @Test
    fun testBuilder_SuccessDefaultLocation() {
        val defaultLocation = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenReturn(null)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider)
                .setDefaultLocation(defaultLocation)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValueCount(1)
                .assertValue(defaultLocation)
    }

    /**
     * * Request location - TimeOut
     * * Last Location - null. But null is valid value
     *
     * Will return null
     */
    @Test
    fun testBuilder_SuccessValidNullValue() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenReturn(null)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS), false)
                .addLastLocation(networkProvider, isNullValid = true)
                .setDefaultLocation(buildFakeLocation())
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(null)
    }

    /**
     * * Request location - [ProviderDisabledException]
     * * Last Location - null
     * * Request location - [ProviderDisabledException]
     *
     * Will emit null value
     */
    @Test
    fun testBuilder_SuccessEmpty() {
        //set providers disabled
        setIsProviderEnabled(networkProvider, false)
        setIsProviderEnabled(LocationManager.GPS_PROVIDER, false)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider)
                .addRequestLocation(LocationManager.GPS_PROVIDER, LocationTime(5, TimeUnit.MILLISECONDS))
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(null)
    }

    /**
     * * Request location - [ProviderDisabledException]
     * * Last Location - [SecurityException]
     * * Request location - [Throwable]
     *
     * Will emit [Throwable]
     */
    @Test
    fun testBuilder_Error() {
        setIsProviderEnabled(isEnabled = false)

        val e = SecurityException()

        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenThrow(e)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider)
                .addRequestLocation(LocationManager.GPS_PROVIDER)
                .setDefaultLocation(buildFakeLocation())
                .create()
                .test()
                .awaitTerminalEvent()
                .assertError(e)
    }

    /**
     * * Request location - [ProviderDisabledException]
     * * Last Location - [SecurityException] with transformer
     * * Request location - [Throwable]
     *
     * Will emit defaultLocation
     */
    @Test
    fun testBuilder_ErrorHandling() {
        setIsProviderEnabled(isEnabled = false)

        val location = buildFakeLocation()
        val ex = SecurityException()

        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenThrow(ex)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider, behaviors = IgnoreErrorBehavior(SecurityException::class.java))
                .addRequestLocation(LocationManager.GPS_PROVIDER)
                .setDefaultLocation(location)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location)
    }

    /**
     * * Request location - [ProviderDisabledException]
     * * Last Location - [SecurityException] with transformer
     * * Request location - [Throwable]
     *
     * Will emit defaultLocation
     */
    @Test
    fun testBuilder_ErrorHandlingAll() {
        setIsProviderEnabled(isEnabled = false)

        val location = buildFakeLocation()
        val ex = SecurityException()

        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenThrow(ex)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider, behaviors = IgnoreErrorBehavior())
                .addRequestLocation(LocationManager.GPS_PROVIDER)
                .setDefaultLocation(location)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location)
    }

    /**
     * Emit default value if only it was setted
     */
    @Test
    fun testBuilder_SuccessOnlyDefaultValue() {
        val location = buildFakeLocation()

        defaultLocationRequestBuilder.setDefaultLocation(location)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location)
    }

    @Test
    fun test_GetProvider() {
        val provider = mock<LocationProvider>()

        whenever(locationManager.getProvider(eq(networkProvider)))
                .thenReturn(provider)

        defaultRxLocationManager.getProvider(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(provider)

        verify(locationManager, only()).getProvider(eq(networkProvider))
    }

    @Test
    fun test_GetProviderEmpty() {
        whenever(locationManager.getProvider(eq(networkProvider)))
                .thenReturn(null)

        defaultRxLocationManager.getProvider(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(null)

        verify(locationManager, only()).getProvider(eq(networkProvider))
    }

    @Test
    fun test_IsProviderEnabled() {
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.isProviderEnabled(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(true)

        verify(locationManager, only()).isProviderEnabled(eq(networkProvider))
    }

    @Test
    fun test_GetProviders() {
        val c = Criteria()
        val enabledOnly = true

        val providers = emptyList<String>()

        whenever(locationManager.getProviders(eq(c), eq(enabledOnly)))
                .thenReturn(providers)

        defaultRxLocationManager.getProviders(c, enabledOnly)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(providers)

        verify(locationManager, only()).getProviders(eq(c), eq(enabledOnly))
    }

    @Test
    fun test_GetBestProvider() {
        val c = Criteria()
        val enabledOnly = true

        whenever(locationManager.getBestProvider(eq(c), eq(enabledOnly)))
                .thenReturn(networkProvider)

        defaultRxLocationManager.getBestProvider(c, enabledOnly)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(networkProvider)

        verify(locationManager, only()).getBestProvider(eq(c), eq(enabledOnly))
    }

    @Test
    fun test_GetGpsStatusEmpty() {
        val gpsStatus = mock<GpsStatus>()

        whenever(locationManager.getGpsStatus(isNull())).thenReturn(gpsStatus)

        defaultRxLocationManager.getGpsStatus()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(gpsStatus)
    }

    @Test
    fun test_GetGpsStatusNotEmpty() {
        val gpsStatus = mock<GpsStatus>()

        whenever(locationManager.getGpsStatus(eq(gpsStatus))).thenReturn(gpsStatus)

        defaultRxLocationManager.getGpsStatus(gpsStatus)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(gpsStatus)
    }

    @Test
    fun getAllProviders() {
        val providers = listOf("provider")

        whenever(locationManager.allProviders).thenReturn(providers)

        defaultRxLocationManager.getAllProviders()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(providers)
    }

    @Test
    fun requestLocationUpdatesRaw_success() {
        setIsProviderEnabled(isEnabled = true)

        val provider = networkProvider
        val time = 0L
        val distance = 0.0F
        val status = 0
        val extras = mock<Bundle>()
        val location = buildFakeLocation(provider)

        doAnswer {
            val args = it.arguments
            val locationListener = args[3] as LocationListener
            locationListener.onProviderDisabled(provider)
            locationListener.onProviderEnabled(provider)
            locationListener.onStatusChanged(provider, status, extras)

            locationListener.onLocationChanged(location)
            locationListener.onLocationChanged(location)
            locationListener.onLocationChanged(location)
            return@doAnswer null
        }.whenever(locationManager).requestLocationUpdates(eq(provider), eq(time), eq(distance), any<LocationListener>())

        defaultRxLocationManager.requestLocationUpdatesRaw(provider, time, distance)
                .test()
                .also {
                    it.awaitTerminalEvent(10, TimeUnit.MILLISECONDS)
                    it.assertNoTerminalEvent()
                            .assertValueCount(6)
                            .onNextEvents.also {
                        (it[0] as BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent).also {
                            assertTrue { !it.enabled && it.provider == provider }
                        }
                        (it[1] as BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent).also {
                            assertTrue { it.enabled && it.provider == provider }
                        }
                        (it[2] as BaseRxLocationManager.LocationEvent.StatusChangedEvent).also {
                            assertTrue { it.provider == provider && it.status == status && it.extras == extras }
                        }
                        (it[3] as BaseRxLocationManager.LocationEvent.LocationChangedEvent).also {
                            assertTrue { it.location == location }
                        }
                        (it[4] as BaseRxLocationManager.LocationEvent.LocationChangedEvent).also {
                            assertTrue { it.location == location }
                        }
                        (it[5] as BaseRxLocationManager.LocationEvent.LocationChangedEvent).also {
                            assertTrue { it.location == location }
                        }
                    }
                }
    }

    @Test
    fun requestLocationUpdatesRaw_unsubscribe() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        val time = 0L
        val distance = 0.0F

        defaultRxLocationManager.requestLocationUpdatesRaw(networkProvider, time, distance)
                .test()
                .also { observerer ->
                    argumentCaptor<LocationListener>().apply {
                        verify(locationManager).requestLocationUpdates(eq(networkProvider), eq(time), eq(distance), capture())
                        assertNotNull(firstValue)
                        observerer.unsubscribe()
                        verify(locationManager, times(1)).removeUpdates(eq(firstValue))
                    }
                }
    }

    @Test
    fun requestLocationUpdates_success() {
        setIsProviderEnabled(isEnabled = true)

        val provider = networkProvider
        val time = 0L
        val distance = 0.0F
        val status = 0
        val extras = mock<Bundle>()
        val location0 = buildFakeLocation(provider)
        val location1 = buildFakeLocation(provider)
        val location2 = buildFakeLocation(provider)

        doAnswer {
            val args = it.arguments
            val locationListener = args[3] as LocationListener
            locationListener.onProviderDisabled(provider)
            locationListener.onProviderEnabled(provider)
            locationListener.onStatusChanged(provider, status, extras)

            locationListener.onLocationChanged(location0)
            locationListener.onLocationChanged(location1)
            locationListener.onLocationChanged(location2)
            return@doAnswer null
        }.whenever(locationManager).requestLocationUpdates(eq(provider), eq(time), eq(distance), any<LocationListener>())

        defaultRxLocationManager.requestLocationUpdates(networkProvider, time, distance)
                .test()
                .also {
                    it.awaitTerminalEvent(10, TimeUnit.MILLISECONDS)
                    it.assertNoTerminalEvent()
                            .assertValueCount(3)
                            .onNextEvents.also {
                        assertTrue { it[0] == location0 }
                        assertTrue { it[1] == location1 }
                        assertTrue { it[2] == location2 }
                    }
                }
    }

    /**
     * Test [PermissionBehavior]
     */
    @Test
    fun test_PermissionTransformer() {
        val caller: PermissionCaller = mock()
        val applicationContext: Context = mock()

        val location = buildFakeLocation()

        whenever(context.applicationContext)
                .thenReturn(applicationContext)
        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenReturn(location)

        defaultRxLocationManager.getLastLocation(networkProvider, behaviors = PermissionBehavior(context, defaultRxLocationManager, caller))
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location)

        verify(caller, never()).requestPermissions(any())
    }

    private fun setIsProviderEnabled(provider: String = networkProvider, isEnabled: Boolean = false) {
        whenever(locationManager.isProviderEnabled(eq(provider))).thenReturn(isEnabled)
    }

    private fun buildFakeLocation(provider: String = networkProvider) =
            Location(provider)
                    .apply {
                        val r = Random()

                        latitude = r.nextDouble()
                        longitude = r.nextDouble()
                        time = System.currentTimeMillis()
                    }
}