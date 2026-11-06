# Inkless

**The ledger that writes itself.**

Inkless is an Android expense tracker built around how students in India actually pay: lots of small UPI payments — chai, canteen, autos, Swiggy, recharges — spread across GPay, PhonePe, Paytm and bank apps. Inkless reads the SMS your bank already sends for every UPI payment and adds the payment for you, so the ledger fills itself with zero typing. Every payment is filed into a category tab, so you can see at a glance where the month's money went.

It's local-first: your ledger lives in a database on your phone, and the SMS text never leaves it. The internet is only used for Firebase: signing in, and the cloud backup you can switch off.

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
- **Safe to spend today** — set your monthly allowance and the day it arrives; Home says what you can spend today and when the money runs out at this pace. See [Budget](#budget).
- **Break down a payment** — one ₹5,000 Amazon order of five things becomes five items in five categories, typed in or read from a screenshot of the order. See [Break down](#break-down).
- **Cloud backup** — everything is backed up to your account as you go; log in on a new phone and it all comes back. See [Cloud backup](#cloud-backup).
- **Category tabs** — swipe between *All* and 13 categories (Food & Canteen, Food Delivery, Travel, Recharge & Subscriptions, Laundry & Hostel, Lent to Friends, …), each with its own monthly total and payment list.
- **Month at a glance** — the total spent this month up top; step back through previous months.
- **Day-by-day list** — payments grouped by day with a daily total, merchant name, time and UPI ID.
- **Refunds handled properly** — a refund is subtracted from the category it came back to, and money coming in (like the monthly allowance) never counts as spending.
- **Transaction details** — tap any payment to set its category, add a note, rename the merchant ("q8291…@ybl" → "Tapri near Block C"), or delete it.
- **Cash quick-add** — the *Cash* button in the app or on the home-screen widget, for spends no SMS will ever mention.
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

## Budget

<p align="center">
  <img src="docs/screenshots/budget-home.png" width="260" alt="Home with ₹162 safe to spend today, a 12% spending ring and a pace chart" />
  <img src="docs/screenshots/category-detail.png" width="260" alt="Food &amp; Canteen detail with a budget bar, six months of spending and top places" />
  <img src="docs/screenshots/widget.png" width="260" alt="Home-screen widget showing ₹139.79 safe to spend today and a Cash button" />
</p>

Set the monthly allowance and the day it usually arrives (Settings → *Allowance and budget*, or the card on Home). Each budget month runs from that day to the day before the next allowance, and everything below is about the current one:

- **Safe to spend today** — what was left this morning, spread evenly over the days until the allowance, minus what today has already taken. It turns into *over today's share* when today goes past it.
- **Spending ring and pace chart** — how much of the allowance is gone, and spending so far against an even pace that ends exactly at the budget.
- **Runway** — *"At this pace you'll run out on the 24th."* The pace is a weighted average of daily spending that leans on the last few days, so a spree shows up straight away.
- **Daily food cap** (optional) — canteen and food delivery against a daily limit, and how many days this month went over it (meals skipped at the mess).
- **Category budgets** — tap a category's total on Home for its page: this month against its budget, the last six months as bars, and the places the money went.
- **Semester total** — spending since July (or January), on the budget page.
- **Home-screen widget** — safe to spend today and a *Cash* button that opens the quick-add sheet. It updates as payments come in.
- **Weekly summary** — Sunday at 7 pm: the week's spending, the top three categories, and what's left until the allowance. It can be switched off on the budget page.

Every spend number (category totals, budgets, safe to spend, the widget, the summary) comes from one database view, `spend_parts`, which already splits broken-down payments into their items and counts refunds against their category. Nothing adds up payments on its own, so the numbers always agree.

## Break down

<p align="center">
  <img src="docs/screenshots/share-order.png" width="260" alt="A shared order screenshot read into four items, with the matching ₹450 payment picked" />
  <img src="docs/screenshots/breakdown-editor.png" width="260" alt="Break down editor filled in from the shared order" />
  <img src="docs/screenshots/breakdown-home.png" width="260" alt="Shopping tab showing an Amazon payment with a +2 more badge" />
</p>

The bank and the UPI app only ever say "₹5,000 to Amazon", never what was in the order. *Break down* splits one payment into items, each with its own amount, category and note:

- **Editor** — type an amount, pick a category, next. The last item always takes whatever is left, so the items add up to the payment exactly, to the paisa.
- **Totals use the items** — each category counts only its own part, so the ₹5,000 order adds ₹3,000 to Shopping, ₹1,500 to College and ₹500 to Health. Merchant totals still see one ₹5,000 payment.
- **Listed once** — on Home the payment shows under its biggest item, with a *+2 more* badge; the other categories count their part without listing it again. *Back to one category* undoes it.
- **Asked at the right time** — after a payment over ₹100 (or ₹250, ₹500, ₹1,000, or never: it's a setting), the one notification that payment gets offers *Break down*. A payment Inkless filed on its own gets a quiet *"₹450 → 🍜 Food Delivery"* with *Looks right · Break down · Change*. Smaller payments stay quiet, and history imports never ask.
- **Share to Inkless** — share an order's screenshot (or its text) from Amazon, Flipkart, Blinkit or anywhere else. Inkless reads the items and prices on the phone, picks the recent payment with the same total, and opens the editor filled in, with a guessed category per item. Delivery, fees and discounts end up in the last item. Nothing is saved until you check it and tap *Save*.
- **Refunds** — money back for one item (same amount) goes back into that item's category; if it isn't clear which item it was, the refund waits in the review list.

Screenshots are read with [Tesseract](https://github.com/tesseract-ocr/tesseract) on the phone itself: no network, and the image and its text are never stored or uploaded. Its English model has no "₹", which it tends to read as "%", "=" or a "7" stuck to the price, so the order parser allows for that and uses the bill's total to tell "₹54" from "754". The parser lives in `parsing/` with fixture tests, including text Tesseract actually read off a test screenshot.

## Accounts

<p align="center">
  <img src="docs/screenshots/welcome.png" width="260" alt="Welcome screen with create account, log in, Google and demo options" />
  <img src="docs/screenshots/signup.png" width="260" alt="Sign-up form checking the username as you type" />
</p>

- **Sign up** with a username, email and password. The username is checked as you type, and the account gets a verification email (the app works while you wait, with a reminder on the home screen).
- **Log in** with your username *or* your email, or with **Continue with Google** (Android's Credential Manager account picker).
- **Forgot password** takes a username or email and sends a reset link.
- **Settings** — link Google to a password account, change password, log out, or delete the account (deletes its cloud backup, frees the username and wipes its data from the phone).
- **Several people, one phone** — each account gets its own database file, so logging out and letting a friend log in never mixes your data.

Error messages are deliberately vague about whether an account exists: a wrong password and an unknown username get the same message, and the reset screen always says *"If an account exists, we've sent a reset link."*

## Cloud backup

<p align="center">
  <img src="docs/screenshots/backup.png" width="260" alt="Settings showing cloud backup switched on and backed up just now" />
  <img src="docs/screenshots/logout-warning.png" width="260" alt="Log-out dialog warning that one change isn't backed up yet" />
</p>

The phone's own database is always the real copy; the app reads nothing else, so it works the same offline. The cloud copy in Firestore exists so a new phone can get everything back.

- **What's backed up:** payments (with their breakdown), categories (with their budgets), the allowance, merchants (with the names and categories you taught them), rules and *My accounts*. Never the text of an SMS or notification, and never more than the last 4 digits of an account number.
- **When:** a few seconds after any change, right after logging in, and every few hours as a safety net, using WorkManager so it only runs with a connection and retries when it fails. *Back up now* in Settings does it on the spot.
- **New phone:** log in and it downloads everything, with a *Restoring your backup…* card on the home screen while it does. If that phone's SMS inbox came back too, importing its history doesn't create duplicates of payments the backup already brought.
- **Two phones:** each upload and download only moves what changed since last time. When both phones changed the same payment, the later edit wins. Deleting something sends a "deleted" marker, so it disappears on the other phone too.
- **Logging out with changes not yet backed up** (say, offline) asks first: back up and log out, or log out anyway. They stay on the phone either way.
- **Switching backup off** applies to every phone on the account and deletes the cloud copy (after a confirmation); the phone keeps everything. Switching it back on uploads it all again. New accounts start with backup on.

## Privacy

- Your transactions are stored in a Room database on the device, one file per account (`inkless_<id>.db`). Demo data lives in its own file and never mixes with anything else.
- SMS access is asked for in plain language first, and can be revoked any time from Android settings. Only bank transaction alerts are kept; the original text stays on the phone so it can be re-read if a parser improves, and is never uploaded. Notification access works the same way: only UPI apps' payment notifications are read, and their text never leaves the phone either.
- The only network traffic is Firebase: Authentication for sign-in, and Cloud Firestore for your username, profile and (unless you switch it off) the cloud backup. No analytics or ads SDKs. Firestore's own offline cache is kept in memory only, so there's no second copy of your data on disk.
- A shared order screenshot is read on the phone with Tesseract (whose English model, `tessdata_fast`, is Apache-2.0 and ships in the app), with no network at all; neither the image nor its text is kept. Only the breakdown you save is stored and backed up.
- Firestore security rules let each account read and write only its own data, and only the fields listed above: a document carrying anything else, such as message text or a full account number, is refused. A username can be looked up one at a time (that's how logging in with a username finds the email), but usernames can't be listed.
- The backup is encrypted in transit and at rest by Google, and other users can't read it, but whoever runs the Firebase project can see it in the Firebase console. If that's not OK for you, switch backup off.
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
| Sync | Two-way, offline-first sync between Room and Firestore in WorkManager; last write wins, deletions as tombstones |
| Capture | `BroadcastReceiver` (SMS_RECEIVED), `NotificationListenerService`, SMS content provider, WorkManager |
| Sharing | Android share target (`ACTION_SEND`), Tesseract OCR (Tesseract4Android) on the device |
| Budget | Pure-Kotlin calculators, charts drawn with Compose `Canvas`, Jetpack Glance home-screen widget, weekly WorkManager job |
| Notifications | Notification actions handled by a `BroadcastReceiver`, deep link into the payment |
| Async | Kotlin Coroutines & Flow |
| Tests | JUnit, Truth, Turbine; Room tests on a device/emulator; Firestore rules tests on the Firebase emulator |
| Build | Gradle 9 (Kotlin DSL + version catalog), AGP 9, KSP |

minSdk 26 · targetSdk 37

## Project layout

```
app/        Android app — UI, Room database, accounts, SMS capture, Hilt wiring, demo data
parsing/    Pure Kotlin module (no Android dependencies): SMS filter, bank and UPI-app parsers, shared-order parser, test fixtures
firebase/   Firestore security rules, their tests, and Firebase setup steps
docs/       Screenshots
```

Inside `app/src/main/java/com/mehul/inkless/`:

```
auth/         Firebase sign-in, usernames, Google ID tokens, input validation
session/      Who's using the app (signed out, demo, an account) and which database is open
capture/      SMS receiver, notification listener, inbox import, the filter → parse → dedupe → save pipeline, transfer/refund classification
categorize/   Rules → merchant memory → keywords, re-sorting, and the "what was this?" notification
budget/       Allowance cycle, safe to spend, runway, food cap, semesters, the weekly summary
widget/       The home-screen widget
breakdown/    Breaking a payment into items: the rules, and which notification a new payment gets
share/        Share to Inkless: reading a shared order (OCR for screenshots) and matching it to a payment
sync/         Cloud backup: Firestore mappers, the sync worker and its schedule, backup on/off
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

# Room, migration, capture, dedupe, categorising, spending and sync tests (needs a running emulator or connected phone)
./gradlew connectedDebugAndroidTest

# Firestore rules tests (needs Node and Java 21+)
cd firebase && npm install && npm test
```

On Windows use `gradlew.bat`. You can also just open the folder in Android Studio and hit Run.

**Installing from an APK file:** Android treats apps that read SMS carefully when they don't come from an app store. Play Protect may refuse an APK opened from a browser or chat app (installing over USB with `adb install` works), and on Android 13+ the SMS permission starts out locked: open the app's info page, tap ⋮ → *Allow restricted settings*, then allow SMS. The in-app setup screen walks through this too.
