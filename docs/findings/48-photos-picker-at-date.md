# Finding #48 — can the Google Photos picker be opened positioned at a given date?

## Answer

- **Android Photo Picker (`PickVisualMedia`, what the app uses): no, not documented.** None of the three Android pages read documents a date, position, start-at or initial-location parameter.
- **Google Photos Picker API (`photospicker.googleapis.com`): not verified, pages unreadable.** No yes/no is given for it. Everything said about it below is **[to verify]** by a person with web access.
- No other official mechanism was found among the pages read.

## Evidence: pages read and pages refused

No actual API call was made (no Google account, no token, none asked for). The evidence is documentation only.

**Read:**

- https://developer.android.com/training/data-storage/shared/photopicker
- https://developer.android.com/reference/androidx/activity/result/PickVisualMediaRequest
- https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia

**Not read (page not readable without web access, to verify by a person):**

- https://developers.google.com/photos/picker/guides/get-started-picker
- https://developers.google.com/photos/picker/reference/rest
- https://developers.google.com/photos/picker/reference/rest/v1/sessions

Every claim below about the Google Photos Picker API (`photospicker.googleapis.com`) is from memory and marked **[to verify]**. Repo facts are marked **[repo]**.

## Constraints found

**What the Android picker documents (read):**

- Options of `PickVisualMediaRequest`: media type (`ImageAndVideo`, `ImageOnly`, `VideoOnly`, `SingleMimeType`), default tab (`PhotosTab`, the default, or `AlbumsTab`; activity 1.10.0+), max items (1.10.0+, multi-select), ordered selection (1.10.0+), accent colour (1.10.0+), media-capabilities for transcoding (1.11.0+, API 33+). No date or position option.
- Order: the picker shows the library "sorted by date from newest to oldest". Fixed, not configurable.
- Scopes and authentication: none documented. Access is through the returned `Uri` (until device restart or app stop; `takePersistableUriPermission()` extends it, 5,000 grants per app). Consistent with the repo: no scope, no sign-in **[repo]** (`prototypes/README.md`).
- Platforms: native picker on Android 11 (API 30)+; backported through Google Play services on Android 4.4–10 and Android Go 11/12 with Play services; otherwise `ACTION_OPEN_DOCUMENT` fallback on API 19+, where the max-items limit is ignored.
- Can the user still scroll to days before and after? Yes: the picker is a scrollable library, newest first. This is what it already does today. Landing on a given day is the missing part.
- Day with no video: not documented for a date-positioned picker, since there is none. `PickVisualMedia` returns `null` when the user selects nothing or closes the picker. The current app already handles a photo or an unreadable video by opening nothing and saying so **[repo]** (`MainActivity.kt`).

**Google Photos Picker API (not read) [to verify]:**

- Web/REST flow: the app creates a *session*, gets a `pickerUri`, the user picks in Google Photos, the app polls and lists the picked media items. A max item count is the only picking option I remember (`pickingConfig`). No date or position option remembered.
- Scope: a read-only Photos Picker scope (OAuth sign-in needed). This is a different flow from the system picker the app uses today and would add sign-in, which the app does not have now.
- Whether the user can scroll around, and what happens on an empty day: unknown.

## The acceptance criteria of this issue

The criterion that asks for this rating says "four acceptance criteria" but the issue body lists six bullets; all six are rated, once, in the order of the issue. Each rating says whether the criterion's *content* can be achieved as written, with changes, or not at all. Ratings that depend on the Google Photos Picker API are provisional.

1. **Yes/no on opening at a date** — *not achievable as written*: the answer is "no, not documented" for the Android picker, and **no firm answer exists for the Google Photos Picker API** (its pages were refused). Opening the picker on the tapped day is not possible with the documented Android options; see fallbacks.
2. **Evidence cited, and which kind** — *achievable with changes*: documentation only, no call. Three Android pages read; three Photos Picker API pages refused three times (including a last attempt in this cook), so this criterion is met only for the Android picker.
3. **Constraints listed** — *achievable with changes*: complete for the Android picker. For the Photos Picker API, scope, scrolling and empty-day behaviour stay **[to verify]**.
4. **Rating of each criterion** — *achievable as written*: this section.
5. **Fallbacks listed without choosing** — *achievable as written*: see below.
6. **No feature code** — *achievable as written*: only this file is added.

Feature-level points from the title and context, for the chef's decision:

- **Tapping a day lands on that day's videos** — *not achievable as written* with the documented Android picker; a Photos Picker API date option is **[to verify]** and would add sign-in and a scope.
- **Not a filter; neighbouring days stay in reach** — *achievable as written* for the Android picker (the whole library scrolls), from the default position only.
- **User can pick a video from a neighbouring day** — *achievable as written* today (#21 places each pick on its capture day **[repo]**).
- **Day with no video** — *achievable with changes*: undocumented for a date-positioned picker; to be defined once a fallback is chosen.

## Blocker for the chef

The three developers.google.com pages could not be read by any cook, so the yes/no for the Google Photos Picker API, and its constraints, remain open. A person with web access must read them (or paste the `sessions` and `pickingConfig` reference) before that part of the question can be answered firmly.

## Candidate fallbacks (none chosen)

- **Default picker, then place by capture date (today, #21).** The user scrolls from the newest media; each pick lands on its capture day. Gives: no sign-in, works now. Costs: the scrolling the chef wants to avoid.
- **Default picker plus an in-app check after selection.** The app compares the picked video's capture date to the tapped day and says if it differs, or offers to place it on the tapped day or the capture day. Gives: protection against picking the wrong day. Costs: still no positioning, and the check happens after the pick.
- **In-app day browser over a Google Photos read scope.** The app lists videos by date itself. Gives: real positioning, with neighbouring days. Costs: sign-in, a Google scope that is probably restricted **[to verify]**, a new screen; contradicts the current "no scope" stance **[repo]**.
- **Google Photos Picker API session.** Gives: possibly another picking surface. Costs: sign-in and scope; whether it can open at a date is unread **[to verify]**.
- **Re-run this check with web access** to read the three refused pages, then decide.
