# Installing Inkless

Inkless isn't on the Play Store (apps that read SMS need Google's special approval), so it's installed
from an APK file. It takes about five minutes.

## 1. Pick the right file

| File | For |
|---|---|
| `inkless-1.1.0-arm64.apk` | Almost every phone from the last six or so years. Start with this. |
| `inkless-1.1.0-armeabi-v7a.apk` | Older or very cheap phones, if the first one says *App not installed*. |
| `inkless-1.1.0-universal.apk` | Works everywhere, but three times the size. |

## 2. Install it

**From a computer (most reliable).** Turn on *Developer options → USB debugging* on the phone, plug it
in, then:

```bash
adb install inkless-1.1.0-arm64.apk
```

**From the phone.** Open the APK from Files, Drive or wherever it was sent, and allow *Install unknown
apps* for that app when Android asks.

In India, Google Play Protect may stop this with *"Blocked to protect your device"*: it blocks apps
that ask for SMS access when they're installed from a browser, a chat app or a file manager. If that
happens, install from a computer as above. (Switching Play Protect's scanning off for the install
also works; if you do, switch it back on straight after.)

## 3. Let it read bank SMS

Open Inkless, create an account, and the *Get started* list walks through the rest. On Android 13 and
newer, an app installed from a file starts with SMS and notification access locked:

1. Open **Settings → Apps → Inkless** (or long-press the Inkless icon → *App info*).
2. Tap **⋮** in the top corner → **Allow restricted settings**, and confirm.
3. Back in Inkless, allow SMS, and switch on notification access if you'd like friendlier names and
   UPI Lite payments.

## 4. Keep it running

Some phones (Xiaomi, Redmi, POCO, Realme, Vivo, Oppo, OnePlus) close apps in the background to save
battery, and then payments stop appearing. In Inkless, **Settings → Capture health** shows what's off.
On those phones also turn on **Autostart** for Inkless in the phone's own settings app, and set its
battery use to **No restrictions**.

## Updates

Install the new APK over the old one, the same way. Everything on the phone stays.

## Privacy, in short

- Only bank payment SMS and UPI app payment notifications are read. OTPs, offers and personal
  messages are dropped unread, and the text of a message never leaves the phone.
- The cloud backup (Firebase, on by default, can be switched off in Settings) holds the payment
  details only: amount, payee, category, note. Google encrypts it and other users can't read it, but
  whoever runs the Firebase project could.
- *Settings → Privacy & your data* has an app lock, exports, and a password-locked backup file.
