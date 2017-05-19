package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.Mockito.doAnswer
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rx.Single
import rx.schedulers.Schedulers
import java.lang.Throwable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertNotNull

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
        `when`(context.getSystemService(eq(Context.LOCATION_SERVICE)))
                .thenReturn(locationManager)
    }

    /**
     * Test that getLastLocation works fine
     */
    @Test
    fun testGetLastLocation_Success() {
        val expectedLocation = buildFakeLocation()

        `when`(locationManager.getLastKnownLocation(networkProvider))
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
    fun testGetLastLocation_Old() {
        `when`(locationManager.getLastKnownLocation(networkProvider))
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
    fun testGetLastLocation_NotOld() {
        val expectedLocation = buildFakeLocation()

        `when`(locationManager.getLastKnownLocation(networkProvider))
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
    fun testGetLastLocation_NoLocation() {
        `when`(locationManager.getLastKnownLocation(networkProvider))
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
    fun testRequestLocation_Unsubscribe() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.requestLocation(networkProvider)
                .subscribe()
                .also { disposable ->
                    ArgumentCaptor.forClass(LocationListener::class.java)
                            .apply {
                                verify(locationManager).requestSingleUpdate(eq(networkProvider), capture(), isNull())
                                assertNotNull(value)
                                disposable.unsubscribe()
                                verify(locationManager, times(1)).removeUpdates(eq(value))
                            }
                }
    }

    @Test
    fun testRequestLocation_Success() {
        val expectedLocation = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        //answer
        doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onLocationChanged(expectedLocation)
            return@doAnswer null
        }.`when`(locationManager).requestSingleUpdate(eq(networkProvider), any(), any())

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that request location throw [ProviderDisabledException] if provider disabled
     */
    @Test
    fun testRequestLocation_ProviderDisabled() {
        //set provider disabled
        setIsProviderEnabled(isEnabled = false)

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertError(ProviderDisabledException::class.java)
    }

    /**
     * Test that request location throw [TimeoutException]
     */
    @Test
    fun testRequestLocation_TimeOutError() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.requestLocation(networkProvider, LocationTime(10, TimeUnit.MILLISECONDS))
                .test()
                .awaitTerminalEvent()
                .assertError(TimeoutException::class.java)
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

        `when`(locationManager.getLastKnownLocation(eq(networkProvider)))
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

        `when`(locationManager.getLastKnownLocation(eq(networkProvider)))
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

        `when`(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenThrow(ex)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider, transformer = IgnoreErrorTransformer(listOf(SecurityException::class.java)))
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

        `when`(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenThrow(ex)

        defaultLocationRequestBuilder.addRequestLocation(networkProvider, LocationTime(5, TimeUnit.MILLISECONDS))
                .addLastLocation(networkProvider, transformer = IgnoreErrorTransformer())
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

    private fun setIsProviderEnabled(provider: String = networkProvider, isEnabled: Boolean = false) {
        `when`(locationManager.isProviderEnabled(eq(provider))).thenReturn(isEnabled)
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