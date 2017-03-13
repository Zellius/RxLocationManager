package ru.solodovnikov.rxlocationmanager

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
import ru.solodovnikov.rxlocationmanager.*
import rx.observers.TestSubscriber
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.JELLY_BEAN))
class RxLocationManagerTest {
    private val networkProvider = LocationManager.NETWORK_PROVIDER
    private val scheduler = Schedulers.immediate()

    @Mock
    lateinit var locationManager: LocationManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    /**
     * Test that all fine
     */
    @Test
    fun getLastLocation_Success() {
        val expectedLocation = buildFakeLocation()

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(expectedLocation)

        val rxLocationManager = RxLocationManager(locationManager, scheduler)

        val subscriber = TestSubscriber<Location>()
        rxLocationManager.getLastLocation(networkProvider).subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertCompleted()
        subscriber.assertValue(expectedLocation)
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

        val rxLocationManager = getDefaultRxLocationManager()

        val subscriber = TestSubscriber<Location>()
        rxLocationManager.getLastLocation(networkProvider, LocationTime(30, TimeUnit.MINUTES)).subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertError(ElderLocationException::class.java)
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

        val rxLocationManager = getDefaultRxLocationManager()

        val subscriber = TestSubscriber<Location>()
        rxLocationManager.requestLocation(networkProvider).subscribe(subscriber)
        subscriber.awaitTerminalEvent(30, TimeUnit.SECONDS)
        subscriber.assertNoErrors()
        subscriber.assertCompleted()
        subscriber.assertValue(expectedLocation)
    }

    /**
     * Test that request location throw Exception if provider disabled
     */
    @Test
    fun requestLocation_ProviderDisabled() {
        //set provider disabled
        setIsProviderEnabled(isEnabled = false)

        val rxLocationManager = getDefaultRxLocationManager()

        val subscriber = TestSubscriber<Location>()
        rxLocationManager.requestLocation(networkProvider).subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertError(ProviderDisabledException::class.java)
    }

    /**
     * Test that request location throw TimeOutException
     */
    @Test
    fun requestLocation_TimeOutError() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        val rxLocationManager = getDefaultRxLocationManager()

        val subscriber = TestSubscriber<Location>()
        rxLocationManager.requestLocation(networkProvider, LocationTime(5, TimeUnit.SECONDS)).subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertError(TimeoutException::class.java)
    }

    @Test
    fun builder_Success() {
        val location1 = buildFakeLocation()

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.addLastLocation(provider = networkProvider, isNullValid = false)
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.SECONDS))
                .setDefaultLocation(location1)
                .create()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(null)

        val subscriber = TestSubscriber<Location>()
        createdObservable.subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertCompleted()
        subscriber.assertValue(location1)
    }

    /**
     * Return null if no default location is setted and no value was emitted
     */
    @Test
    fun builder_Success2() {
        val location1 = buildFakeLocation()
        location1.time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.addLastLocation(provider = networkProvider, howOldCanBe = LocationTime(10, TimeUnit.MINUTES), isNullValid = false)
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.SECONDS))
                .create()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(location1)

        val subscriber = TestSubscriber<Location>()
        createdObservable.subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertValue(null)
    }

    @Test
    fun builder_Success3() {
        val location1 = buildFakeLocation()

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.setDefaultLocation(location1).create()

        val subscriber = TestSubscriber<Location>()
        createdObservable.subscribe(subscriber)
        subscriber.awaitTerminalEvent()
        subscriber.assertValue(location1)
    }

    private fun setIsProviderEnabled(provider: String = networkProvider, isEnabled: Boolean = false) {
        Mockito.`when`(locationManager.isProviderEnabled(provider)).thenReturn(isEnabled)
    }

    private fun getDefaultRxLocationManager() = RxLocationManager(locationManager, scheduler)

    private fun getDefaultLocationRequestBuilder() = LocationRequestBuilder(getDefaultRxLocationManager(), scheduler)

    private fun buildFakeLocation(provider: String = networkProvider): Location {
        val location = Location(provider)
        location.latitude = 50.0
        location.longitude = 30.0

        return location
    }
}