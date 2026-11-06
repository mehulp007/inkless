# Inkless

**The ledger that writes itself.**

Inkless is an Android expense tracker built around how students in India actually pay: lots of small UPI payments — chai, canteen, autos, Swiggy, recharges — spread across GPay, PhonePe, Paytm and bank apps. Every payment is filed into a category tab, so you can see at a glance where the month's money went.

It's local-first: your ledger lives in a database on your phone. The internet is only used to sign in.

<p align="center">
  <img src="docs/screenshots/home-all.png" width="260" alt="Month view with every payment grouped by day" />
  <img src="docs/screenshots/home-category.png" width="260" alt="Food &amp; Canteen tab with its monthly total" />
  <img src="docs/screenshots/home-dark.png" width="260" alt="Food Delivery tab in dark mode" />
</p>

<sub>Screenshots use the built-in demo data — none of it is real.</sub>

## What it does

- **Category tabs** — swipe between *All* and 13 categories (Food & Canteen, Food Delivery, Travel, Recharge & Subscriptions, Laundry & Hostel, Lent to Friends, …), each with its own monthly total and payment list.
- **Month at a glance** — the total spent this month up top; step back through previous months.
- **Day-by-day list** — payments grouped by day with a daily total, merchant name, time and UPI ID.
- **Refunds handled properly** — a refund is subtracted from the category it came back to, and money coming in (like the monthly allowance) never counts as spending.
- **Demo mode** — *Try the demo* on the welcome screen opens a few months of made-up hostel spending, no account needed. The data is seeded, so it comes out the same every time.
- **Material 3** with light/dark theme and dynamic colour on Android 12+.

All amounts are stored as whole paise (`Long`), never floating point, and shown with Indian digit grouping (₹1,23,456.50).

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
| Storage | Room (schema exported to `app/schemas/`) |
| Accounts | Firebase Authentication, Cloud Firestore, Credential Manager (Sign in with Google) |
| Async | Kotlin Coroutines & Flow |
| Tests | JUnit, Truth, Turbine; Room tests on a device/emulator; Firestore rules tests on the Firebase emulator |
| Build | Gradle 9 (Kotlin DSL + version catalog), AGP 9, KSP |

minSdk 26 · targetSdk 37

## Project layout

```
app/        Android app — UI, Room database, accounts, Hilt wiring, demo data
parsing/    Pure Kotlin module (no Android dependencies) for reading amounts out of bank messages
firebase/   Firestore security rules, their tests, and Firebase setup steps
docs/       Screenshots
```

Inside `app/src/main/java/com/mehul/inkless/`:

```
auth/         Firebase sign-in, usernames, Google ID tokens, input validation
session/      Who's using the app (signed out, demo, an account) and which database is open
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

# Room tests (needs a running emulator or connected phone)
./gradlew connectedDebugAndroidTest

# Firestore rules tests (needs Node and Java 21+)
cd firebase && npm install && npm test
```

On Windows use `gradlew.bat`. You can also just open the folder in Android Studio and hit Run.
