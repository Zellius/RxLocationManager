RxLocationManager
-----------------
Android library that helps to get location using standart LocationManager and RxJava. It does not use the Google Play Services and it's easier to use.

Feautures
-----------------

 - Get last known device location from any location provider. You can specify how long the location could be obtained. For example you want only those locations that have been received up to 30 minutes ago.
 - Request current device location. You can specify request time out or you can wait until observable will emit any value or error.
 - Yo can use LocationRequestBuilder to build sequence of location requests.
 - All methods will return an rxJava Observable, so you can perform transofrm/map... etc methods on it.

Samples
-----------------

**Get last known location**
```java
/*
get last known location from network provider. 
It will emitt only those locations that have been received up to hour ago
It will emitt ElderLocationException if location is too old
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime.OneHour()).subscribe();
```
**Request location**
```java
/*
Request current location with timeout. 
It will emitt ProviderDisabledException in case of timeout
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(10, TimeUnit.SECONDS)).subscribe();
```

**LocationRequestBuilder**
```java
/*
1. Try to get valid last known location
2. If last known location is not valid, try to get current location from GPS
3. Emitt a default location if no location was emitted.
*/
 final LocationRequestBuilder locationRequestBuilder = new LocationRequestBuilder(this);
locationRequestBuilder.addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.SECONDS), false)
                .addRequestLocation(LocationManager.GPS_PROVIDER, new LocationTime(10, TimeUnit.SECONDS))
                .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                .create().subscribe();
```
> **Note:** For default the LocationRequestBuilder will emit a default location in case of any exceptions. If you want to receive any exceptions you should use 
>```java
locationRequestBuilder.setReturnDefaultLocationOnError(false)
```
