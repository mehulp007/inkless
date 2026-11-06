# Inkless

**The ledger that writes itself.**

Inkless is an Android expense tracker built around how students in India actually pay: lots of small UPI payments — chai, canteen, autos, Swiggy, recharges — spread across GPay, PhonePe, Paytm and bank apps. Every payment is filed into a category tab, so you can see at a glance where the month's money went.

It's local-first: your data lives in a database on your phone, and the app doesn't even request the internet permission.

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
- **Demo mode** — the app ships with a generator that makes up a few months of realistic hostel spending, so you can try it (and take screenshots) without exposing real finances. The data is seeded, so it comes out the same every time.
- **Material 3** with light/dark theme and dynamic colour on Android 12+.

All amounts are stored as whole paise (`Long`), never floating point, and shown with Indian digit grouping (₹1,23,456.50).

## Privacy

- Everything is stored in a Room database on the device.
- Databases are opened per profile (`inkless_<name>.db`), so demo data lives in its own file and never mixes with anything else.
- Inkless opts out of Android's automatic cloud backup and device-to-device transfer, so your spending data isn't copied anywhere behind your back.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe routes) |
| Architecture | MVVM, unidirectional data flow with `StateFlow` |
| DI | Hilt |
| Storage | Room (schema exported to `app/schemas/`) |
| Async | Kotlin Coroutines & Flow |
| Tests | JUnit, Truth, Turbine; Room tests run on a device/emulator |
| Build | Gradle 9 (Kotlin DSL + version catalog), AGP 9, KSP |

minSdk 26 · targetSdk 37

## Project layout

```
app/        Android app — UI, Room database, Hilt wiring, demo data
parsing/    Pure Kotlin module (no Android dependencies) for reading amounts out of bank messages
docs/       Screenshots
```

Inside `app/src/main/java/com/mehul/inkless/`:

```
data/db/      Room entities, DAOs, DatabaseProvider (one database file per account)
data/repo/    Repositories the UI reads from
data/seed/    Default categories
demo/         Demo data generator and seeder
ui/           Compose screens, ViewModels, theme, formatting
```

## Building

Requirements: JDK 17 or newer (21 recommended) and the Android SDK with platform 37.

```bash
# Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew test

# Room tests (needs a running emulator or connected phone)
./gradlew connectedDebugAndroidTest
```

On Windows use `gradlew.bat`. You can also just open the folder in Android Studio and hit Run.
