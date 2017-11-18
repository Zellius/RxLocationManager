package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.*
import android.os.Build
import android.os.Bundle
import com.nhaarman.mockito_kotlin.*
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.JELLY_BEAN))
class RxLocationManager2Test {
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

        whenever(locationManager.getLastKnownLocation(networkProvider)).thenReturn(expectedLocation)

        defaultRxLocationManager.getLastLocation(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
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
                .await()
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(expectedLocation)
    }

    /**
     * Test that getLastLocation emit no value if [LocationManager] return null
     */
    @Test
    fun getLastKnownLocation_noLocation() {
        whenever(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(null)

        defaultRxLocationManager.getLastLocation(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertNoValues()
    }

    /**
     * Test that [LocationManager] will be unsubscribed after dispose
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
                        disposable.dispose()
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
                .await()
                .assertNoErrors()
                .assertComplete()
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
                    o.assertNotTerminated()

                    argumentCaptor<LocationListener>().apply {
                        verify(locationManager).requestSingleUpdate(eq(networkProvider), capture(), isNull())
                        firstValue.onLocationChanged(fakeLocation)
                    }

                    o.await(1, TimeUnit.SECONDS)
                    o.assertNoErrors()
                            .assertComplete()
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
                .await()
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
                .await()
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(4)
                .assertValueAt(0, {
                    (it as? BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent)?.run {
                        !enabled && this.provider == provider
                    } ?: false
                })
                .assertValueAt(1, {
                    (it as? BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent)?.run {
                        enabled && this.provider == provider
                    } ?: false
                })
                .assertValueAt(2, {
                    (it as? BaseRxLocationManager.LocationEvent.StatusChangedEvent)?.run {
                        this.provider == provider && this.status == status && this.extras == extras
                    } ?: false
                })
                .assertValueAt(3, {
                    (it as? BaseRxLocationManager.LocationEvent.LocationChangedEvent)?.run {
                        this.location == location
                    } ?: false
                })
    }

    /**
     * Test success emiting Gps status
     */
    @Test
    fun addGpsStatusListener_success() {
        whenever(locationManager.addGpsStatusListener(any())).thenReturn(true)

        defaultRxLocationManager.addGpsStatusListener()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val listener = argumentCaptor<GpsStatus.Listener>().run {
                        verify(locationManager, times(1)).addGpsStatusListener(capture())
                        firstValue
                    }.apply {
                        onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED)
                        onGpsStatusChanged(GpsStatus.GPS_EVENT_STOPPED)
                        onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                    }

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(3)
                            .assertValueSequence(arrayListOf(GpsStatus.GPS_EVENT_STARTED,
                                    GpsStatus.GPS_EVENT_STOPPED,
                                    GpsStatus.GPS_EVENT_SATELLITE_STATUS))
                            .dispose()

                    verify(locationManager, times(1)).removeGpsStatusListener(eq(listener))
                }
    }


    /**
     * Test whenever listener was added
     */
    @Test
    fun addGpsStatusListener_error() {
        whenever(locationManager.addGpsStatusListener(any())).thenReturn(false)

        defaultRxLocationManager.addGpsStatusListener()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertValueCount(0)
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(1)
                .assertValue(defaultLocation)
    }

