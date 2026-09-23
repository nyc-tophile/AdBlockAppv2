# AdBlock DNS VPN

AdBlock is a DNS-only Android VPN for Android 9 (API 28) and newer. It does **not** change Android Private DNS and does not require ADB or `WRITE_SECURE_SETTINGS`.

## How it works

After the user accepts Android's official VPN permission dialog, the app creates a TUN interface with routes only for two synthetic DNS server addresses (one IPv4 and one IPv6). Android sends system DNS lookups to that local interface. The app:

1. checks the requested domain against its small bundled block list;
2. returns DNS `NXDOMAIN` for a blocked domain; or
3. forwards an allowed query to Cloudflare `1.1.1.1` over protected UDP, then returns its answer to the app.

Ordinary TCP/UDP app traffic is not routed through this VPN. IPv4 and IPv6 DNS packets from Android's resolver are handled. The upstream DNS transport is **unencrypted UDP**; this project intentionally does not claim DNS-over-HTTPS or DNS-over-TLS support.

### Important limitations

- A local DNS block list blocks matching domains in browsers and other apps that use Android's configured resolver.
- Apps that hard-code a DNS server, use DNS-over-HTTPS/DoT, use a VPN, or use another encrypted resolver can bypass this filter.
- DNS filtering cannot remove ads served from the same domain as content. HTTPS, certificate pinning, and QUIC traffic are not decrypted or inspected.
- The local packet handler implements UDP DNS. Rare lookups that require DNS-over-TCP fallback (for example, a truncated UDP answer) are not supported and can fail instead of falling back.
- The bundled list is intentionally small. A production app should add a reviewed, regularly updated list and a DNS transport with appropriate privacy guarantees.

## Build locally

The repository currently uses a system Gradle installation (there is no Gradle wrapper):

```powershell
gradle assembleDebug --stacktrace
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

Push to `main` or run the **Build Android APK** workflow manually. It uses JDK 17, installs Android API 35/build-tools 35.0.0, compiles with detailed Gradle errors, and uploads `adblock-debug-apk`.

## Install and test on a phone

1. Install the debug APK and open AdBlock.
2. Tap **Enable AdBlock** and accept Android's VPN confirmation dialog.
3. Confirm the persistent AdBlock notification and the app status say ON.
4. Add the **AdBlock** tile from Quick Settings' edit screen. When permission has not been granted, tapping it opens the app because a tile cannot reliably show the VPN consent dialog itself.
5. Browse to a site or app that resolves a domain in the bundled list, then disable AdBlock and compare DNS behavior.

## Troubleshooting

- **VPN permission required:** open the app and tap Enable; Android owns this confirmation dialog.
- **VPN service unavailable / Error starting VPN:** check that another always-on or lockdown VPN is not preventing a new VPN, then disable and re-enable AdBlock.
- **Some ads remain:** they may be first-party, cached, or served through an app-specific/encrypted resolver; those are outside a DNS-only filter's scope.
- **No network after enabling:** disable AdBlock from the notification, app, or Quick Settings and report the device model/Android version. The service only routes its synthetic DNS addresses, so normal traffic should continue on the underlying network.

## Migration from the Private DNS version

- Deleted `PrivateDnsController.kt`; it used secure global settings and is no longer needed.
- Replaced `MainActivity.kt`, `AdBlockTileService.kt`, `AndroidManifest.xml`, `strings.xml`, and this README.
- Added `AdBlockVpnService.kt`, `DnsPacket.kt`, `DnsFilter.kt`, and `AdBlockStatus.kt`.
- No third-party runtime dependency or ProGuard/R8 exception is needed for this implementation.

To commit and push your work:

```powershell
git add .
git commit -m "Replace Private DNS toggle with DNS VPN"
git push origin main
```
