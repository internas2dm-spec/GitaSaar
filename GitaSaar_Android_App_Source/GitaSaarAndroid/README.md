# Gita Saar — श्रीमद्भगवद्गीता Android App

**Status:** Android Studio *source-code prototype*, not a tested APK / Play Store-approved release.

Native Android/Java app aimed at Play Store submission, with an online-only connection gate, Gita chapters and verses, Sanskrit text, Hindi meanings, daily quotes, favourites, search, share and a Google AdMob test banner.

## What's implemented

- Chapter listing of all 18 chapters (canonical 700 verse counts).
- Loads `base.json` and `hi.json` over HTTPS from [Adiyogi Arts' MIT-licensed Gita dataset](https://github.com/AdiyogiArts/gita-json), with CDN/GitHub fallback; checks roughly 700 verses before showing content.
- Saves the downloaded JSON locally for performance. **Internet is checked on every visit, even after caching**: without a validated internet connection, the reader doesn't open. Both Wi-Fi and mobile data work.
- Today's selected Gita verse, showing the original shloka and Hindi translation from the source.
- 18 chapter reader, verse detail, text search, saved shlokas, Android share sheet.
- Local 8 AM daily notification **opt-in**, approximate time (Android alarm may defer delivery). A curated set of 27 verses rotates, using the locally cached text after initial sync.
- AdMob test banner, and UMP consent hook. Test ad IDs do **not** earn money.
- Target SDK **36** (Android 16), minimum SDK 26 (Android 8.0).
- Github Actions workflow to produce an installable *debug* APK after uploading source to GitHub.

## Build instructions

1. Install the latest Android Studio with Android SDK Platform 36, Build-Tools 36.0.0, Gradle 8.13, and JDK 17.
2. Open the **GitaSaarAndroid** directory in Android Studio and sync the Gradle project.
3. This source ZIP deliberately does not contain the Gradle wrapper binary (`gradle-wrapper.jar`). If your IDE asks for a wrapper, use an installed Gradle **8.13** and generate one with `gradle wrapper --gradle-version 8.13` (from the project root), then reopen and sync.
4. Build > Build APK(s) for a debug APK, or run on a physical device / emulator with Google Play Services and internet.
5. Alternative: push the project files to GitHub, open the **Actions** tab, run **Build Android Test APK**, and download the resulting Actions artifact. No local Android setup needed for this route.

## Before monetization / publication

- [ ] Replace `applicationId 'com.gitasaar.reader'` with your own unique package ID **before the first Play release**.
- [ ] Sign up for a [Google AdMob](https://admob.google.com/) account, create an Android app and a Banner Ad Unit. Replace:
  - `app/src/main/AndroidManifest.xml` demo **AdMob App ID** (`ca-app-pub-...~...`).
  - `MainActivity.java` demo **banner unit ID** (`ca-app-pub-.../...`).
- [ ] In AdMob Privacy & Messaging, create any applicable consent messages and test the Google UMP flow for relevant regions. Keep test ads during testing; never click your own live ads.
- [ ] Provide a working **public privacy policy URL** and accurate Data Safety disclosures. A draft is in `playstore/PRIVACY_POLICY_DRAFT.md`, which must be edited with real owner/contact details and hosted online.
- [ ] Review the Gita source's correctness/attribution and licensing for your commercial use. Keep the MIT license attribution.
- [ ] Add real production screenshots, feature graphic, content rating, support email and Play listing.
- [ ] Review platform requirements for newly created personal Play Console accounts (potential 12 testers for 14 days) and account verification.
- [ ] QA on actual Android phones before publishing. The project has **not** been compiled or device-tested in this environment.
- [ ] Build a **signed Android App Bundle (.aab)** from Android Studio / CI for Google Play submission. The GitHub workflow only generates a *debug APK*, not a signed Play bundle.
- [ ] Decide an ad-frequency policy. This prototype uses a single bottom banner, not full-screen ads interrupting sacred-text reading.

## Online-only behavior

The downloaded verse data is cached in `filesDir`, but access to reading is gated on `ConnectivityManager.NET_CAPABILITY_VALIDATED`. Offline users see a clear internet-required page. This verifies the device has a working network; it does **not** enforce cellular data only. The current app has **no account system and no backend authentication**, so it is not an anti-tampering/DRM security mechanism.

## Data & copyright

- Bhagavad Gita original Sanskrit verses: ancient scripture.
- Machine-readable dataset and provided Hindi translations: [AdiyogiArts/gita-json](https://github.com/AdiyogiArts/gita-json), MIT license. Respectful credit appears in the app's Settings and this README. Please verify individual translation rights prior to a commercial production release.
- The demo Google AdMob identifiers are publicly documented testing IDs.

## Limitations of this version

This is an MVP source project: no account login, no audio recitation, no subscription payments, no analytics dashboard, no live app review, no guarantee of AdMob approval or earnings, and no Play Store account integration. Daily notifications are device-local approximate scheduling and may not arrive at exactly 8 AM on all devices. Network and provider outages could temporarily prevent data loading.
