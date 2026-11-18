package io.github.benomite.secondes

/**
 * The Google Cloud values the Drive file picker needs to let a member join the family's folder: the project's API key
 * (restricted to the Picker API) and its project number. They are never committed: the build reads them from
 * SECONDES_PICKER_API_KEY and SECONDES_GOOGLE_PROJECT_NUMBER (repository secrets for the release workflow; env vars,
 * Gradle properties or local.properties for a local or forked build, see the README). Sign-in needs no client ID in
 * the code: Google matches the Android OAuth client by package name and signing certificate. Empty: 'Join a journal'
 * says the picker is not set up in this build.
 */
object GoogleCloud {
    val PICKER_API_KEY: String = BuildConfig.PICKER_API_KEY
    val PROJECT_NUMBER: String = BuildConfig.GOOGLE_PROJECT_NUMBER

    val pickerSetUp: Boolean get() = PICKER_API_KEY.isNotBlank() && PROJECT_NUMBER.isNotBlank()
}
