# AdBlock Quick Settings Tile

This project adds a Quick Settings tile that toggles AdGuard Private DNS.

## One-time development permission

Android protects WRITE_SECURE_SETTINGS. On a personal development device, connect through ADB and run:

```bash
adb shell pm grant com.example.adblock android.permission.WRITE_SECURE_SETTINGS
```

Then install the APK, add the AdBlock tile through Quick Settings edit mode, and tap it.

This project controls Private DNS; it is not a custom VPN implementation.
