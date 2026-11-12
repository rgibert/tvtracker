# TV Tracker

A small, free Trakt client for keeping up with **TV shows only** (no movies).
It is meant to be a lighter alternative to bigger all-in-one Trakt apps.

## What it does

- **To Watch** – combines Trakt's Up Next feed with shows on your watchlist, so
  every show has one clear next episode.
- **Coming Up** – the next 14 days as a list for the shows you watch and the
  shows on your watchlist, grouped by local day. Switch to a month calendar
  that marks airing days, scroll to the next month to load its episodes, and
  tap a day to see what airs then.
- **Add shows** – tap **+** on To Watch to search Trakt by title. Adding a show
  puts it on your Trakt watchlist, so it joins To Watch and Coming Up; shows
  already in your queue are marked, and tapping a result opens its details.
- **Show details** – full season and episode list with watched checkboxes, a
  "mark watched" shortcut for the next episode, and per-show progress.
- **Pinned channels** – Trakt lists a show's original network (BBC, say), which
  isn't always where it airs for you (HBO in North America). Tap the channel on
  a show's page to pin your own; it replaces Trakt's everywhere in the app and
  can be reset there or under **Settings → Pinned channels**.
- **Open in Plex** – shows pinned to Plex get an **Open in Plex** button. With
  a server set under **Settings → Plex address** (for example
  `192.168.1.10:32400`) it searches that server for the show; otherwise it
  opens the Plex app if installed, or app.plex.tv.
- **Offline-friendly** – the last list and show details are cached on the device
  and shown (with a "saved data" banner) when Trakt can't be reached.
- **Light on the Trakt API** – watched progress is fetched in bulk (one
  paginated request, not one per show), and screens reuse recent snapshots
  instead of refreshing on every resume.
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

### Sentry DSN at build time

Crash and error reporting through [Sentry](https://sentry.io) is enabled only
when a DSN is supplied at build time:

```sh
SENTRY_DSN=https://key@o0.ingest.sentry.io/0 ./gradlew :app:assembleDebug
# or
./gradlew :app:assembleDebug -PsentryDsn=https://key@o0.ingest.sentry.io/0
```

You can also add `sentry.dsn=...` to `local.properties`. Precedence is
`-PsentryDsn` → `SENTRY_DSN` env var → `local.properties`. Without a DSN the
SDK is never initialised. Events are tagged with the build type as the
environment (`debug` / `release`) and `ca.gibert.tvtracker@<versionName>+<versionCode>`
as the release; default PII (IP address, user info) is not sent.

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

### GitHub Actions

`.github/workflows/build.yml` runs when a version tag is pushed (`v*`) and for
pull requests against `main`. Tag builds publish the debug and release APKs on
a GitHub **Release**. Pull request builds upload the debug APK as the
`tv-tracker-debug` workflow artifact and leave a comment on the PR linking to
it, so the test APK is easy to find without creating a tag or pre-release.
Fork pull requests only upload the artifact, because their token cannot write
comments.

All builds run the unit tests and lint and upload the APKs to the workflow run's
**Artifacts**.

The tag drives the app version: `v0.1.0` becomes `versionName` `0.1.0`, and
`versionCode` is derived as `major × 1,000,000 + minor × 1,000 + patch`
(`v0.1.0` → `1000`). Local builds default to `0.1.0`; override with
`-PappVersionName=1.2.3` and `-PappVersionCode=1002003` if needed.

Add a repository secret named `TRAKT_CLIENT_ID` so CI builds skip the Client ID
setup screen. Without it, the APK still works but asks for a Client ID.

Add a repository secret named `SENTRY_DSN` to enable Sentry in CI builds.
Without it, the APK is built with Sentry disabled.

The debug APK is installable as-is, signed with the Android debug key. To sign
the release APK, create a keystore once:

```sh
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias tvtracker \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=TV Tracker" \
  -storepass 'your-password' \
  -keypass 'your-password'
```

Keep `release.keystore` backed up and out of Git (it is ignored). Then add these
repository secrets under **Settings → Secrets and variables → Actions**:

- `TRAKT_KEYSTORE_BASE64` – base64-encoded keystore
- `TRAKT_KEYSTORE_PASSWORD` – the `-storepass` value
- `TRAKT_KEY_ALIAS` – `tvtracker` in the example above
- `TRAKT_KEY_PASSWORD` – the `-keypass` value

On macOS, copy the base64 keystore with:

```sh
base64 -i release.keystore | pbcopy
```

Then push a tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release assets are named `tvtracker-<version>-debug.apk` and
`tvtracker-<version>.apk` (`-unsigned.apk` when no signing secrets are set).
Re-running the workflow for the same tag replaces the existing assets.

Without signing secrets, the release artifact is unsigned
(`app-release-unsigned.apk`) and only useful as a build check. Losing the
keystore prevents updating an installed release build.

To build a signed release locally, point Gradle at the same keystore:

```sh
TRAKT_KEYSTORE_FILE="$PWD/release.keystore" \
TRAKT_KEYSTORE_PASSWORD='your-password' \
TRAKT_KEY_ALIAS=tvtracker \
TRAKT_KEY_PASSWORD='your-password' \
./gradlew :app:assembleRelease
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
