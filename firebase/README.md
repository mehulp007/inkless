# Firebase setup

Inkless uses Firebase Authentication (email/password and Google) and Cloud Firestore. The app
won't build without an `app/google-services.json`, and that file is not in the repo, so to build
it yourself you need your own (free) Firebase project. Everything below can be done with the
[Firebase CLI](https://firebase.google.com/docs/cli).

```bash
npm install -g firebase-tools
firebase login
```

## 1. Project and Android app

```bash
firebase projects:create <your-project-id> --display-name Inkless
firebase use <your-project-id>

firebase apps:create ANDROID Inkless --package-name com.mehul.inkless
firebase apps:list ANDROID          # note the app id, 1:…:android:…

# Also register a web app, even though there's no website. It gives the project a
# Web API key, which Firebase puts into verification and password-reset links. Without
# it those links open a page that just says "The selected page mode is invalid".
firebase apps:create WEB "Inkless email links"
```

## 2. Signing fingerprints (Google sign-in needs these)

Google sign-in fails with a vague error unless the SHA-1 **and** SHA-256 of every keystore you
sign with are registered. Get the debug ones with:

```bash
./gradlew :app:signingReport
```

then add both:

```bash
firebase apps:android:sha:create <app-id> <sha1>
firebase apps:android:sha:create <app-id> <sha256>
```

Do the same again for the release keystore when you make one.

## 3. Sign-in providers

Turn on Email/Password and Google. In the console that's *Authentication → Sign-in method*.
With the CLI, add this to a copy of `firebase.json` (it isn't committed because it carries a
support email) and deploy it:

```json
"auth": {
  "providers": {
    "emailPassword": true,
    "googleSignIn": { "oAuthBrandDisplayName": "Inkless", "supportEmail": "<you@example.com>" }
  }
}
```

```bash
firebase deploy --only auth --config <that-copy>.json
```

## 4. Firestore and rules

```bash
firebase firestore:databases:create "(default)" --location asia-south1
firebase deploy --only firestore
```

## 5. Config file

Download the config **after** enabling Google sign-in (it adds the web client id the app needs):

```bash
firebase apps:sdkconfig ANDROID <app-id> -o app/google-services.json
```

## Data layout

```
usernames/{username}   { uid, email }           anyone can get one, nobody can list
users/{uid}            { username, email, createdAt }   owner only
users/{uid}/...        owner only
```

Logging in with a username works by reading `usernames/{username}` before signing in, so that
document has to be readable without an account. The rules allow reading a single name you
already know, but not listing them.

## Rules tests

The rules in `firestore.rules` are tested against the local emulator (needs Java 21+):

```bash
cd firebase
npm install
npm test
```
