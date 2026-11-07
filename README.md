# Kay's APK Builder Free

Free native Android app for creating simple editable Android starter projects.

## Download the app on Android

**[Tap to download Kay's APK Builder Free APK](https://github.com/jettblue1488/-Kays-APK-Builder-Free./releases/download/android-apk/Kays-APK-Builder-Free.apk)**

The APK link stays the same after future successful builds on the main branch. If it does not download, [check the build status](https://github.com/jettblue1488/-Kays-APK-Builder-Free./actions/workflows/build-apk.yml) or [browse Releases](https://github.com/jettblue1488/-Kays-APK-Builder-Free./releases).

### Install and open

1. On Android, tap the download link above.
2. Open the downloaded `Kays-APK-Builder-Free.apk` in your **Downloads** app.
3. If prompted, allow your browser or Files app to install apps, then tap **Install** and **Open**.
4. Open **Kay's APK Builder** from your app drawer whenever you want.

This is a downloadable Android app, not a web page. Opening the GitHub repository alone cannot launch an app that has not been installed.

### What this version does

Enter an app name and description, then choose **Counter app** or **Notes app**. Tap **Generate Project ZIP** and choose where to save it. The ZIP contains a Gradle-based Java Android project, including a GitHub Actions workflow that you can upload to another GitHub repository to compile an APK. The app does **not** compile arbitrary app ideas directly on your phone, and the description is saved to the generated README rather than translated into features.

**Updating warning:** These GitHub Actions APKs are debug-signed; the signing key can differ between builds. Android can reject a newer APK as conflicting with an already installed version. Avoid uninstalling an app if you need its saved data.

## Build locally

Requires Java 17, Android SDK 35 and Gradle 8.9.

```sh
gradle --no-daemon :app:assembleDebug
```

The `Build Native Android APK` workflow builds and uploads the debug APK, then publishes the latest successful APK to the `android-apk` GitHub Release for one-tap download.
