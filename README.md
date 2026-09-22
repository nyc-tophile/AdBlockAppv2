# AdBlock Android App

Minimal Android app that toggles Android Private DNS between an ad-blocking hostname and `off`.

## Important permission setup

Android protects `WRITE_SECURE_SETTINGS`. After installing the APK on a test device, grant it with:

```bash
adb shell pm grant com.example.adblock android.permission.WRITE_SECURE_SETTINGS
```

The app cannot grant this permission to itself. ADB or a compatible Shizuku setup is required.

## Build

Open this folder in Android Studio and run:

```bash
./gradlew assembleDebug
```

The generated debug APK will be under `app/build/outputs/apk/debug/`.

## Notes

- The selected DNS hostname is `dns.adguard-dns.com`.
- OFF currently sets Private DNS mode to `off`; production should store and restore the user's previous Private DNS configuration instead.
- Test on multiple Android manufacturers before distributing the APK.
