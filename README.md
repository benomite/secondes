# secondes

secondes is an open source, serverless Android app that builds a shared video diary — one second per day, vertical — and exports the film of the year. One second a day, shared.

## Install

secondes is distributed as an APK from GitHub Releases, not through the app stores.

1. Download the APK from the [Releases page](../../releases) of this repository.
2. Allow installing from unknown sources for the app you downloaded it with (your browser or file manager); Android asks for this the first time.
3. Open the APK and confirm the installation.

Installing a newer APK over the previous one keeps your journal and your sign-in.

The official build is published by the chef (the repository owner) under their own Google Cloud project. If you build the app yourself, it is not covered by that project: you need your own Google credentials (see below).

## Build it yourself

### Prerequisites

- JDK 17
- The Android SDK (platform 35), with `ANDROID_HOME` pointing to it

### Google Cloud project, consent screen and OAuth credentials

Create your own Google Cloud project in the [Google Cloud console](https://console.cloud.google.com/):

1. Create (or choose) a project and note its **project number**.
2. APIs & Services → Library: enable **Google Picker API** and **Google Drive API**.
3. APIs & Services → OAuth consent screen: user type External, publishing status **Testing**. Add only the scope `https://www.googleapis.com/auth/drive.file`, and add the Google accounts that will use your build as **Test users**.
4. Credentials → Create credentials → **OAuth client ID**.
5. Credentials → Create credentials → **API key**, restricted to the Picker API.

### Where the credentials go

Today the Android app in `app/` reads no Google credential: there is no client ID, key or setting in `app/build.gradle.kts`, in the sources or in `local.properties`, and the Photos Picker it uses needs no sign-in. The only place that takes credentials is the Drive folder picker prototype, `prototypes/drive-picker/index.html`: you paste the OAuth client ID (type *Web application*, with `http://localhost:8000` as authorized JavaScript origin), the API key and the project number into its three fields; nothing is stored in the code. See `prototypes/README.md` for the full procedure. This section will be updated when the app itself reads credentials.

### Build the APK

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`; install it with `adb install -r`. `./gradlew test` runs the unit tests.

## License

secondes is released under the [MIT license](LICENSE).