    /**
     * * Request location - [ProviderDisabledException]
     * * Last Location - null
     * * Request location - [ProviderDisabledException]
     *
     * Will emit no values
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertNoValues()
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
                .await()
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
                .await()
                .assertNoErrors()
                .assertComplete()
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
                .await()
                .assertNoErrors()
                .assertComplete()
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(location)
    }

    @Test
    fun test_GetProvider() {
        val provider = mock<LocationProvider>()

        whenever(locationManager.getProvider(eq(networkProvider)))
                .thenReturn(provider)

        defaultRxLocationManager.getProvider(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(provider)

        verify(locationManager, only()).getProvider(eq(networkProvider))
    }

    @Test
    fun test_GetProviderEmpty() {
        whenever(locationManager.getProvider(eq(networkProvider)))
                .thenReturn(null)

        defaultRxLocationManager.getProvider(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValueCount(0)

        verify(locationManager, only()).getProvider(eq(networkProvider))
    }

    @Test
    fun test_IsProviderEnabled() {
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.isProviderEnabled(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
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
                .await()
                .assertNoErrors()
                .assertComplete()
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
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(networkProvider)

        verify(locationManager, only()).getBestProvider(eq(c), eq(enabledOnly))
    }

    @Test
    fun test_GetGpsStatusEmpty() {
        val gpsStatus = mock<GpsStatus>()

        whenever(locationManager.getGpsStatus(isNull())).thenReturn(gpsStatus)

        defaultRxLocationManager.getGpsStatus()
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(gpsStatus)
    }

    @Test
    fun test_GetGpsStatusNotEmpty() {
        val gpsStatus = mock<GpsStatus>()

        whenever(locationManager.getGpsStatus(eq(gpsStatus))).thenReturn(gpsStatus)

        defaultRxLocationManager.getGpsStatus(gpsStatus)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(gpsStatus)
    }

    @Test
    fun getAllProviders() {
        val providers = listOf("provider")

        whenever(locationManager.allProviders).thenReturn(providers)

        defaultRxLocationManager.getAllProviders()
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
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
                    it.await(10, TimeUnit.MILLISECONDS)
                    it.assertNotTerminated()
                            .assertValueCount(6)
                            .assertValueAt(0, {
                                (it as? BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent)?.run {
                                    !enabled && this.provider == provider
                                } ?: false
                            })
                            .assertValueAt(1, {
                                (it as? BaseRxLocationManager.LocationEvent.ProviderEnableStatusEvent)?.run {
                                    enabled && this.provider == provider
                                } ?: false
                            })
                            .assertValueAt(2, {
                                (it as? BaseRxLocationManager.LocationEvent.StatusChangedEvent)?.run {
                                    this.provider == provider && this.status == status && this.extras == extras
                                } ?: false
                            })
                            .assertValueAt(3, {
                                (it as? BaseRxLocationManager.LocationEvent.LocationChangedEvent)?.run {
                                    this.location == location
                                } ?: false
                            })
                            .assertValueAt(4, {
                                (it as? BaseRxLocationManager.LocationEvent.LocationChangedEvent)?.run {
                                    this.location == location
                                } ?: false
                            })
                            .assertValueAt(5, {
                                (it as? BaseRxLocationManager.LocationEvent.LocationChangedEvent)?.run {
                                    this.location == location
                                } ?: false
                            })
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
                        observerer.dispose()
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
                    it.await(10, TimeUnit.MILLISECONDS)
                    it.assertNotTerminated()
                            .assertValueCount(3)
                            .assertValueAt(0, location0)
                            .assertValueAt(1, location1)
                            .assertValueAt(2, location2)
                }
    }

    /**
     * Test [RxLocationManager.addNmeaListener]
     */
    @Test
    fun addNmeaListener_test() {
        val message = "test_message"
        val timestamp = System.currentTimeMillis()


        whenever(locationManager.addNmeaListener(any<GpsStatus.NmeaListener>())).thenReturn(true)

        defaultRxLocationManager.addNmeaListener()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val listener = argumentCaptor<GpsStatus.NmeaListener>().run {
                        verify(locationManager, times(1)).addNmeaListener(capture())
                        firstValue
                    }.apply {
                        onNmeaReceived(timestamp, message)
                        onNmeaReceived(timestamp, message)
                    }

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(2)
                            .assertValueAt(0, { it.nmea == message && it.timestamp == timestamp })
                            .assertValueAt(1, { it.nmea == message && it.timestamp == timestamp })
                            .dispose()

                    verify(locationManager, times(1)).removeNmeaListener(listener)
                }
    }

    /**
     * Test whenever [RxLocationManager.addNmeaListener] throw exception if callback is not registered
     */
    @Test
    fun addNmeaListener_error() {
        defaultRxLocationManager.addNmeaListener()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
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
                .await()
                .assertNoErrors()
                .assertComplete()
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