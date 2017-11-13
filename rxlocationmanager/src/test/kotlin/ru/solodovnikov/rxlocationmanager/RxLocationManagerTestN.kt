package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.N))
class RxLocationManagerTestN {
    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager by lazy { RxLocationManager(context, Schedulers.immediate()) }

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
                    o.assertNoTerminalEvent()

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

                    o.awaitTerminalEvent(1, TimeUnit.MILLISECONDS)

                    o.assertNoErrors()
                            .assertNotCompleted()
                            .assertValueCount(5)
                            .apply {
                                val values = onNextEvents
                                assertTrue { values[0] is BaseRxLocationManager.GnssStatusResponse.GnssStarted }
                                assertTrue { values[1] is BaseRxLocationManager.GnssStatusResponse.GnssStopped }
                                values[2].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssFirstFix && it.ttffMillis == ttffMillis }
                                }
                                values[3].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status0 }
                                }
                                values[4].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status1 }
                                }
                            }.unsubscribe()

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
                .awaitTerminalEvent()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }
}