# secondes

One second a day, shared. An open source, serverless Android app that builds a shared video diary — one second per day, vertical — and exports the film of the year. See the product vision in issue #3.

## Build and test

Requirements: JDK 17 and the Android SDK (platform 35), with `ANDROID_HOME` pointing to it.

```bash
./gradlew test             # unit tests
./gradlew assembleDebug    # debug APK in app/build/outputs/apk/debug/
```
