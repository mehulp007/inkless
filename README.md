# Inkless

**The ledger that writes itself.**

Inkless is an Android expense tracker built around how students in India actually pay: lots of small UPI payments — chai, canteen, autos, Swiggy, recharges — spread across GPay, PhonePe, Paytm and bank apps. Inkless reads the SMS your bank already sends for every UPI payment and adds the payment for you, so the ledger fills itself with zero typing. Every payment is filed into a category tab, so you can see at a glance where the month's money went.

It's local-first: your ledger lives in a database on your phone, and the SMS text never leaves it. The internet is only used to sign in.

<p align="center">
  <img src="docs/screenshots/home-all.png" width="260" alt="Month view with every payment grouped by day" />
  <img src="docs/screenshots/home-category.png" width="260" alt="Food &amp; Canteen tab with its monthly total" />
  <img src="docs/screenshots/home-dark.png" width="260" alt="Food Delivery tab in dark mode" />
</p>

<sub>Screenshots use the built-in demo data — none of it is real.</sub>

## What it does

- **Auto-capture from bank SMS** — payments show up within seconds of paying, from any UPI app. See [below](#auto-capture).
- **One payment, one entry** — the bank SMS and the GPay/PhonePe/Paytm notification for the same payment are merged. See [UPI app notifications](#upi-app-notifications).
- **Transfers aren't spending** — money moved between your own accounts shows as 🔁 and stays out of the totals; money from home is tagged as allowance.
- **Files itself** — known merchants go straight into the right category, and anything new is a one-tap question. See [Smart categories](#smart-categories).
- **Category tabs** — swipe between *All* and 13 categories (Food & Canteen, Food Delivery, Travel, Recharge & Subscriptions, Laundry & Hostel, Lent to Friends, …), each with its own monthly total and payment list.
- **Month at a glance** — the total spent this month up top; step back through previous months.
- **Day-by-day list** — payments grouped by day with a daily total, merchant name, time and UPI ID.
- **Refunds handled properly** — a refund is subtracted from the category it came back to, and money coming in (like the monthly allowance) never counts as spending.
- **Transaction details** — tap any payment to set its category, add a note, rename the merchant ("q8291…@ybl" → "Tapri near Block C"), or delete it.
- **Cash quick-add** — the *Cash* button on the home screen, for spends no SMS will ever mention.
- **Demo mode** — *Try the demo* on the welcome screen opens a few months of made-up hostel spending, no account needed. The data is seeded, so it comes out the same every time.
- **Material 3** with light/dark theme and dynamic colour on Android 12+.

All amounts are stored as whole paise (`Long`), never floating point, and shown with Indian digit grouping (₹1,23,456.50).

## Auto-capture

<p align="center">
  <img src="docs/screenshots/sms-setup.png" width="260" alt="Auto-capture setup explaining what is and isn't read" />
  <img src="docs/screenshots/transaction.png" width="260" alt="A captured payment waiting for a category" />
</p>

UPI apps have no API, but every bank sends an SMS for every UPI payment, whichever app was used. Inkless reads those:

- **Live** — a `BroadcastReceiver` picks up each bank SMS as it arrives and turns it into a transaction.
- **History** — on setup it can import the last 1, 3 or 6 months from the SMS inbox. This runs in WorkManager with a progress bar, and the same SMS is never saved twice.
- **Catch-up** — after logging back in, it quietly picks up any bank SMS that arrived in the meantime.
- **Bank-specific parsers** for SBI, HDFC, ICICI and Axis, built from real (masked) alerts, plus a generic UPI parser for everything else: amount, debit/credit, payee, UPI ID, account (last 4 digits), UPI reference and balance.
- **Refunds and reversals** are recognised (including SBI's `UPI/DRC` and `UPI/ONRF` codes) so they offset spending instead of counting as income.
- **Couldn't read these** — bank messages that look financial but no parser understood are kept in a list in Settings, so formats can be fixed instead of silently lost.

What it refuses to read matters just as much. A filter runs before any parsing and drops:

- **OTPs**, always and from any sender — they are never stored, not even as a hash.
- **Promotions and loan offers**, including anything from a promotional (`-P`) sender.
- **Failed payments, collect requests, mandate set-ups, balance reminders and telecom recharges** — money didn't actually move.
- **Personal messages and non-bank senders** — only bank-style sender IDs (`VM-HDFCBK`, `JK-SBIUPI-S`, …) are considered.

Each account turns capture on separately, nothing is captured in demo mode or while signed out, and the parsers live in the pure-Kotlin `:parsing` module with table-driven tests: every sample message in [`parsing/src/test/resources/parser-fixtures/`](parsing/src/test/resources/parser-fixtures/) states what it should be read as.

## UPI app notifications

<p align="center">
  <img src="docs/screenshots/transfer.png" width="260" alt="A transfer to the user's own account shown with a transfer icon and left out of the month's spending" />
  <img src="docs/screenshots/capture-health.png" width="260" alt="Capture health screen with SMS and notification access on, and battery and notifications needing attention" />
</p>

With notification access switched on, Inkless also reads payment notifications from UPI apps (Google Pay, PhonePe, Paytm, BHIM, Amazon Pay, CRED, super.money, Navi). It ignores every other app's notifications without reading them, and drops cashback, offers, requests, reminders and failed or pending payments. Notifications bring:

- **Names people recognise** — "Tapri near Block C" instead of `q829154@ybl`.
- **UPI Lite payments**, which send no bank SMS at all.

The same payment now usually arrives twice, so a deduplicator decides when two captures are one payment: the same UPI reference settles it; otherwise the same amount and direction within three minutes, from different sources, with payees that don't contradict each other (it allows for banks cutting names short and masking UPI IDs). Two SMS for the same amount are never merged — that's two chais. When they merge, the bank SMS wins on the money (amount, reference, account, balance), the notification wins on the name, and a category you already chose is kept.

**My accounts** (Settings) lists your own UPI IDs and your name as banks print it, plus home's UPI ID:

- Money to or from your own accounts is a **transfer**: shown with 🔁, never counted as spending or income.
- Money from home is **income tagged as allowance**.
- Money back from a merchant you paid at least as much in the last 30 days is a **refund** in that payment's category.

Adding or removing an account re-checks the last six months, so old transfers stop counting as spending straight away.

**Capture health** (Settings) shows whether SMS access, notification access, Inkless's own notifications and battery optimisation are set up, with a button straight to the system setting that fixes each one — including the *Allow restricted settings* step Android 13+ needs for apps installed from a file.

## Smart categories

<p align="center">
  <img src="docs/screenshots/category-question.png" width="520" alt="Notification asking what a ₹30 payment was, with category buttons" />
</p>
<p align="center">
  <img src="docs/screenshots/review.png" width="260" alt="To-sort list with one-tap category chips" />
</p>

Every captured payment goes through a small categoriser, most specific knowledge first:

1. **Your rules** — Settings → Categorising rules, e.g. *UPI ID contains `canteen` → Food & Canteen*. They match the UPI ID, the payee name or either, by *contains*, *is exactly* or a regex, and run in the order you put them.
2. **Merchant memory** — once you've said what a merchant is, every later payment to it is filed the same way. This is what handles opaque QR handles like `q829154@ybl` and SMS that only carry a name ("trf to RAMU BHAIYA"), which no keyword can guess.
3. **Built-in keywords** — Swiggy/Zomato → Food Delivery, Blinkit/Zepto/Instamart → Groceries, Uber/Rapido/IRCTC → Travel, Jio/Airtel/Netflix → Recharge & Subscriptions, and so on. Short keywords must be a whole word, so "ola" files *Ola* but not *Kolapur Stores*.
4. **Otherwise it asks.** A notification — *"₹30 to RAMU BHAIYA — what was this?"* — carries your three most-used categories as buttons; tapping one files the payment and teaches the merchant without opening the app. Anything still unsorted waits on the home screen under *N payments to sort*, where one tap per merchant clears it.

Teaching a merchant also files its other waiting payments, and payments captured before a rule or a merchant was known get re-sorted the next time the app opens. Anything you've categorised yourself is never changed.

## Accounts

<p align="center">
  <img src="docs/screenshots/welcome.png" width="260" alt="Welcome screen with create account, log in, Google and demo options" />
  <img src="docs/screenshots/signup.png" width="260" alt="Sign-up form checking the username as you type" />
</p>

- **Sign up** with a username, email and password. The username is checked as you type, and the account gets a verification email (the app works while you wait, with a reminder on the home screen).
- **Log in** with your username *or* your email, or with **Continue with Google** (Android's Credential Manager account picker).
- **Forgot password** takes a username or email and sends a reset link.
- **Settings** — link Google to a password account, change password, log out, or delete the account (frees the username and wipes its data from the phone).
- **Several people, one phone** — each account gets its own database file, so logging out and letting a friend log in never mixes your data.

Error messages are deliberately vague about whether an account exists: a wrong password and an unknown username get the same message, and the reset screen always says *"If an account exists, we've sent a reset link."*

## Privacy

- Your transactions are stored in a Room database on the device, one file per account (`inkless_<id>.db`). Demo data lives in its own file and never mixes with anything else.
- SMS access is asked for in plain language first, and can be revoked any time from Android settings. Only bank transaction alerts are kept; the original text stays on the phone so it can be re-read if a parser improves, and is never uploaded. Notification access works the same way: only UPI apps' payment notifications are read, and their text never leaves the phone either.
- The only network traffic is Firebase: Authentication for sign-in, and Cloud Firestore for your username and profile. No analytics or ads SDKs.
- Firestore security rules let each account read and write only its own data. A username can be looked up one at a time (that's how logging in with a username finds the email), but usernames can't be listed.
- Inkless opts out of Android's automatic cloud backup and device-to-device transfer, so your spending data isn't copied anywhere behind your back.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe routes) |
| Architecture | MVVM, unidirectional data flow with `StateFlow` |
| DI | Hilt |
| Storage | Room (schema exported to `app/schemas/`, migrations tested against every past schema) |
| Accounts | Firebase Authentication, Cloud Firestore, Credential Manager (Sign in with Google) |
| Capture | `BroadcastReceiver` (SMS_RECEIVED), `NotificationListenerService`, SMS content provider, WorkManager |
| Notifications | Notification actions handled by a `BroadcastReceiver`, deep link into the payment |
| Async | Kotlin Coroutines & Flow |
| Tests | JUnit, Truth, Turbine; Room tests on a device/emulator; Firestore rules tests on the Firebase emulator |
| Build | Gradle 9 (Kotlin DSL + version catalog), AGP 9, KSP |

minSdk 26 · targetSdk 37

## Project layout

```
app/        Android app — UI, Room database, accounts, SMS capture, Hilt wiring, demo data
parsing/    Pure Kotlin module (no Android dependencies): SMS filter, bank and UPI-app parsers, test fixtures
firebase/   Firestore security rules, their tests, and Firebase setup steps
docs/       Screenshots
```

Inside `app/src/main/java/com/mehul/inkless/`:

```
auth/         Firebase sign-in, usernames, Google ID tokens, input validation
session/      Who's using the app (signed out, demo, an account) and which database is open
capture/      SMS receiver, notification listener, inbox import, the filter → parse → dedupe → save pipeline, transfer/refund classification
categorize/   Rules → merchant memory → keywords, re-sorting, and the "what was this?" notification
data/db/      Room entities, DAOs, DatabaseProvider (one database file per account)
data/repo/    Repositories the UI reads from
data/seed/    Default categories
demo/         Demo data generator and seeder
ui/           Compose screens, ViewModels, theme, formatting
```

## Building

Requirements: JDK 17 or newer (21 recommended) and the Android SDK with platform 37.

The app needs an `app/google-services.json` from a Firebase project. It isn't committed; [`firebase/README.md`](firebase/README.md) walks through creating a free project and getting one.

```bash
# Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew test

# Room, migration, capture, dedupe and categorising tests (needs a running emulator or connected phone)
./gradlew connectedDebugAndroidTest

# Firestore rules tests (needs Node and Java 21+)
cd firebase && npm install && npm test
```

On Windows use `gradlew.bat`. You can also just open the folder in Android Studio and hit Run.

**Installing from an APK file:** Android treats apps that read SMS carefully when they don't come from an app store. Play Protect may refuse an APK opened from a browser or chat app (installing over USB with `adb install` works), and on Android 13+ the SMS permission starts out locked: open the app's info page, tap ⋮ → *Allow restricted settings*, then allow SMS. The in-app setup screen walks through this too.
