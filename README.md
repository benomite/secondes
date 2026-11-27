# secondes

secondes is an open source, serverless Android app that builds a shared video diary — one second per day, vertical — and exports the film of the year. One second a day, shared.

## Install

secondes is distributed as an APK from GitHub Releases, not through the app stores.

1. Download the APK from the [Releases page](../../releases) of this repository.
2. Allow installing from unknown sources for the app you downloaded it with (your browser or file manager); Android asks for this the first time.
3. Open the APK and confirm the installation.

Installing a newer APK over the previous one keeps your journal and your sign-in.

The official build is published by the chef (the repository owner) under their own Google Cloud project. If you build the app yourself, it is not covered by that project: you need your own Google credentials (see below).

## Share the journal with your family

The shared journal is a folder in the Google Drive of one member of the family. secondes has no server: the app only reads and writes that folder, with the `drive.file` access (the files it created or that you picked, nothing else of your Drive).

1. **One member creates the journal.** Open the app, tap *Sign in with Google*, then *Create a journal*. The app creates a folder named `secondes` in your Drive.
2. **Share the folder.** On the next screen, tap *Open the folder in Drive* and share the folder with the Google account of each other member, as **editor**.
3. **Each other member joins.** On their own phone, open the app, tap *Sign in with Google*, then *Join a journal*, and pick the shared folder in Google's picker.
4. **Each member types their name** in the settings: the days they fill are signed with it.

The days are sent when the app opens and as soon as a day is filled. Without network, a day stays on the phone, marked *Not sent*, and goes at the next sync. Opening the app brings the other members' days.

### Read the journal without the app

The folder holds one plain JSON file per member per day, named `<date> <name>.json`, for example `2026-11-17 Léa.json`. `<name>` is the member's name as typed in the settings; the characters `\ / : * ? " < > |` and the control characters in it are replaced by `_`.

The JSON file looks like this:

```json
{
  "date": "2026-11-17",
  "author": "Léa",
  "clip": {
    "videoUri": "content:\/\/media\/picker\/0\/com.android.providers.media.photopicker\/media\/1000000042",
    "startMs": 4000,
    "endMs": 5000
  }
}
```

The fields:

- `date`: the day, as `yyyy-MM-dd`.
- `author`: the member who filled the day.
- `clip.startMs` and `clip.endMs`: the chosen second, the window from `startMs` to `endMs`, in milliseconds from the start of the video.
- `clip.videoUri`: the address of the video on the author's phone (`\/` is an escaped `/`). It is only meaningful on the phone that picked the video.

To read the journal by hand, without the app:

1. Open the folder in Google Drive (or download it, *Download* gives a zip).
2. Open the `.json` of the day with any text editor, for the date, the member and the chosen second.
3. To see the second, find the video in the author's Google Photos by its date, and go to the second at `startMs`.

The clip change (epic #71) makes the app also write, next to each JSON file, a one-second `<date> <name>.mp4` clip with the same base name. The app does not write this file yet: until it does, the folder holds only the JSON files, and step 3 is the way to see the second.

When two members filled the same day, the app shows the JSON file modified last.

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

The Drive folder picker (*Join a journal*) needs two values, which are never committed to the repository:

- `SECONDES_PICKER_API_KEY`: the API key of step 5;
- `SECONDES_GOOGLE_PROJECT_NUMBER`: the project number of step 1.

The build reads each one from an environment variable, a Gradle property (`-P` or `~/.gradle/gradle.properties`) or the uncommitted `local.properties` at the root of the repository, for example:

```properties
SECONDES_PICKER_API_KEY=<your API key>
SECONDES_GOOGLE_PROJECT_NUMBER=<your project number>
```

The release workflow (`.github/workflows/release.yml`) takes them from the repository secrets of the same names: a fork that publishes its own APK adds these two secrets. Without them, the app still builds and works, and *Join a journal* says the picker is not set up in this build. The API key ends up in the APK, where it can be read: keep it restricted to the Picker API.

Sign-in needs no client ID in the code: the OAuth client of step 4 is of type *Android*, with the package name `io.github.benomite.secondes` and the SHA-1 of the certificate your APK is signed with, and Google matches it by those.

### Build the APK

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`; install it with `adb install -r`. `./gradlew test` runs the unit tests.

## License

secondes is released under the [MIT license](LICENSE).
