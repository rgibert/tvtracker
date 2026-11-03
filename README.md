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

If the app was built with a Trakt **Client ID** (see
[Build](#trakt-client-id-at-build-time)), it skips API setup and opens straight
on the Trakt connection step.

1. Open TV Tracker and tap **Connect with Trakt**.
2. Enter the short code shown in the app at the Trakt activation page (the app
   has a button that opens it), and approve the app.
3. The show lists load automatically.

To use a different Trakt API app, create one at
<https://app.trakt.tv/settings/apps/api/new> (Trakt now asks you to link a
GitHub account first):

- **Name:** anything, for example `TV Tracker`.
- **Redirect URI:** `https://example.com/oauth/callback` (or any HTTPS URL you
  control). TV Tracker uses Trakt's device-code flow, so this URI is never
  called. Don't use the old `urn:ietf:wg:oauth:2.0:oob` value; current Trakt
  guidance advises against out-of-band and `localhost` redirects.
- Copy the **Client ID**. A client secret is not needed for the device flow.

Then use **Settings → Trakt API app → Change** to enter it.

## Build

Requirements: JDK 17+ and an Android SDK with platform 35.

### Trakt Client ID at build time

The app can take its free Trakt **Client ID** at build time, so it never needs
to be committed:

```sh
TRAKT_CLIENT_ID=your_client_id ./gradlew :app:assembleDebug
# or
./gradlew :app:assembleDebug -PtraktClientId=your_client_id
```

You can also add it to `local.properties` (already gitignored):

```properties
trakt.clientId=your_client_id
```

Precedence is `-PtraktClientId` → `TRAKT_CLIENT_ID` env var →
`local.properties`. If none is supplied, the app asks for the Client ID on
first launch. The value is compiled into `BuildConfig`; it is a public app
identifier, not a secret.

Then build or install:

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
