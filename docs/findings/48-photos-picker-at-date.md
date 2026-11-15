# Finding #48 — can the Google Photos picker be opened positioned at a given date?

## Answer

**No, not documented.** None of the three Android pages read documents a date, position, start-at or initial-location parameter for the picker the app uses. The Google Photos Picker API pages could not be read, so for that API the "no" is a presumption **[to verify]**. No other official mechanism was found.

## Evidence: pages read and pages refused

No actual API call was made (no Google account, no token, none asked for). The evidence is documentation only.

**Read (this session):**

- https://developer.android.com/training/data-storage/shared/photopicker
- https://developer.android.com/reference/androidx/activity/result/PickVisualMediaRequest
- https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia

**Refused (WebFetch denied in "don't ask" mode), not read:**

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

## The four acceptance criteria of this issue

The ticket text I received does not list four feature criteria apart from the six feasibility bullets. I rate the four requirements stated in its title and context:

1. **Tapping a day opens Google Photos on that day's videos** — *not achievable as written* with the picker as documented (no date parameter). Achievable only with a change (see fallbacks). If the Google Photos Picker API turns out to have a date option **[to verify]**, it would still need sign-in and a scope.
2. **Not a filter: days just before and after stay in reach** — *achievable as written* for the Android picker (it is not a filter, the whole library scrolls), but only from the default position, not from the tapped day.
3. **The user can pick a video from a neighbouring day** — *achievable as written* today (#21 already places each picked video on its capture day **[repo]**).
4. **Day with no video** — *achievable with changes*: nothing documented for a date-positioned picker; the behaviour must be defined by the chef once a fallback is chosen.

## Candidate fallbacks (none chosen)

- **Default picker, then place by capture date (today, #21).** The user scrolls from the newest media; each pick lands on its capture day. Gives: no sign-in, works now. Costs: the scrolling the chef wants to avoid.
- **Default picker plus an in-app check after selection.** The app compares the picked video's capture date to the tapped day and says if it differs, or offers to place it on the tapped day or the capture day. Gives: protection against picking the wrong day. Costs: still no positioning, and the check happens after the pick.
- **In-app day browser over a Google Photos read scope.** The app lists videos by date itself. Gives: real positioning, with neighbouring days. Costs: sign-in, a Google scope that is probably restricted **[to verify]**, a new screen; contradicts the current "no scope" stance **[repo]**.
- **Google Photos Picker API session.** Gives: possibly another picking surface. Costs: sign-in and scope; whether it can open at a date is unread **[to verify]**.
- **Re-run this check with web access** to read the three refused pages, then decide.
