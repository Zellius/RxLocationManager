package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.*
import android.os.Build
import com.nhaarman.mockito_kotlin.*
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.N))
class RxLocationManager2TestN {
    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager by lazy { RxLocationManager(context, Schedulers.trampoline()) }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(context.getSystemService(eq(Context.LOCATION_SERVICE)))
                .thenReturn(locationManager)
    }

    /**
     * Test [RxLocationManager.addGnssStatusListener]
     */
    @Test
    fun addGnssStatusListener_success() {
        val ttffMillis = 20
        val status0 = mock<GnssStatus>()
        val status1 = mock<GnssStatus>()

        whenever(locationManager.registerGnssStatusCallback(any())).thenReturn(true)

        defaultRxLocationManager.addGnssStatusListener()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val callback = argumentCaptor<GnssStatus.Callback>().run {
                        verify(locationManager, times(1)).registerGnssStatusCallback(capture())
                        firstValue
                    }.apply {
                        onStarted()
                        onStopped()
                        onFirstFix(ttffMillis)
                        onSatelliteStatusChanged(status0)
                        onSatelliteStatusChanged(status1)
                    }

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(5)
                            .assertValueAt(0, { it is BaseRxLocationManager.GnssStatusResponse.GnssStarted })
                            .assertValueAt(1, { it is BaseRxLocationManager.GnssStatusResponse.GnssStopped })
                            .assertValueAt(2, { it is BaseRxLocationManager.GnssStatusResponse.GnssFirstFix && it.ttffMillis == ttffMillis })
                            .assertValueAt(3, { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status0 })
                            .assertValueAt(4, { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status1 })
                            .dispose()

                    verify(locationManager, times(1)).unregisterGnssStatusCallback(callback)
                }
    }

    /**
     * Test whenever [RxLocationManager.addGnssStatusListener] throw exception if callback is not registered
     */
    @Test
    fun addGnssStatusListener_error() {
        whenever(locationManager.registerGnssStatusCallback(any())).thenReturn(false)

        defaultRxLocationManager.addGnssStatusListener()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    /**
     * Test [RxLocationManager.addNmeaListenerN]
     */
    @Test
    fun addNmeaListener_test() {
        val message = "test_message"
        val timestamp = System.currentTimeMillis()


        whenever(locationManager.addNmeaListener(any<OnNmeaMessageListener>())).thenReturn(true)

        defaultRxLocationManager.addNmeaListenerN()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val listener = argumentCaptor<OnNmeaMessageListener>().run {
                        verify(locationManager, times(1)).addNmeaListener(capture())
                        firstValue
                    }.apply {
                        onNmeaMessage(message, timestamp)
                        onNmeaMessage(message, timestamp)
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
     * Test whenever [RxLocationManager.addNmeaListenerN] throw exception if callback is not registered
     */
    @Test
    fun addNmeaListener_error() {
        defaultRxLocationManager.addNmeaListenerN()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    @Test
    fun registerGnssMeasurementsCallback_success() {
        val status0 = GnssMeasurementsEvent.Callback.STATUS_READY
        val status1 = GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED
        val event = mock<GnssMeasurementsEvent>()

        whenever(locationManager.registerGnssMeasurementsCallback(any())).thenReturn(true)

        defaultRxLocationManager.registerGnssMeasurementsCallback()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val callback = argumentCaptor<GnssMeasurementsEvent.Callback>().run {
                        verify(locationManager, times(1)).registerGnssMeasurementsCallback(capture())
                        firstValue
                    }.apply {
                        onStatusChanged(status0)
                        onStatusChanged(status1)
                        onGnssMeasurementsReceived(event)
                    }

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(3)
                            .assertValueAt(0, { it is BaseRxLocationManager.GnssMeasurementsResponse.StatusChanged && it.status == status0 })
                            .assertValueAt(1, { it is BaseRxLocationManager.GnssMeasurementsResponse.StatusChanged && it.status == status1 })
                            .assertValueAt(2, { it is BaseRxLocationManager.GnssMeasurementsResponse.GnssMeasurementsReceived && it.eventArgs == event })
                            .dispose()

                    verify(locationManager, times(1)).unregisterGnssMeasurementsCallback(callback)
                }
    }

    @Test
    fun registerGnssMeasurementsCallback_error() {
        defaultRxLocationManager.registerGnssMeasurementsCallback()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    @Test
    fun registerGnssNavigationMessageCallback_success() {
        val status0 = GnssNavigationMessage.Callback.STATUS_READY
        val status1 = GnssNavigationMessage.Callback.STATUS_LOCATION_DISABLED
        val event = mock<GnssNavigationMessage>()

        whenever(locationManager.registerGnssNavigationMessageCallback(any())).thenReturn(true)

        defaultRxLocationManager.registerGnssNavigationMessageCallback()
                .test()
                .also { o ->
                    o.assertNotTerminated()

                    val callback = argumentCaptor<GnssNavigationMessage.Callback>().run {
                        verify(locationManager, times(1)).registerGnssNavigationMessageCallback(capture())
                        firstValue
                    }.apply {
                        onStatusChanged(status0)
                        onStatusChanged(status1)
                        onGnssNavigationMessageReceived(event)
                    }

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(3)
                            .assertValueAt(0, { it is BaseRxLocationManager.GnssNavigationResponse.StatusChanged && it.status == status0 })
                            .assertValueAt(1, { it is BaseRxLocationManager.GnssNavigationResponse.StatusChanged && it.status == status1 })
                            .assertValueAt(2, { it is BaseRxLocationManager.GnssNavigationResponse.GnssNavigationMessageReceived && it.event == event })
                            .dispose()

                    verify(locationManager, times(1)).unregisterGnssNavigationMessageCallback(callback)
                }
    }

    @Test
    fun registerGnssNavigationMessageCallback_error() {
        defaultRxLocationManager.registerGnssNavigationMessageCallback()
                .test()
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }
}