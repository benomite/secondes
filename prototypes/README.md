# Prototypes — Google Photos Picker and shared Drive folder (issue #4)

Two small prototypes to settle the two Google integrations the product rests on. **Nothing here has been run against real Google accounts yet**: the manual checks below are left to the chef (two accounts, two phones), and the results table is empty on purpose.

| Prototype | Where | What it does |
|---|---|---|
| Photos Picker (Android) | `app/` — `MainActivity.kt` (button, picker call, `DATE_TAKEN` read), `PickedVideo.kt` (picker result → `(uri, captureDate)` record), test `PickedVideoTest.kt` | Opens the system Photo Picker restricted to videos (`PickVisualMediaRequest(VideoOnly)`, multi-select via `PickMultipleVisualMedia`) and lists each selected video's content URI and capture date (or "no capture date"). |
| Drive folder picker (web) | `prototypes/drive-picker/index.html` | Static page, HTML/JS only. Asks for the `drive.file` scope, opens the Google Picker on folders, shows the selected folder id and name. |

## Scopes and servers — how to check

The only Google scope requested anywhere is `https://www.googleapis.com/auth/drive.file`. The Android prototype requests no Google scope at all (the Photo Picker needs no permission and no sign-in). Neither prototype calls a server of ours: the only network calls are Google's own scripts and Google's Picker, loaded by the web page.

Check that no restricted scope appears in the code or configuration (must print nothing):

```bash
grep -rnE 'auth/drive(\.readonly)?([^.a-z]|$)' app/src prototypes/drive-picker app/build.gradle.kts
```

List the scopes and hosts actually used (must show `drive.file` only, and Google hosts only):

```bash
grep -rnE 'auth/|https?://' app/src/main prototypes/drive-picker
```

## Setup

### Android — Photos Picker

1. JDK 17 and the Android SDK (platform 35), `ANDROID_HOME` set (see the root README).
2. `./gradlew test` runs the mapping unit test; `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`; `adb install -r` it on the phone.
3. No OAuth client, key or test user is needed for this prototype.
4. The phone needs the Google Photos app signed in, with Google Photos set as the cloud media provider for the system picker (Photo Picker → "Photos" tab shows the account's library; the Photo Picker menu lets you switch account/provider).

### Web — Google Picker with `drive.file`

1. In the [Google Cloud console](https://console.cloud.google.com/), create (or choose) a project; note its **project number** (App ID).
2. APIs & Services → Library: enable **Google Picker API** and **Google Drive API**.
3. APIs & Services → OAuth consent screen: user type External, publishing status **Testing**. Under Scopes add only `.../auth/drive.file`. Under **Test users** add the Google accounts of A and B (up to 100).
4. Credentials → Create credentials → **OAuth client ID**, type *Web application*. Add the authorized JavaScript origin of the page (for local use: `http://localhost:8000`).
5. Credentials → Create credentials → **API key**; restrict it to the Picker API and to the page's HTTP referrer.
6. Serve the page: `cd prototypes/drive-picker && python3 -m http.server 8000`, then open `http://localhost:8000/` (on a phone, host it on any static HTTPS site and add that origin to step 4).
7. Paste the client ID, API key and project number into the three fields; keys are not stored in the code.

## Manual test protocol (two accounts, two phones)

Account A = the chef, account B = the partner. Fill the results table after each step.

**1. Photos Picker, own library.** On A's phone, open the app, tap *Pick videos*, select several videos at once. Expected: the app lists each video's URI and capture date.

**2. Photos Picker, partner-shared videos.** With Google Photos partner sharing set up from B to A, open the Picker on A's phone, go to the Photos tab, and select videos taken by B (partner-shared). Expected: they are selectable and listed with a URI and a capture date. Note whether the date is present or "no capture date".

**3. Photos Picker, shared album.** Same, with videos that B put in a shared album A joined. Select several at once, mixing own, partner-shared and album videos.

**4. Create the journal folder on A.** On A's phone, open the web page, sign in as A, create a folder in Drive (Drive app) named e.g. `secondes-test`. Note: `drive.file` only gives the app access to files it created or the user picked — check whether the prototype page can pick a folder A created in the Drive app, and whether the Picker lists it.
   *Limit of this prototype:* it has no "create folder" button; the folder is created in the Drive app. If the chef wants proof that a folder created **by the app** is the one B joins, add a Drive API `files.create` call (mimeType `application/vnd.google-apps.folder`) to the page — not done here.

**5. Share with B.** In Drive, share the folder with B's account as editor.

**6. B joins.** On B's phone, sign in as B on the page and open the Picker on the shared folder (Picker "Shared with me"). Selecting it is the one-time gesture. Expected: the page shows the folder id and name. Note whether the folder is listed and selectable at all.

**7. B adds a clip.** Check whether B's app, now granted the folder, can upload a video into it. This prototype does not upload; test by adding a clip to the folder in the Drive app as B, and record whether an app limited to `drive.file` could list and write the folder's content (for the product, an upload call with the folder id as parent must be tried next).

**8. A sees B's clip.** On A's phone, check the clip appears in the folder (Drive app, and whether the app with `drive.file` can list it).

## Decision rule

If `drive.file` cannot let account B join the shared folder (step 6 fails, or B's app cannot read or write the folder's content after joining), switch to the **full Drive scope without Google verification**: users accept the "unverified app" screen once; the app is limited to 100 users, enough while secondes stays private. Google's security assessment for restricted scopes is only needed for a public release. Drive stays the storage in both cases (alternatives reviewed in the vision: shared Google Photos albums, peer-to-peer sync, Nextcloud/WebDAV, Dropbox — none is better).

## Results template

Copy into a comment on issue #4.

| Criterion | Works / does not work | Consequence for the product |
|---|---|---|
| Photos Picker: select several videos at once (own library) | | |
| Photos Picker: partner-shared videos selectable, URI received | | |
| Photos Picker: shared-album videos selectable, URI received | | |
| Photos Picker: capture date received for each video | | |
| A creates a journal folder and shares it with B | | |
| B joins the folder with the Picker (`drive.file`, one-time gesture) | | |
| B sees and adds clips to the folder | | |
| A sees B's clips | | |
| No server of ours, no restricted scope (grep check above) | | |

Chosen route: `drive.file` / full Drive scope without verification (strike one).
