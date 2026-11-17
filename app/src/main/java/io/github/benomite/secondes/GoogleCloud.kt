package io.github.benomite.secondes

/**
 * The Google Cloud project of the official build, committed here so the APK of the GitHub release works as is, with
 * no build option and no local property. The Drive file picker, which lets a member join the family's folder,
 * needs the project's browser API key (restricted to the Picker API) and its project number; neither is a secret,
 * the page that shows the picker hands both to Google. Sign-in needs no client ID in the code: Google matches the
 * Android OAuth client by package name and signing certificate. A build from another Google Cloud project changes
 * these two values (see the README). Empty: 'Join a journal' says the picker is not set up in this build.
 */
object GoogleCloud {
    const val PICKER_API_KEY = ""
    const val PROJECT_NUMBER = ""
}
