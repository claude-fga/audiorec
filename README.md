# audiorec

A minimal Android app: open it and it immediately records audio from the
microphone. The screen shows a single red button. Tap it to stop the
recording and close the app.

## How it works

- Recording starts as soon as the app opens (the microphone permission is
  requested on first launch).
- Recording runs in a foreground service, so it keeps going if the screen
  turns off. A notification with a **Stop** action is shown while recording.
- Files are saved as AAC `.m4a` in **Music/audiorec** on the phone, named
  `REC_YYYYMMDD_HHMMSS.m4a`. They show up in any file manager or music app.

## Download

Every push to `main` is built automatically by GitHub Actions.

1. Open the **Releases** page of this repository.
2. Download `audiorec.apk` from the newest release (or from the **Actions**
   tab, under the run's artifacts).
3. Open it on your phone and allow "Install unknown apps" when asked.

Requires Android 10 (API 29) or newer.

## Build locally

Requires JDK 17, the Android SDK and Gradle 8.7:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Notes

The APK is signed with the standard debug key, which is fine for personal
use. For Play Store distribution you would add your own release signing.
