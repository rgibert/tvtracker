# TV Tracker

A small, free Trakt client for keeping up with **TV shows only** (no movies).
It is meant to be a lighter alternative to bigger all-in-one Trakt apps.

## What it does

- **To Watch** – combines Trakt's Up Next feed with shows on your watchlist, so
  every show has one clear next episode.
- **Coming Up** – episodes airing in the next 14 days for the shows you watch
  and the shows on your watchlist, grouped by local day.
- **Show details** – full season and episode list with watched checkboxes, a
  "mark watched" shortcut for the next episode, and per-show progress.
- **Offline-friendly** – the last list and show details are cached on the device
  and shown (with a "saved data" banner) when Trakt can't be reached.
- **No account system of its own** – Trakt device-code login, no password ever
  entered into this app, no subscriptions or ads.

## First run

Trakt requires every third-party app to use its own free API key.

1. Sign in at [trakt.tv](https://trakt.tv) and create a new API app at
   <https://app.trakt.tv/settings/apps/api/new>.
2. Copy the **Client ID** (a long string of letters and numbers). A client
   secret is not needed for the current device flow; the field is kept under
   "Advanced" only for older Trakt apps.
3. Open TV Tracker, paste the Client ID, and tap **Continue**.
4. Tap **Connect with Trakt**, then enter the short code shown in the app at
   the Trakt activation page (the app has a button that opens it).
5. Approve the app. The show lists load automatically.

The Client ID is stored on the device so this is only needed once. Use
**Settings → Trakt API app → Change** to switch to a different API app.

## Build

Requirements: JDK 17+ and an Android SDK with platform 35.

```sh
./gradlew :app:assembleDebug
# or install on a connected device/emulator
./gradlew :app:installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the checks with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Notes

- Package name: `ca.gibert.tvtracker`. Minimum Android version: 8.0 (API 26).
- Tokens live in app-private DataStore storage; list snapshots are plain JSON
  files in the app's private cache directory. Signing out clears both.
- Access tokens last 7 days. The app refreshes them automatically; the refresh
  token is single-use and rolled over on each refresh.
- The calendar request uses a few extra UTC days and then filters to the
  device's local 14-day window, so time zones near midnight behave correctly.
- This is an unofficial app and is not affiliated with Trakt.
