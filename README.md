RxLocationManager
-----------------
Android library that helps to get location using standart LocationManager and RxJava. It does not use the Google Play Services and it's easier to use.

Download
-----------------
```xml
<dependency>
  <groupId>com.github.zellius</groupId>
  <artifactId>rxlocationmanager</artifactId>
  <version>0.1.1</version>
  <type>pom</type>
</dependency>
```
#####Gradle

######Java
```gradle
compile 'com.github.zellius:rxlocationmanager:0.1.1'
```
######Kotlin
```gradle
compile 'com.github.zellius:rxlocationmanager.kotlin:0.1.0'
```

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
It will emit only those locations that have been received up to hour ago
It will emit ElderLocationException if location is too old
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime.OneHour()).subscribe();
```
**Request location**
```java
/*
Request current location with timeout. 
It will emit ProviderDisabledException in case of timeout
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(10, TimeUnit.SECONDS)).subscribe();
```

**LocationRequestBuilder**
```java
/*
1. Try to get valid last known location
2. If last known location is not valid, try to get current location from GPS
3. Emit a default location if no location was emitted.
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

## License

```
The MIT License (MIT)

Copyright (c) 2016 Sergey Solodovnikov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
