## [1.0.0] - 2017-05-19
### Added
- RxJava2 support! Please look at rxlocationmanager-rxjava2 project.
- Samples.
### Changed
- Both libraries (RxJava1 and RxJava2 implementations) rewritten to [Kotlin](https://kotlinlang.org/).
But you still can use it in your Java projects.
### Removed
- There is no location _uses-permission_. You need to add them to your app manifest.
- There is no setReturnDefaultLocationOnError method in the LocationRequestBuilder.
You can use a "transformer" property in any method to ignore errors (e.g. by **IgnoreErrorTransformer**).