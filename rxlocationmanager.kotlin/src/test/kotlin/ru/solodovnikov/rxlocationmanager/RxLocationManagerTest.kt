package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
        Mockito.`when`(context.getSystemService(Mockito.eq(Context.LOCATION_SERVICE)))
                .thenReturn(locationManager)
    }

    /**
     * Test that all fine
     */
    @Test
    fun getLastLocation_Success() {
        val expectedLocation = buildFakeLocation()

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(expectedLocation)

        defaultRxLocationManager.getLastLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that getLastLocation throw ElderLocationException if howOldCanBe is provided
     *
     */
    @Test
    fun getLastLocation_Old() {
        val expectedLocation = buildFakeLocation()

        expectedLocation.time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(expectedLocation)

        defaultRxLocationManager.getLastLocation(networkProvider, LocationTime(30, TimeUnit.MINUTES))
                .test()
                .awaitTerminalEvent()
                .assertError(ElderLocationException::class.java)
    }

    @Test
    fun requestLocation_Success() {
        val expectedLocation = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        //answer
        Mockito.doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onLocationChanged(expectedLocation)
            return@doAnswer null
        }.`when`(locationManager).requestSingleUpdate(Mockito.eq(networkProvider), Mockito.any(), Mockito.any())

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .awaitTerminalEvent(30, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertCompleted()
                .assertValue(expectedLocation)
    }

    /**
     * Test that request location throw Exception if provider disabled
     */
    @Test
    fun requestLocation_ProviderDisabled() {
        //set provider disabled
        setIsProviderEnabled(isEnabled = false)

        defaultRxLocationManager.requestLocation(networkProvider)
                .test()
                .awaitTerminalEvent()
                .assertError(ProviderDisabledException::class.java)
    }

    /**
     * Test that request location throw TimeOutException
     */
    @Test
    fun requestLocation_TimeOutError() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        defaultRxLocationManager.requestLocation(networkProvider, LocationTime(10, TimeUnit.MILLISECONDS))
                .test()
                .awaitTerminalEvent()
                .assertError(TimeoutException::class.java)
    }

    @Test
    fun builder_Success() {
        val location1 = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider))
                .thenReturn(null)

        defaultLocationRequestBuilder.addLastLocation(provider = networkProvider)
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.MILLISECONDS))
                .setDefaultLocation(location1)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location1)
    }

    /**
     * Return null if no default location is setted and no value was emitted
     */
    @Test
    fun builder_Success2() {
        val location1 = buildFakeLocation()
        location1.time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(location1)

        defaultLocationRequestBuilder.addLastLocation(provider = networkProvider, howOldCanBe = LocationTime(10, TimeUnit.MINUTES))
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.MILLISECONDS))
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(null)
    }

    @Test
    fun builder_Success3() {
        val location1 = buildFakeLocation()


        defaultLocationRequestBuilder.setDefaultLocation(location1)
                .create()
                .test()
                .awaitTerminalEvent()
                .assertNoErrors()
                .assertCompleted()
                .assertValue(location1)
    }

    private fun setIsProviderEnabled(provider: String = networkProvider, isEnabled: Boolean = false) {
        Mockito.`when`(locationManager.isProviderEnabled(provider)).thenReturn(isEnabled)
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