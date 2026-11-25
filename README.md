# SK Browser TV

A remote-friendly web browser for **Android TV / Google TV** with a built-in ad blocker, a DNS-shield VPN, YouTube support, a download manager, home-screen shortcuts and a full settings screen.

Made by **Sk Kaushal**.

## Features

| Area | What you get |
|------|--------------|
| Home screen | Search box, shortcut tiles, an **Add site** tile, and quick buttons for VPN, Downloads and Settings. Hold OK on a tile to edit or delete it. |
| Browser | Tabs-free fast WebView browser with Back / Forward / Reload / Home, URL bar, progress bar, fullscreen video. |
| TV remote support | **Cursor mode**: move a pointer with the D-pad, press OK to click, push up/down at the edge to scroll. Press the remote **Menu** key (or the Menu button) for browser options. |
| Ad blocker | Blocks requests to known ad / tracker domains, hides leftover ad boxes, per-site "Allow ads" exception. |
| VPN (DNS shield) | Local VPN that blocks ad and tracker domains for **every app** on the TV. Pick your DNS server (Cloudflare, Google, Quad9, AdGuard). |
| YouTube | YouTube tile on the home screen. Opens the YouTube app if installed, otherwise the YouTube website in this browser. |
| Downloads | Real file downloads through Android's download manager, confirmation prompt, progress screen, open or delete files. |
| Shortcuts | **Settings > Add current page to shortcuts**, **Settings > Add a site**, and **Add site** on the home screen. |
| Settings | Home page, search engine, desktop site, JavaScript, cursor mode, ad blocker, VPN, DNS, YouTube, download prompt, clear browsing data, About and license. |

## Important notes (please read)

* **The VPN is a DNS shield, not a privacy VPN.** It filters ad and tracker domains on the device. It does **not** hide your IP address, change your location or unlock region-locked content.
* **YouTube video ads** are served from the same servers as the videos, so no DNS or domain blocker can reliably remove them. Hiding in-video ads needs a patched YouTube client, which this project does not include.
* Sites using DRM (Netflix, Prime Video etc.) usually do not play in a WebView. Use their official apps.
* `blob:` and `data:` downloads are not supported.
* The built-in blocklist is small. Run `python3 tools/update_blocklist.py` on your PC to merge a large public host list, then rebuild.

## Build the APK

### Option 1: GitHub Actions (no Android Studio needed)

1. Create a new repository on GitHub and push this folder to it.
2. Open the **Actions** tab, run **Build APK** (it also runs on every push to `main`).
3. Download the **SKBrowserTV-debug-apk** artifact and unzip it.

### Option 2: Android Studio

1. Open this folder in Android Studio (Koala or newer, JDK 17).
2. Let Gradle sync, then choose **Build > Build APK(s)**.

## Install on your TV

1. On the TV enable **Developer options > USB / network debugging**.
2. From a PC on the same network:
   ```
   adb connect <TV-IP-ADDRESS>
   adb install app-debug.apk
   ```
   Or copy the APK to a USB drive and open it with a file manager app on the TV.
3. Open **SK Browser** from the TV launcher.

## Remote control cheat sheet

| Key | Action |
|-----|--------|
| D-pad | Move the cursor (hold to speed up). At the page edge it scrolls. |
| OK / Enter | Click at the cursor |
| D-pad Up at the very top | Move focus to the toolbar |
| D-pad Down in the toolbar | Return to the page |
| Menu | Browser options (add to shortcuts, cursor mode, desktop site, allow ads, downloads, settings) |
| Back | Go back, or leave the browser |

## Project structure

```
app/src/main/java/com/skkaushal/tvbrowser/
  MainActivity.kt        Home screen, shortcut tiles, Add site
  BrowserActivity.kt     WebView, ad blocking, cursor, downloads, menu
  AdBlocker.kt           Domain blocklist + cosmetic CSS injector
  AdBlockVpnService.kt   Local DNS-shield VPN (VpnService)
  SettingsActivity.kt    Settings screen
  DownloadsActivity.kt   Download list
  ShortcutStore.kt       Saved shortcuts
  Nav.kt, Prefs.kt, Dialogs.kt, CursorView.kt
app/src/main/assets/     blocklist.txt, cosmetic.css, LICENSE.txt
tools/update_blocklist.py
.github/workflows/build.yml
```

## Author

**Sk Kaushal**
Phone: +91 97793 71866
Email: skgdrive932@gmail.com

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Sk Kaushal.
